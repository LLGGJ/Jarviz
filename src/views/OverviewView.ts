import * as vscode from 'vscode';
import type { AuthenticationManager } from '../auth/AuthenticationManager';
import type { ResourceStore, Slice } from '../providers/ResourceStore';
import { buildWebviewHtml } from './webviewHtml';
import { OVERVIEW_ACTIONS, parseOverviewMessage } from './messages';

export interface CountedState {
  state: Slice<unknown>['state'];
  count?: number;
  error?: string;
}

export interface OverviewState {
  signedIn: boolean;
  account?: { label: string; email?: string; plan?: string; method: string; expiresAt?: number };
  connection: { status: 'unknown' | 'ok' | 'error' | 'loading'; lastRefresh?: number };
  counts: Record<'apps' | 'games' | 'databases' | 'storage' | 'snapshots', CountedState>;
  appStatus: { online: number; offline: number; other: number };
  warnings: string[];
}

const COMMANDS: Record<(typeof OVERVIEW_ACTIONS)[number], string> = {
  refresh: 'venixCloud.refresh',
  openWeb: 'venixCloud.openWebPanel',
  checkConnection: 'venixCloud.checkConnection',
  signOut: 'venixCloud.signOut',
};

export function buildOverviewState(store: ResourceStore, auth: AuthenticationManager): OverviewState {
  const snap = store.snapshot;
  const session = auth.getSessionInfo();
  const slice = (s: Slice<unknown>): CountedState => ({
    state: s.state,
    count: s.state === 'loaded' ? s.items.length : undefined,
    error: s.error,
  });
  const loading = [snap.apps, snap.games, snap.databases, snap.storage, snap.snapshots].some((s) => s.state === 'loading');
  const warnings: string[] = [];
  for (const [name, s] of [
    ['Aplicações', snap.apps],
    ['Servidores de jogos', snap.games],
    ['Bancos de dados', snap.databases],
    ['Armazenamento', snap.storage],
    ['Snapshots', snap.snapshots],
  ] as const) {
    if (s.state === 'error' && s.error) warnings.push(`${name}: ${s.error}`);
  }
  if (session?.expiresAt && session.expiresAt - Date.now() < 24 * 3600 * 1000) {
    warnings.push('A sessão expira em menos de 24 horas.');
  }
  const statuses = snap.apps.items.map((a) => a.status);
  return {
    signedIn: session !== undefined,
    account: session
      ? { label: session.account.label, email: session.account.email, plan: session.account.plan, method: session.method === 'oauth' ? 'OAuth 2.0 (PKCE)' : 'Token de API', expiresAt: session.expiresAt }
      : undefined,
    connection: { status: loading ? 'loading' : snap.connection, lastRefresh: snap.lastRefresh },
    counts: {
      apps: slice(snap.apps),
      games: slice(snap.games),
      databases: slice(snap.databases),
      storage: slice(snap.storage),
      snapshots: slice(snap.snapshots),
    },
    appStatus: {
      online: statuses.filter((s) => s === 'online').length,
      offline: statuses.filter((s) => s === 'offline' || s === 'error').length,
      other: statuses.filter((s) => s !== 'online' && s !== 'offline' && s !== 'error').length,
    },
    warnings,
  };
}

/** Visão geral na barra lateral. O webview só recebe dados de exibição (nunca tokens). */
export class OverviewViewProvider implements vscode.WebviewViewProvider, vscode.Disposable {
  static readonly viewType = 'venixCloud.overview';
  private view?: vscode.WebviewView;
  private readonly subs: vscode.Disposable[] = [];
  private unsubscribe: Array<{ dispose(): void }> = [];

  constructor(
    private readonly extensionUri: vscode.Uri,
    private readonly store: ResourceStore,
    private readonly auth: AuthenticationManager,
  ) {
    this.unsubscribe.push(store.onDidChange(() => this.push()), auth.onDidChange(() => this.push()));
  }

  resolveWebviewView(view: vscode.WebviewView): void {
    this.view = view;
    view.webview.options = { enableScripts: true, localResourceRoots: [vscode.Uri.joinPath(this.extensionUri, 'media')] };
    view.webview.html = buildWebviewHtml(view.webview, this.extensionUri, {
      title: 'Venix Cloud',
      script: 'overview.js',
      bodyHtml: '<main id="root" aria-live="polite"><p class="muted">Carregando…</p></main>',
    });
    this.subs.push(
      view.webview.onDidReceiveMessage((raw: unknown) => {
        const msg = parseOverviewMessage(raw);
        if (!msg) return;
        if (msg.type === 'ready') this.push();
        else void vscode.commands.executeCommand(COMMANDS[msg.action]);
      }),
      view.onDidDispose(() => {
        this.view = undefined;
      }),
    );
  }

  private push(): void {
    void this.view?.webview.postMessage({ type: 'state', state: buildOverviewState(this.store, this.auth) });
  }

  dispose(): void {
    for (const d of [...this.subs, ...this.unsubscribe]) d.dispose();
  }
}
