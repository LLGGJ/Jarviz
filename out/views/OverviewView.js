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
exports.OverviewViewProvider = void 0;
exports.buildOverviewState = buildOverviewState;
const vscode = __importStar(require("vscode"));
const webviewHtml_1 = require("./webviewHtml");
const messages_1 = require("./messages");
const COMMANDS = {
    refresh: 'venixCloud.refresh',
    openWeb: 'venixCloud.openWebPanel',
    checkConnection: 'venixCloud.checkConnection',
    signOut: 'venixCloud.signOut',
};
function buildOverviewState(store, auth) {
    const snap = store.snapshot;
    const session = auth.getSessionInfo();
    const slice = (s) => ({
        state: s.state,
        count: s.state === 'loaded' ? s.items.length : undefined,
        error: s.error,
    });
    const loading = [snap.apps, snap.games, snap.databases, snap.storage, snap.snapshots].some((s) => s.state === 'loading');
    const warnings = [];
    for (const [name, s] of [
        ['Aplicações', snap.apps],
        ['Servidores de jogos', snap.games],
        ['Bancos de dados', snap.databases],
        ['Armazenamento', snap.storage],
        ['Snapshots', snap.snapshots],
    ]) {
        if (s.state === 'error' && s.error)
            warnings.push(`${name}: ${s.error}`);
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
class OverviewViewProvider {
    extensionUri;
    store;
    auth;
    static viewType = 'venixCloud.overview';
    view;
    subs = [];
    unsubscribe = [];
    constructor(extensionUri, store, auth) {
        this.extensionUri = extensionUri;
        this.store = store;
        this.auth = auth;
        this.unsubscribe.push(store.onDidChange(() => this.push()), auth.onDidChange(() => this.push()));
    }
    resolveWebviewView(view) {
        this.view = view;
        view.webview.options = { enableScripts: true, localResourceRoots: [vscode.Uri.joinPath(this.extensionUri, 'media')] };
        view.webview.html = (0, webviewHtml_1.buildWebviewHtml)(view.webview, this.extensionUri, {
            title: 'Venix Cloud',
            script: 'overview.js',
            bodyHtml: '<main id="root" aria-live="polite"><p class="muted">Carregando…</p></main>',
        });
        this.subs.push(view.webview.onDidReceiveMessage((raw) => {
            const msg = (0, messages_1.parseOverviewMessage)(raw);
            if (!msg)
                return;
            if (msg.type === 'ready')
                this.push();
            else
                void vscode.commands.executeCommand(COMMANDS[msg.action]);
        }), view.onDidDispose(() => {
            this.view = undefined;
        }));
    }
    push() {
        void this.view?.webview.postMessage({ type: 'state', state: buildOverviewState(this.store, this.auth) });
    }
    dispose() {
        for (const d of [...this.subs, ...this.unsubscribe])
            d.dispose();
    }
}
exports.OverviewViewProvider = OverviewViewProvider;
//# sourceMappingURL=OverviewView.js.map