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
exports.nodeOf = nodeOf;
exports.reportError = reportError;
exports.run = run;
exports.confirmDestructive = confirmDestructive;
exports.ensureSignedIn = ensureSignedIn;
exports.ensureLoaded = ensureLoaded;
exports.pickApp = pickApp;
exports.askNumber = askNumber;
exports.pickFolder = pickFolder;
const vscode = __importStar(require("vscode"));
const errors_1 = require("../api/errors");
function nodeOf(arg, kind) {
    if (typeof arg === 'object' && arg !== null && arg.kind === kind && 'item' in arg) {
        return arg;
    }
    return undefined;
}
/** Mostra o erro ao usuário (sem stack) e registra apenas dados não sensíveis. */
function reportError(s, context, err) {
    s.logger.error(context, err instanceof errors_1.VenixApiError ? `${err.kind}${err.status ? ` HTTP ${err.status}` : ''}` : err);
    if (err instanceof errors_1.VenixApiError && err.kind === 'cancelled')
        return;
    const message = `Venix Cloud: ${(0, errors_1.describeError)(err)}`;
    if (err instanceof errors_1.VenixApiError && (err.kind === 'unauthorized' || err.kind === 'not-authenticated')) {
        void vscode.window.showErrorMessage(message, 'Entrar').then((choice) => {
            if (choice === 'Entrar')
                void vscode.commands.executeCommand('venixCloud.signIn');
        });
        return;
    }
    void vscode.window.showErrorMessage(message);
}
/** Executa uma operação com notificação de progresso, tratamento de erro e atualização da árvore. */
async function run(s, title, fn, options = {}) {
    try {
        const value = await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title, cancellable: options.cancellable ?? false }, (_progress, token) => {
            const controller = new AbortController();
            token.onCancellationRequested(() => controller.abort());
            return fn(controller.signal);
        });
        if (options.success) {
            const text = typeof options.success === 'function' ? options.success(value) : options.success;
            void vscode.window.showInformationMessage(`Venix Cloud: ${text}`);
        }
        if (options.refresh?.length)
            void s.store.refresh(...options.refresh);
        return { ok: true, value };
    }
    catch (err) {
        reportError(s, title, err);
        return { ok: false };
    }
}
/**
 * Confirmação explícita para operações destrutivas.
 * Com `expectedName`, o usuário precisa digitar o nome do recurso.
 */
async function confirmDestructive(s, opts) {
    if (!s.settings().confirmDestructive)
        return true;
    if (opts.typeName) {
        const typed = await vscode.window.showInputBox({
            title: `${opts.action}: ${opts.name}`,
            prompt: `${opts.detail} Digite "${opts.name}" para confirmar.`,
            ignoreFocusOut: true,
            validateInput: (v) => (v === opts.name ? undefined : `Digite exatamente: ${opts.name}`),
        });
        return typed === opts.name;
    }
    const choice = await vscode.window.showWarningMessage(`${opts.action}: ${opts.name}?`, { modal: true, detail: opts.detail }, opts.action);
    return choice === opts.action;
}
async function ensureSignedIn(s) {
    if (s.auth.isSignedIn())
        return true;
    const choice = await vscode.window.showWarningMessage('Venix Cloud: você não está conectado.', 'Entrar');
    if (choice === 'Entrar')
        await vscode.commands.executeCommand('venixCloud.signIn');
    return s.auth.isSignedIn();
}
/** Garante que os dados de uma categoria estejam carregados antes de abrir um seletor. */
async function ensureLoaded(s, category) {
    if (s.store.snapshot[category].state === 'idle')
        await s.store.refresh(category);
}
async function pickApp(s, arg, placeHolder = 'Selecione a aplicação') {
    const fromNode = nodeOf(arg, 'app');
    if (fromNode)
        return fromNode.item;
    if (!(await ensureSignedIn(s)))
        return undefined;
    await ensureLoaded(s, 'apps');
    const apps = s.store.snapshot.apps.items;
    if (apps.length === 0) {
        void vscode.window.showInformationMessage('Venix Cloud: nenhuma aplicação encontrada.');
        return undefined;
    }
    const linked = s.linkedApp()?.id;
    const picked = await vscode.window.showQuickPick(apps.map((a) => ({ label: a.name, description: `${a.status}${a.id === linked ? ' · projeto atual' : ''}`, detail: a.id, app: a })), { placeHolder, matchOnDetail: true });
    return picked?.app;
}
async function askNumber(prompt, value, check) {
    const text = await vscode.window.showInputBox({
        prompt,
        value,
        ignoreFocusOut: true,
        validateInput: (v) => {
            const n = Number(v);
            if (v.trim() === '' || !Number.isFinite(n))
                return 'Informe um número.';
            return check(n);
        },
    });
    return text === undefined ? undefined : Number(text);
}
async function pickFolder() {
    const folders = vscode.workspace.workspaceFolders;
    if (!folders || folders.length === 0)
        return undefined;
    if (folders.length === 1)
        return folders[0];
    return vscode.window.showWorkspaceFolderPick({ placeHolder: 'Selecione a pasta do projeto' });
}
//# sourceMappingURL=shared.js.map