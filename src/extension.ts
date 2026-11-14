import * as vscode from 'vscode';
import { VenixApi } from './api';
import { AccountApi, parseAccount } from './api/modules/account';
import { VenixApiClient } from './api/VenixApiClient';
import { AuthenticationManager } from './auth/AuthenticationManager';
import { VenixOAuthProvider } from './auth/OAuthProvider';
import { UriCallbackRouter } from './auth/vscodeReceivers';
import { VenixAuthenticationProvider } from './auth/VenixAuthenticationProvider';
import { signInOAuth } from './commands/auth';
import { registerCommands } from './commands';
import { LINK_FILE, OAUTH_AUTHORIZE_URL, OAUTH_CLIENT_ID, OAUTH_TIMEOUT_MS, OAUTH_TOKEN_URL } from './constants';
import { ResourceStore } from './providers/ResourceStore';
import { VenixTreeProvider } from './providers/VenixTreeProvider';
import type { Services } from './services';
import { readSettings } from './utils/config';
import { Logger } from './utils/logger';
import { OverviewViewProvider } from './views/OverviewView';
import { VenixStatusBar } from './views/statusBar';
import { readLink } from './workspace/link';

export async function activate(context: vscode.ExtensionContext): Promise<{ services: Services }> {
  const channel = vscode.window.createOutputChannel('Venix Cloud');
  context.subscriptions.push(channel);

  // eslint-disable-next-line prefer-const
  let auth: AuthenticationManager;
  const logger = new Logger(channel, () => auth?.knownSecrets() ?? []);

  const oauth = new VenixOAuthProvider({
    clientId: OAUTH_CLIENT_ID,
    authorizeUrl: OAUTH_AUTHORIZE_URL,
    tokenUrl: OAUTH_TOKEN_URL,
    timeoutMs: OAUTH_TIMEOUT_MS,
  });

  // Validação do token recém-recebido: cliente SEM o gancho de 401 para não derrubar uma sessão válida existente.
  const resolveAccount = async (token: string) => {
    const client = new VenixApiClient({ baseUrl: readSettings().apiBaseUrl, getToken: () => token, logger });
    return parseAccount(await new AccountApi(client).getMe());
  };
  auth = new AuthenticationManager(context.secrets, context.globalState, oauth, resolveAccount);
  await auth.initialize();

  let cached: { baseUrl: string; api: VenixApi } | undefined;
  const api = (): VenixApi => {
    const { apiBaseUrl } = readSettings();
    if (!cached || cached.baseUrl !== apiBaseUrl) {
      const client = new VenixApiClient({
        baseUrl: apiBaseUrl,
        getToken: () => auth.getAccessToken(),
        onUnauthorized: () => void auth.handleUnauthorized(),
        logger,
      });
      cached = { baseUrl: apiBaseUrl, api: new VenixApi(client) };
    }
    return cached.api;
  };

  const store = new ResourceStore(
    () => (auth.isSignedIn() ? api() : undefined),
    (account) => void auth.updateAccount(account),
  );

  let linked: { id: string; name?: string } | undefined;
  const refreshLink = async (): Promise<void> => {
    const folder = vscode.workspace.workspaceFolders?.[0];
    linked = folder ? await readLink(folder.uri.fsPath) : undefined;
    tree.refresh();
    statusBar.update();
  };

  const uriRouter = new UriCallbackRouter();
  const services: Services = {
    context,
    logger,
    auth,
    store,
    uriRouter,
    settings: readSettings,
    api,
    setLinkedApp: (id, name) => {
      linked = id ? { id, name } : undefined;
      tree.refresh();
      statusBar.update();
    },
    linkedApp: () => linked,
  };

  const tree = new VenixTreeProvider(store, auth, () => linked?.id);
  const statusBar = new VenixStatusBar(auth, store, () => linked?.name ?? linked?.id);
  const overview = new OverviewViewProvider(context.extensionUri, store, auth);
  const { provider: authProvider, registration } = VenixAuthenticationProvider.register(auth, () => signInOAuth(services));

  const setSignedInContext = (): PromiseLike<unknown> => vscode.commands.executeCommand('setContext', 'venixCloud.signedIn', auth.isSignedIn());
  void setSignedInContext();

  let expiredNoticeShown = false;
  const authSub = auth.onDidChange((e) => {
    void setSignedInContext();
    if (e.reason === 'signed-in') {
      expiredNoticeShown = false;
      void store.refreshAll();
    } else if (e.reason === 'signed-out' || e.reason === 'expired') {
      store.clear();
      if (e.reason === 'expired' && !expiredNoticeShown) {
        expiredNoticeShown = true;
        void vscode.window.showWarningMessage('Venix Cloud: sua sessão expirou ou foi revogada. Entre novamente.', 'Entrar').then((c) => {
          if (c === 'Entrar') void vscode.commands.executeCommand('venixCloud.signIn');
        });
      }
    }
  });

  // Atualização automática (opt-in): só com a janela em foco, para economizar o limite de 60 req/min.
  let timer: ReturnType<typeof setInterval> | undefined;
  const configureTimer = (): void => {
    if (timer) clearInterval(timer);
    timer = undefined;
    const settings = readSettings();
    if (!settings.autoRefresh) return;
    timer = setInterval(() => {
      if (auth.isSignedIn() && vscode.window.state.focused) void store.refreshAll();
    }, settings.refreshIntervalSec * 1000);
  };
  configureTimer();

  const warnBaseUrl = (): void => {
    const { apiBaseUrlError } = readSettings();
    if (apiBaseUrlError) {
      void vscode.window.showWarningMessage(`Venix Cloud: "venixCloud.apiBaseUrl" rejeitada (${apiBaseUrlError}) — usando a URL padrão. Credenciais nunca são enviadas a domínios não confiáveis.`);
    }
  };
  warnBaseUrl();

  const treeView = vscode.window.createTreeView('venixCloud.explorer', { treeDataProvider: tree, showCollapseAll: true });
  const watcher = vscode.workspace.createFileSystemWatcher(`**/${LINK_FILE}`);

  context.subscriptions.push(
    treeView,
    tree,
    statusBar,
    overview,
    authProvider,
    registration,
    authSub,
    watcher,
    watcher.onDidChange(() => void refreshLink()),
    watcher.onDidCreate(() => void refreshLink()),
    watcher.onDidDelete(() => void refreshLink()),
    vscode.window.registerWebviewViewProvider(OverviewViewProvider.viewType, overview, { webviewOptions: { retainContextWhenHidden: true } }),
    vscode.window.registerUriHandler(uriRouter),
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration('venixCloud.apiBaseUrl')) {
        cached = undefined;
        warnBaseUrl();
      }
      if (e.affectsConfiguration('venixCloud.autoRefresh') || e.affectsConfiguration('venixCloud.refreshInterval')) configureTimer();
    }),
    vscode.workspace.onDidChangeWorkspaceFolders(() => void refreshLink()),
    { dispose: () => timer && clearInterval(timer) },
    ...registerCommands(services),
  );

  await refreshLink();
  if (auth.isSignedIn()) void store.refreshAll();
  logger.info('Extensão Venix Cloud ativada.');
  return { services };
}

export function deactivate(): void {
  // Os recursos são liberados via context.subscriptions.
}
