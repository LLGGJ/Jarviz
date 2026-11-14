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
exports.signInOAuth = signInOAuth;
exports.signInApiToken = signInApiToken;
exports.signOut = signOut;
exports.showAccount = showAccount;
exports.checkConnection = checkConnection;
exports.openWebPanel = openWebPanel;
exports.openSettings = openSettings;
const vscode = __importStar(require("vscode"));
const errors_1 = require("../api/errors");
const OAuthProvider_1 = require("../auth/OAuthProvider");
const CallbackServer_1 = require("../auth/CallbackServer");
const vscodeReceivers_1 = require("../auth/vscodeReceivers");
const AuthenticationManager_1 = require("../auth/AuthenticationManager");
const constants_1 = require("../constants");
const format_1 = require("../utils/format");
const shared_1 = require("./shared");
function redirectUriFor(s, mode) {
    const settings = s.settings();
    if (mode === 'auto' && settings.redirectMode === 'vscode') {
        return { uri: `${vscode.env.uriScheme}://${s.context.extension.id}${constants_1.OAUTH_CALLBACK_PATH}`, kind: 'vscode' };
    }
    return { uri: `http://127.0.0.1:${settings.callbackPort}${constants_1.OAUTH_CALLBACK_PATH}`, kind: 'loopback' };
}
async function openBrowser(url) {
    const opened = await vscode.env.openExternal(vscode.Uri.parse(url, true));
    if (!opened) {
        const choice = await vscode.window.showWarningMessage('Venix Cloud: não consegui abrir o navegador.', 'Copiar link');
        if (choice === 'Copiar link')
            await vscode.env.clipboard.writeText(url);
    }
}
async function signInOAuth(s, mode = 'auto') {
    if (s.auth.isSignedIn()) {
        const choice = await vscode.window.showInformationMessage('Você já está conectado à Venix Cloud.', 'Trocar de conta');
        if (choice !== 'Trocar de conta')
            return;
    }
    const { uri, kind } = redirectUriFor(s, mode);
    let receiver;
    if (mode === 'manual')
        receiver = new vscodeReceivers_1.ManualReceiver(uri);
    else if (kind === 'vscode')
        receiver = s.uriRouter.createReceiver(uri);
    else
        receiver = new CallbackServer_1.LoopbackReceiver(s.settings().callbackPort);
    try {
        const info = await vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: mode === 'manual' ? 'Venix Cloud: cole a URL final no campo exibido…' : 'Venix Cloud: aguardando autorização no navegador…',
            cancellable: true,
        }, (_p, token) => {
            const controller = new AbortController();
            token.onCancellationRequested(() => controller.abort());
            return s.auth.signInWithOAuth({ receiver, openUrl: openBrowser, signal: controller.signal });
        });
        void vscode.window.showInformationMessage(`Venix Cloud: conectado como ${info.account.label}.`);
        void s.store.refreshAll();
    }
    catch (err) {
        if (err instanceof OAuthProvider_1.OAuthError && err.kind === 'cancelled')
            return;
        if (err instanceof OAuthProvider_1.OAuthError && err.kind === 'listen-failed' && mode === 'auto' && kind === 'loopback') {
            s.logger.warn(`Loopback indisponível: ${err.message}`);
            const choice = await vscode.window.showWarningMessage(`Venix Cloud: ${err.message} Você pode concluir o login colando a URL final manualmente.`, 'Entrar manualmente');
            if (choice === 'Entrar manualmente')
                await signInOAuth(s, 'manual');
            return;
        }
        (0, shared_1.reportError)(s, 'Login OAuth', err);
    }
}
/** Alternativa documentada: token de API gerado no painel. NÃO é OAuth. */
async function signInApiToken(s) {
    const token = await vscode.window.showInputBox({
        title: 'Venix Cloud — Token de API',
        prompt: 'Cole o token gerado no painel da Venix Cloud. Ele será guardado no armazenamento seguro do VS Code.',
        password: true,
        ignoreFocusOut: true,
        validateInput: (v) => (v.trim() === '' ? undefined : (0, AuthenticationManager_1.validateApiToken)(v.trim())),
    });
    if (!token?.trim())
        return;
    try {
        const info = await s.auth.signInWithApiToken(token);
        void vscode.window.showInformationMessage(`Venix Cloud: conectado com token de API (${info.account.label}).`);
        void s.store.refreshAll();
    }
    catch (err) {
        void vscode.window.showErrorMessage(`Venix Cloud: ${(0, errors_1.describeError)(err)}`);
    }
}
async function signOut(s) {
    if (!s.auth.isSignedIn()) {
        void vscode.window.showInformationMessage('Venix Cloud: você não está conectado.');
        return;
    }
    await s.auth.signOut();
    s.store.clear();
    void vscode.window.showInformationMessage('Venix Cloud: sessão encerrada e dados locais removidos.');
}
async function showAccount(s) {
    const info = s.auth.getSessionInfo();
    if (!info) {
        const choice = await vscode.window.showInformationMessage('Venix Cloud: você não está conectado.', 'Entrar');
        if (choice === 'Entrar')
            await signInOAuth(s);
        return;
    }
    const lines = [
        `Conta: ${info.account.label}`,
        info.account.email ? `E-mail: ${info.account.email}` : undefined,
        info.account.plan ? `Plano: ${info.account.plan}` : undefined,
        `Autenticação: ${info.method === 'oauth' ? 'OAuth 2.0 com PKCE' : 'Token de API (não é OAuth)'}`,
        info.expiresAt ? `Sessão expira em: ${(0, format_1.formatTime)(info.expiresAt)}` : 'Expiração: não informada pelo servidor',
    ].filter(Boolean);
    const choice = await vscode.window.showInformationMessage(lines.join('\n'), { modal: true }, 'Sair');
    if (choice === 'Sair')
        await signOut(s);
}
async function checkConnection(s) {
    if (!(await (0, shared_1.ensureSignedIn)(s)))
        return;
    const started = Date.now();
    try {
        await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: 'Venix Cloud: verificando conexão…' }, () => s.api().account.getMe());
        void vscode.window.showInformationMessage(`Venix Cloud: conexão OK (${Date.now() - started} ms) • ${s.settings().apiBaseUrl}`);
    }
    catch (err) {
        (0, shared_1.reportError)(s, 'Verificação de conexão', err);
    }
}
async function openWebPanel() {
    await vscode.env.openExternal(vscode.Uri.parse(constants_1.WEB_PANEL_URL));
}
async function openSettings() {
    await vscode.commands.executeCommand('workbench.action.openSettings', '@ext:venixcloud.venix-cloud');
}
//# sourceMappingURL=auth.js.map