import { SECRET_SESSION_KEY, STATE_ACCOUNT_KEY } from '../constants';
import type { AccountInfo } from '../api/types';
import { VenixApiError } from '../api/errors';
import { Emitter, type Disposable } from '../utils/events';
import type { CallbackReceiver, TokenSet } from './OAuthProvider';
import type { VenixOAuthProvider } from './OAuthProvider';

export type AuthMethod = 'oauth' | 'apiToken';

/** Conteúdo guardado SOMENTE em SecretStorage. */
interface StoredSession {
  method: AuthMethod;
  accessToken: string;
  expiresAt?: number;
  createdAt: number;
}

export interface SecretStore {
  get(key: string): PromiseLike<string | undefined>;
  store(key: string, value: string): PromiseLike<void>;
  delete(key: string): PromiseLike<void>;
}

/** Armazena apenas dados NÃO sensíveis (rótulo da conta). */
export interface StateStore {
  get<T>(key: string): T | undefined;
  update(key: string, value: unknown): PromiseLike<void>;
}

export type SessionChange = { reason: 'signed-in' | 'signed-out' | 'expired' | 'account-updated' };

export interface SessionInfo {
  method: AuthMethod;
  account: AccountInfo;
  expiresAt?: number;
}

export type AccountResolver = (token: string) => Promise<AccountInfo>;

export class AuthenticationManager {
  private session?: StoredSession;
  private account?: AccountInfo;
  private readonly changes = new Emitter<SessionChange>();
  private signingIn = false;
  readonly onDidChange = this.changes.event;

  constructor(
    private readonly secrets: SecretStore,
    private readonly state: StateStore,
    private readonly oauth: VenixOAuthProvider,
    private readonly resolveAccount: AccountResolver,
    private readonly now: () => number = Date.now,
  ) {}

  /** Restaura a sessão do SecretStorage (descartando dados corrompidos ou expirados). */
  async initialize(): Promise<void> {
    const raw = await this.secrets.get(SECRET_SESSION_KEY);
    if (!raw) return;
    try {
      const parsed = JSON.parse(raw) as Partial<StoredSession>;
      if (
        typeof parsed.accessToken !== 'string' ||
        !parsed.accessToken ||
        (parsed.method !== 'oauth' && parsed.method !== 'apiToken')
      ) {
        throw new Error('formato inválido');
      }
      this.session = {
        method: parsed.method,
        accessToken: parsed.accessToken,
        expiresAt: typeof parsed.expiresAt === 'number' ? parsed.expiresAt : undefined,
        createdAt: typeof parsed.createdAt === 'number' ? parsed.createdAt : this.now(),
      };
      this.account = this.state.get<AccountInfo>(STATE_ACCOUNT_KEY);
    } catch {
      await this.clearStorage();
      return;
    }
    if (this.isExpired(this.session)) {
      await this.expire();
    }
  }

  isSignedIn(): boolean {
    return this.session !== undefined && !this.isExpired(this.session);
  }

  getSessionInfo(): SessionInfo | undefined {
    if (!this.session || this.isExpired(this.session)) return undefined;
    return {
      method: this.session.method,
      account: this.account ?? { label: 'Conta Venix Cloud' },
      expiresAt: this.session.expiresAt,
    };
  }

  /** Token em memória para o cliente HTTP; expira a sessão se já venceu. */
  async getAccessToken(): Promise<string | undefined> {
    if (!this.session) return undefined;
    if (this.isExpired(this.session)) {
      await this.expire();
      return undefined;
    }
    return this.session.accessToken;
  }

  /** Segredos conhecidos, para o logger removê-los de qualquer mensagem. */
  knownSecrets(): string[] {
    return this.session ? [this.session.accessToken] : [];
  }

  async signInWithOAuth(options: {
    receiver: CallbackReceiver;
    openUrl: (url: string) => Promise<unknown>;
    signal?: AbortSignal;
  }): Promise<SessionInfo> {
    return this.guard(async () => {
      const tokens = await this.oauth.signIn(options);
      return this.commit('oauth', tokens);
    });
  }

  /** Alternativa documentada (Bearer com token gerado no painel). NÃO é OAuth. */
  async signInWithApiToken(token: string): Promise<SessionInfo> {
    const clean = token.trim();
    const problem = validateApiToken(clean);
    if (problem) throw new Error(problem);
    return this.guard(() => this.commit('apiToken', { accessToken: clean }));
  }

  /** Encerra a sessão e apaga TODOS os dados locais relacionados. */
  async signOut(): Promise<void> {
    const had = this.session !== undefined;
    await this.clearStorage();
    if (had) this.changes.fire({ reason: 'signed-out' });
  }

  /** A API respondeu 401: o token foi revogado/expirou. */
  async handleUnauthorized(): Promise<void> {
    if (!this.session) return;
    await this.expire();
  }

  dispose(): void {
    this.changes.dispose();
  }

  // ---------------------------------------------------------------- internos

  private async guard<T>(fn: () => Promise<T>): Promise<T> {
    if (this.signingIn) throw new Error('Já existe um login em andamento.');
    this.signingIn = true;
    try {
      return await fn();
    } finally {
      this.signingIn = false;
    }
  }

  private async commit(method: AuthMethod, tokens: TokenSet): Promise<SessionInfo> {
    // Valida o resultado antes de persistir: um token que a API recusa nunca é salvo.
    let account: AccountInfo = { label: 'Conta Venix Cloud' };
    try {
      account = await this.resolveAccount(tokens.accessToken);
    } catch (err) {
      if (err instanceof VenixApiError && (err.kind === 'unauthorized' || err.kind === 'forbidden')) {
        throw new Error(
          method === 'apiToken'
            ? 'Token de API recusado pela Venix Cloud. Verifique se ele está correto e ativo.'
            : 'A Venix Cloud recusou o token recebido. Tente entrar novamente.',
        );
      }
      // Falhas transitórias (rede/5xx) não invalidam o login; o rótulo é atualizado depois.
    }
    const session: StoredSession = {
      method,
      accessToken: tokens.accessToken,
      expiresAt: tokens.expiresAt,
      createdAt: this.now(),
    };
    await this.secrets.store(SECRET_SESSION_KEY, JSON.stringify(session));
    await this.state.update(STATE_ACCOUNT_KEY, account);
    this.session = session;
    this.account = account;
    this.changes.fire({ reason: 'signed-in' });
    return { method, account, expiresAt: session.expiresAt };
  }

  /** Atualiza o rótulo da conta (ex.: após `GET /me`). */
  async updateAccount(account: AccountInfo): Promise<void> {
    if (!this.session) return;
    this.account = account;
    await this.state.update(STATE_ACCOUNT_KEY, account);
    this.changes.fire({ reason: 'account-updated' });
  }

  private isExpired(session: StoredSession): boolean {
    return session.expiresAt !== undefined && session.expiresAt <= this.now();
  }

  private async expire(): Promise<void> {
    await this.clearStorage();
    this.changes.fire({ reason: 'expired' });
  }

  private async clearStorage(): Promise<void> {
    this.session = undefined;
    this.account = undefined;
    await this.secrets.delete(SECRET_SESSION_KEY);
    await this.state.update(STATE_ACCOUNT_KEY, undefined);
  }
}

export function validateApiToken(token: string): string | undefined {
  if (token.length < 8) return 'O token parece curto demais.';
  if (token.length > 4096) return 'O token é longo demais.';
  // eslint-disable-next-line no-control-regex
  if (/[\s\u0000-\u001f]/.test(token)) return 'O token não pode conter espaços ou quebras de linha.';
  return undefined;
}

export type { Disposable };
