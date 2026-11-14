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
exports.pickLocalFile = pickLocalFile;
exports.confirmSecretFile = confirmSecretFile;
exports.storageUpload = storageUpload;
exports.storageCopyUrl = storageCopyUrl;
exports.storageOpenUrl = storageOpenUrl;
const fs = __importStar(require("node:fs/promises"));
const path = __importStar(require("node:path"));
const vscode = __importStar(require("vscode"));
const validators_1 = require("../api/validators");
const trustedHosts_1 = require("../security/trustedHosts");
const secretFiles_1 = require("../security/secretFiles");
const projectDetector_1 = require("../workspace/projectDetector");
const shared_1 = require("./shared");
async function pickLocalFile(arg, title) {
    if (arg instanceof vscode.Uri)
        return arg;
    const picked = await vscode.window.showOpenDialog({ canSelectMany: false, canSelectFolders: false, title, defaultUri: vscode.window.activeTextEditor?.document.uri });
    return picked?.[0];
}
/** Arquivos que parecem conter segredos exigem confirmação explícita (nunca são enviados automaticamente). */
async function confirmSecretFile(fileName) {
    if (!(0, secretFiles_1.isSecretFile)(fileName))
        return true;
    const choice = await vscode.window.showWarningMessage(`"${fileName}" parece conter segredos (chaves, credenciais ou variáveis de ambiente).`, { modal: true, detail: 'Enviar para um destino público ou compartilhado pode expor credenciais.' }, 'Enviar mesmo assim');
    return choice === 'Enviar mesmo assim';
}
async function storageUpload(s, arg) {
    if (!(await (0, shared_1.ensureSignedIn)(s)))
        return;
    const uri = await pickLocalFile(arg, 'Arquivo para o CDN (1 KB a 100 MB)');
    if (!uri)
        return;
    const stat = await fs.stat(uri.fsPath);
    const sizeProblem = (0, validators_1.checkCdnSize)(stat.size);
    if (sizeProblem) {
        void vscode.window.showErrorMessage(`Venix Cloud: ${sizeProblem}`);
        return;
    }
    const base = path.basename(uri.fsPath);
    if (!(await confirmSecretFile(base)))
        return;
    const name = await vscode.window.showInputBox({
        title: 'Upload para o CDN (1/3): nome do objeto',
        prompt: '3 a 32 caracteres: letras, números e "_".',
        value: (0, projectDetector_1.suggestObjectName)(base),
        ignoreFocusOut: true,
        validateInput: validators_1.checkObjectName,
    });
    if (!name)
        return;
    const prefix = await vscode.window.showInputBox({
        title: 'Upload para o CDN (2/3): pasta/prefixo (opcional)',
        ignoreFocusOut: true,
        validateInput: (v) => (v ? (0, validators_1.checkObjectPrefix)(v) : undefined),
    });
    if (prefix === undefined)
        return;
    const expireText = await vscode.window.showInputBox({
        title: 'Upload para o CDN (3/3): expiração em dias (opcional, 1 a 365)',
        ignoreFocusOut: true,
        validateInput: (v) => (v ? (Number.isFinite(Number(v)) ? (0, validators_1.checkExpireDays)(Number(v)) : 'Informe um número.') : undefined),
    });
    if (expireText === undefined)
        return;
    const data = new Uint8Array(await fs.readFile(uri.fsPath));
    const res = await (0, shared_1.run)(s, `Venix Cloud: enviando ${base}…`, (signal) => s.api().storage.upload({ name, prefix: prefix || undefined, expireDays: expireText ? Number(expireText) : undefined, file: { filename: base, data } }, signal), { cancellable: true, refresh: ['storage'] });
    if (!res.ok)
        return;
    const choice = await vscode.window.showInformationMessage(`Venix Cloud: upload concluído.\n${res.value.url}`, 'Copiar URL', 'Abrir');
    if (choice === 'Copiar URL')
        await vscode.env.clipboard.writeText(res.value.url);
    if (choice === 'Abrir' && (0, trustedHosts_1.isSafeExternalUrl)(res.value.url))
        await vscode.env.openExternal(vscode.Uri.parse(res.value.url));
}
async function storageCopyUrl(_s, arg) {
    const node = (0, shared_1.nodeOf)(arg, 'object');
    if (!node?.item.url) {
        void vscode.window.showWarningMessage('Venix Cloud: este objeto não informou uma URL.');
        return;
    }
    await vscode.env.clipboard.writeText(node.item.url);
    void vscode.window.showInformationMessage('Venix Cloud: URL copiada para a área de transferência.');
}
async function storageOpenUrl(_s, arg) {
    const url = (0, shared_1.nodeOf)(arg, 'object')?.item.url;
    if (!url || !(0, trustedHosts_1.isSafeExternalUrl)(url)) {
        void vscode.window.showWarningMessage('Venix Cloud: URL indisponível ou insegura.');
        return;
    }
    await vscode.env.openExternal(vscode.Uri.parse(url));
}
//# sourceMappingURL=storage.js.map