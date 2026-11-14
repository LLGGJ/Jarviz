import * as vscode from 'vscode';
import { AUTH_PROVIDER_ID } from '../constants';
import type { AuthenticationManager } from './AuthenticationManager';

const SESSION_ID = 'venixcloud-session';

/**
 * Adaptador fino que expõe a sessão ao menu "Contas" do VS Code. A lógica de OAuth,
 * PKCE e armazenamento seguro continua no AuthenticationManager.
 * Observação: extensões de terceiros só obtêm o token após consentimento explícito do usuário.
 */
export class VenixAuthenticationProvider implements vscode.AuthenticationProvider, vscode.Disposable {
  private readonly emitter = new vscode.EventEmitter<vscode.AuthenticationProviderAuthenticationSessionsChangeEvent>();
  readonly onDidChangeSessions = this.emitter.event;
  private readonly sub: { dispose(): void };
  private hadSession: boolean;

  constructor(
    private readonly auth: AuthenticationManager,
    private readonly signIn: () => Promise<void>,
  ) {
    this.hadSession = auth.isSignedIn();
    this.sub = auth.onDidChange(() => {
      const has = auth.isSignedIn();
      const session = this.currentSession();
      if (has && !this.hadSession && session) this.emitter.fire({ added: [session], removed: [], changed: [] });
      else if (!has && this.hadSession) this.emitter.fire({ added: [], removed: [{ id: SESSION_ID, accessToken: '', account: { id: 'venix', label: 'Venix Cloud' }, scopes: [] }], changed: [] });
      this.hadSession = has;
    });
  }

  static register(auth: AuthenticationManager, signIn: () => Promise<void>): { provider: VenixAuthenticationProvider; registration: vscode.Disposable } {
    const provider = new VenixAuthenticationProvider(auth, signIn);
    const registration = vscode.authentication.registerAuthenticationProvider(AUTH_PROVIDER_ID, 'Venix Cloud', provider);
    return { provider, registration };
  }

  private currentSession(): vscode.AuthenticationSession | undefined {
    const info = this.auth.getSessionInfo();
    const token = this.auth.knownSecrets()[0];
    if (!info || !token) return undefined;
    return { id: SESSION_ID, accessToken: token, account: { id: info.account.id ?? 'venix', label: info.account.label }, scopes: [] };
  }

  async getSessions(): Promise<vscode.AuthenticationSession[]> {
    const s = this.currentSession();
    return s ? [s] : [];
  }

  async createSession(): Promise<vscode.AuthenticationSession> {
    await this.signIn();
    const s = this.currentSession();
    if (!s) throw new Error('Login não concluído.');
    return s;
  }

  async removeSession(): Promise<void> {
    await this.auth.signOut();
  }

  dispose(): void {
    this.sub.dispose();
    this.emitter.dispose();
  }
}
