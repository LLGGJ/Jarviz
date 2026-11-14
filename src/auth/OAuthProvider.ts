import { sanitizeMessage } from '../security/redact';
import { assertTrustedUrl } from '../security/trustedHosts';
import { codeChallengeS256, generateCodeVerifier, generateState, safeEqual } from './pkce';

export type OAuthErrorKind = 'denied' | 'cancelled' | 'timeout' | 'exchange-failed' | 'invalid-response' | 'listen-failed';

export class OAuthError extends Error {
  constructor(
    readonly kind: OAuthErrorKind,
    message: string,
  ) {
    super(message);
    this.name = 'OAuthError';
  }
}

export type CallbackParams = Record<string, string>;
export type CallbackResult = { ok: true } | { ok: false; title: string; message: string };

/**
 * Meio de recebimento do redirecionamento (loopback HTTP, URI handler do VS Code
 * ou colagem manual). A validação de `state` é feita aqui no provedor, nunca no meio.
 */
export interface CallbackReceiver {
  readonly redirectUri: string;
  listen(handler: (params: CallbackParams) => CallbackResult, context: { authUrl: string }): Promise<void>;
  dispose(): void;
}

export interface OAuthConfig {
  clientId: string;
  authorizeUrl: string;
  tokenUrl: string;
  timeoutMs: number;
  fetch?: typeof fetch;
  now?: () => number;
}

export interface TokenSet {
  accessToken: string;
  /** Epoch em ms; ausente quando o servidor não informa `expires_in`. */
  expiresAt?: number;
}

export interface SignInOptions {
  receiver: CallbackReceiver;
  openUrl: (url: string) => Promise<unknown>;
  signal?: AbortSignal;
}

export class VenixOAuthProvider {
  private readonly fetchImpl: typeof fetch;
  private readonly now: () => number;

  constructor(private readonly config: OAuthConfig) {
    // Mesma política de domínios confiáveis usada pelo cliente HTTP.
    assertTrustedUrl(config.authorizeUrl.split('?')[0] as string);
    assertTrustedUrl(config.tokenUrl);
    this.fetchImpl = config.fetch ?? globalThis.fetch.bind(globalThis);
    this.now = config.now ?? Date.now;
  }

  buildAuthorizeUrl(redirectUri: string, challenge: string, state: string): string {
    const url = new URL(this.config.authorizeUrl);
    url.searchParams.set('client_id', this.config.clientId);
    url.searchParams.set('redirect_uri', redirectUri);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('code_challenge', challenge);
    url.searchParams.set('code_challenge_method', 'S256');
    url.searchParams.set('state', state);
    return url.toString();
  }

  /** Fluxo Authorization Code + PKCE (S256) para aplicativo instalado (cliente público). */
  async signIn(options: SignInOptions): Promise<TokenSet> {
    const { receiver, signal } = options;
    const verifier = generateCodeVerifier();
    const state = generateState();
    const authUrl = this.buildAuthorizeUrl(receiver.redirectUri, codeChallengeS256(verifier), state);

    let settled = false;
    let resolveCode!: (code: string) => void;
    let rejectCode!: (err: OAuthError) => void;
    const codePromise = new Promise<string>((resolve, reject) => {
      resolveCode = resolve;
      rejectCode = reject;
    });
    codePromise.catch(() => undefined); // evita "unhandled rejection" se a limpeza vier antes

    const handler = (params: CallbackParams): CallbackResult => {
      if (settled) {
        return { ok: false, title: 'Link já utilizado', message: 'Este link de autorização já foi usado.' };
      }
      const returnedState = params['state'];
      const stateMatches = returnedState !== undefined && safeEqual(returnedState, state);
      if (returnedState !== undefined && !stateMatches) {
        // Possível CSRF/requisição forjada: rejeita SEM encerrar o login legítimo em andamento.
        return { ok: false, title: 'Estado inválido', message: 'A resposta não corresponde a este login. Tente novamente.' };
      }
      if (params['error']) {
        settled = true;
        const detail = sanitizeMessage(params['error_description'] || params['error'] || 'acesso negado', 200);
        rejectCode(new OAuthError('denied', `Autorização recusada: ${detail}`));
        return { ok: false, title: 'Login recusado', message: detail };
      }
      if (!stateMatches) {
        return { ok: false, title: 'Estado ausente', message: 'A resposta não trouxe o parâmetro de segurança "state".' };
      }
      const code = params['code'];
      if (!code) {
        settled = true;
        rejectCode(new OAuthError('invalid-response', 'O servidor não devolveu o código de autorização.'));
        return { ok: false, title: 'Código ausente', message: 'O servidor não devolveu o código de autorização.' };
      }
      settled = true;
      resolveCode(code);
      return { ok: true };
    };

    let timer: ReturnType<typeof setTimeout> | undefined;
    const onAbort = (): void => rejectCode(new OAuthError('cancelled', 'Login cancelado.'));
    try {
      if (signal?.aborted) throw new OAuthError('cancelled', 'Login cancelado.');
      signal?.addEventListener('abort', onAbort, { once: true });
      timer = setTimeout(
        () => rejectCode(new OAuthError('timeout', 'Tempo esgotado aguardando a autorização no navegador.')),
        this.config.timeoutMs,
      );
      try {
        await receiver.listen(handler, { authUrl });
      } catch (err) {
        if (err instanceof OAuthError) throw err;
        throw new OAuthError('listen-failed', 'Não foi possível abrir o recebimento do callback.');
      }
      await options.openUrl(authUrl);
      const code = await codePromise;
      return await this.exchange(code, receiver.redirectUri, verifier, signal);
    } finally {
      if (timer) clearTimeout(timer);
      signal?.removeEventListener('abort', onAbort);
      receiver.dispose();
    }
  }

  /** POST JSON no endpoint de token (formato usado pela CLI oficial). */
  private async exchange(code: string, redirectUri: string, verifier: string, signal?: AbortSignal): Promise<TokenSet> {
    let res: Response;
    try {
      res = await this.fetchImpl(this.config.tokenUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({
          grant_type: 'authorization_code',
          client_id: this.config.clientId,
          code,
          redirect_uri: redirectUri,
          code_verifier: verifier,
        }),
        redirect: 'manual',
        signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(30_000)]) : AbortSignal.timeout(30_000),
      });
    } catch {
      if (signal?.aborted) throw new OAuthError('cancelled', 'Login cancelado.');
      throw new OAuthError('exchange-failed', 'Falha de rede ao trocar o código de autorização por tokens.');
    }

    const text = (await res.text().catch(() => '')).slice(0, 64 * 1024);
    let body: unknown;
    try {
      body = JSON.parse(text);
    } catch {
      body = undefined;
    }
    if (!res.ok) {
      let detail = '';
      if (body && typeof body === 'object') {
        const rec = body as Record<string, unknown>;
        for (const key of ['error_description', 'message', 'error', 'detail']) {
          if (typeof rec[key] === 'string' && rec[key]) {
            detail = sanitizeMessage(rec[key] as string, 200);
            break;
          }
        }
      }
      throw new OAuthError('exchange-failed', `A Venix Cloud recusou a troca do código (HTTP ${res.status})${detail ? `: ${detail}` : '.'}`);
    }
    if (!body || typeof body !== 'object') {
      throw new OAuthError('invalid-response', 'Resposta inválida do endpoint de token.');
    }
    const rec = body as Record<string, unknown>;
    // Alguns servidores aninham o resultado em `response`.
    const data = (rec['response'] && typeof rec['response'] === 'object' ? rec['response'] : rec) as Record<string, unknown>;
    const accessToken = data['access_token'];
    if (typeof accessToken !== 'string' || accessToken.trim() === '') {
      throw new OAuthError('invalid-response', 'A resposta não trouxe um access_token.');
    }
    const expiresIn = Number(data['expires_in']);
    return {
      accessToken,
      expiresAt: Number.isFinite(expiresIn) && expiresIn > 0 ? this.now() + expiresIn * 1000 : undefined,
    };
  }
}
