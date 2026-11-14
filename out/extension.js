"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.activate = activate;
exports.deactivate = deactivate;
const vscode = __importStar(require("vscode"));
const api_1 = require("./api");
const account_1 = require("./api/modules/account");
const VenixApiClient_1 = require("./api/VenixApiClient");
const AuthenticationManager_1 = require("./auth/AuthenticationManager");
const OAuthProvider_1 = require("./auth/OAuthProvider");
const vscodeReceivers_1 = require("./auth/vscodeReceivers");
const VenixAuthenticationProvider_1 = require("./auth/VenixAuthenticationProvider");
const auth_1 = require("./commands/auth");
const commands_1 = require("./commands");
const constants_1 = require("./constants");
const ResourceStore_1 = require("./providers/ResourceStore");
const VenixTreeProvider_1 = require("./providers/VenixTreeProvider");
const config_1 = require("./utils/config");
const logger_1 = require("./utils/logger");
const OverviewView_1 = require("./views/OverviewView");
const statusBar_1 = require("./views/statusBar");
const link_1 = require("./workspace/link");
async function activate(context) {
    const channel = vscode.window.createOutputChannel('Venix Cloud');
    context.subscriptions.push(channel);
    // eslint-disable-next-line prefer-const
    let auth;
    const logger = new logger_1.Logger(channel, () => auth?.knownSecrets() ?? []);
    const oauth = new OAuthProvider_1.VenixOAuthProvider({
        clientId: constants_1.OAUTH_CLIENT_ID,
        authorizeUrl: constants_1.OAUTH_AUTHORIZE_URL,
        tokenUrl: constants_1.OAUTH_TOKEN_URL,
        timeoutMs: constants_1.OAUTH_TIMEOUT_MS,
    });
    // Validação do token recém-recebido: cliente SEM o gancho de 401 para não derrubar uma sessão válida existente.
    const resolveAccount = async (token) => {
        const client = new VenixApiClient_1.VenixApiClient({ baseUrl: (0, config_1.readSettings)().apiBaseUrl, getToken: () => token, logger });
        return (0, account_1.parseAccount)(await new account_1.AccountApi(client).getMe());
    };
    auth = new AuthenticationManager_1.AuthenticationManager(context.secrets, context.globalState, oauth, resolveAccount);
    await auth.initialize();
    let cached;
    const api = () => {
        const { apiBaseUrl } = (0, config_1.readSettings)();
        if (!cached || cached.baseUrl !== apiBaseUrl) {
            const client = new VenixApiClient_1.VenixApiClient({
                baseUrl: apiBaseUrl,
                getToken: () => auth.getAccessToken(),
                onUnauthorized: () => void auth.handleUnauthorized(),
                logger,
            });
            cached = { baseUrl: apiBaseUrl, api: new api_1.VenixApi(client) };
        }
        return cached.api;
    };
    const store = new ResourceStore_1.ResourceStore(() => (auth.isSignedIn() ? api() : undefined), (account) => void auth.updateAccount(account));
    let linked;
    const refreshLink = async () => {
        const folder = vscode.workspace.workspaceFolders?.[0];
        linked = folder ? await (0, link_1.readLink)(folder.uri.fsPath) : undefined;
        tree.refresh();
        statusBar.update();
    };
    const uriRouter = new vscodeReceivers_1.UriCallbackRouter();
    const services = {
        context,
        logger,
        auth,
        store,
        uriRouter,
        settings: config_1.readSettings,
        api,
        setLinkedApp: (id, name) => {
            linked = id ? { id, name } : undefined;
            tree.refresh();
            statusBar.update();
        },
        linkedApp: () => linked,
    };
    const tree = new VenixTreeProvider_1.VenixTreeProvider(store, auth, () => linked?.id);
    const statusBar = new statusBar_1.VenixStatusBar(auth, store, () => linked?.name ?? linked?.id);
    const overview = new OverviewView_1.OverviewViewProvider(context.extensionUri, store, auth);
    const { provider: authProvider, registration } = VenixAuthenticationProvider_1.VenixAuthenticationProvider.register(auth, () => (0, auth_1.signInOAuth)(services));
    const setSignedInContext = () => vscode.commands.executeCommand('setContext', 'venixCloud.signedIn', auth.isSignedIn());
    void setSignedInContext();
    let expiredNoticeShown = false;
    const authSub = auth.onDidChange((e) => {
        void setSignedInContext();
        if (e.reason === 'signed-in') {
            expiredNoticeShown = false;
            void store.refreshAll();
        }
        else if (e.reason === 'signed-out' || e.reason === 'expired') {
            store.clear();
            if (e.reason === 'expired' && !expiredNoticeShown) {
                expiredNoticeShown = true;
                void vscode.window.showWarningMessage('Venix Cloud: sua sessão expirou ou foi revogada. Entre novamente.', 'Entrar').then((c) => {
                    if (c === 'Entrar')
                        void vscode.commands.executeCommand('venixCloud.signIn');
                });
            }
        }
    });
    // Atualização automática (opt-in): só com a janela em foco, para economizar o limite de 60 req/min.
    let timer;
    const configureTimer = () => {
        if (timer)
            clearInterval(timer);
        timer = undefined;
        const settings = (0, config_1.readSettings)();
        if (!settings.autoRefresh)
            return;
        timer = setInterval(() => {
            if (auth.isSignedIn() && vscode.window.state.focused)
                void store.refreshAll();
        }, settings.refreshIntervalSec * 1000);
    };
    configureTimer();
    const warnBaseUrl = () => {
        const { apiBaseUrlError } = (0, config_1.readSettings)();
        if (apiBaseUrlError) {
            void vscode.window.showWarningMessage(`Venix Cloud: "venixCloud.apiBaseUrl" rejeitada (${apiBaseUrlError}) — usando a URL padrão. Credenciais nunca são enviadas a domínios não confiáveis.`);
        }
    };
    warnBaseUrl();
    const treeView = vscode.window.createTreeView('venixCloud.explorer', { treeDataProvider: tree, showCollapseAll: true });
    const watcher = vscode.workspace.createFileSystemWatcher(`**/${constants_1.LINK_FILE}`);
    context.subscriptions.push(treeView, tree, statusBar, overview, authProvider, registration, authSub, watcher, watcher.onDidChange(() => void refreshLink()), watcher.onDidCreate(() => void refreshLink()), watcher.onDidDelete(() => void refreshLink()), vscode.window.registerWebviewViewProvider(OverviewView_1.OverviewViewProvider.viewType, overview, { webviewOptions: { retainContextWhenHidden: true } }), vscode.window.registerUriHandler(uriRouter), vscode.workspace.onDidChangeConfiguration((e) => {
        if (e.affectsConfiguration('venixCloud.apiBaseUrl')) {
            cached = undefined;
            warnBaseUrl();
        }
        if (e.affectsConfiguration('venixCloud.autoRefresh') || e.affectsConfiguration('venixCloud.refreshInterval'))
            configureTimer();
    }), vscode.workspace.onDidChangeWorkspaceFolders(() => void refreshLink()), { dispose: () => timer && clearInterval(timer) }, ...(0, commands_1.registerCommands)(services));
    await refreshLink();
    if (auth.isSignedIn())
        void store.refreshAll();
    logger.info('Extensão Venix Cloud ativada.');
    return { services };
}
function deactivate() {
    // Os recursos são liberados via context.subscriptions.
}
//# sourceMappingURL=extension.js.map