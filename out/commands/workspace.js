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
exports.linkWorkspace = linkWorkspace;
exports.publishWorkspace = publishWorkspace;
exports.uploadFile = uploadFile;
exports.describeLastPush = describeLastPush;
const fs = __importStar(require("node:fs/promises"));
const path = __importStar(require("node:path"));
const vscode = __importStar(require("vscode"));
const validators_1 = require("../api/validators");
const constants_1 = require("../constants");
const format_1 = require("../utils/format");
const link_1 = require("../workspace/link");
const archive_1 = require("./archive");
const storage_1 = require("./storage");
const shared_1 = require("./shared");
/** Vincula a pasta a uma aplicação remota (`.venix.json`, compatível com `venix link`). */
async function linkWorkspace(s, arg) {
    const folder = await (0, shared_1.pickFolder)();
    if (!folder) {
        void vscode.window.showWarningMessage('Venix Cloud: abra uma pasta no VS Code primeiro.');
        return;
    }
    const app = await (0, shared_1.pickApp)(s, (0, shared_1.nodeOf)(arg, 'app') ? arg : undefined, 'Aplicação de destino para este projeto');
    if (!app)
        return;
    await (0, link_1.writeLink)(folder.uri.fsPath, { id: app.id, name: app.name });
    s.setLinkedApp(app.id, app.name);
    void vscode.window.showInformationMessage(`Venix Cloud: projeto vinculado a ${app.name} (.venix.json). Não versione esse arquivo se o repositório for público.`);
}
/**
 * Publica o projeto: ZIP → upload (/files/upload) → extração (/files/extract) → reinício.
 * Mesmo fluxo do `venix push` da CLI oficial, usando apenas endpoints documentados.
 */
async function publishWorkspace(s, arg) {
    if (!(await (0, shared_1.ensureSignedIn)(s)))
        return;
    const folder = await (0, shared_1.pickFolder)();
    if (!folder) {
        void vscode.window.showWarningMessage('Venix Cloud: abra uma pasta no VS Code primeiro.');
        return;
    }
    await (0, shared_1.ensureLoaded)(s, 'apps');
    const link = await (0, link_1.readLink)(folder.uri.fsPath);
    let app = (0, shared_1.nodeOf)(arg, 'app')?.item ?? (link ? s.store.findApp(link.id) : undefined);
    if (!app)
        app = await (0, shared_1.pickApp)(s, undefined, 'Aplicação de destino da publicação');
    if (!app)
        return;
    const target = app;
    const prepared = await (0, archive_1.prepareArchive)(s, folder, `${target.name} (${target.id})`);
    if (!prepared)
        return;
    const restart = s.settings().restartAfterUpload;
    const res = await (0, shared_1.run)(s, `Venix Cloud: publicando em ${target.name}…`, async (signal) => {
        const api = s.api();
        await api.apps.uploadFile(target.id, constants_1.REMOTE_UPDATE_ZIP, { filename: 'update.zip', data: prepared.zip }, signal);
        await api.apps.extract(target.id, constants_1.REMOTE_UPDATE_ZIP, signal);
        // Limpeza do pacote temporário: melhor esforço, não afeta o resultado.
        await api.apps.deleteFile(target.id, constants_1.REMOTE_UPDATE_ZIP).catch(() => undefined);
        if (restart)
            await api.apps.action(target.id, 'RESTART');
    }, { cancellable: true, success: `${prepared.scan.files.length} arquivos enviados para ${target.name}${restart ? ' e aplicação reiniciada' : ''}.`, refresh: ['apps'] });
    if (!res.ok)
        return;
    await s.context.workspaceState.update(constants_1.STATE_LAST_PUSH_KEY, { appId: target.id, appName: target.name, at: Date.now(), files: prepared.scan.files.length });
    if (!link || link.id !== target.id) {
        const choice = await vscode.window.showInformationMessage(`Vincular esta pasta a ${target.name} para os próximos envios?`, 'Vincular');
        if (choice === 'Vincular') {
            await (0, link_1.writeLink)(folder.uri.fsPath, { id: target.id, name: target.name });
            s.setLinkedApp(target.id, target.name);
        }
    }
}
/** Envia UM arquivo escolhido pelo usuário para o volume da aplicação. */
async function uploadFile(s, arg) {
    if (!(await (0, shared_1.ensureSignedIn)(s)))
        return;
    const uri = await (0, storage_1.pickLocalFile)(arg, 'Arquivo para enviar à aplicação');
    if (!uri)
        return;
    const stat = await fs.stat(uri.fsPath);
    if (stat.size > constants_1.LIMITS.appArchiveMaxBytes) {
        void vscode.window.showErrorMessage('Venix Cloud: o arquivo excede o limite de 500 MB.');
        return;
    }
    const base = path.basename(uri.fsPath);
    if (!(await (0, storage_1.confirmSecretFile)(base)))
        return;
    const app = await (0, shared_1.pickApp)(s, undefined, 'Aplicação de destino');
    if (!app)
        return;
    const remote = await vscode.window.showInputBox({
        title: `Enviar ${base} para ${app.name}`,
        prompt: 'Caminho remoto completo do arquivo no volume da aplicação.',
        value: `/${base}`,
        ignoreFocusOut: true,
        validateInput: validators_1.checkRemotePath,
    });
    if (!remote)
        return;
    const data = new Uint8Array(await fs.readFile(uri.fsPath));
    await (0, shared_1.run)(s, `Venix Cloud: enviando ${base}…`, (signal) => s.api().apps.uploadFile(app.id, remote, { filename: base, data }, signal), {
        cancellable: true,
        success: `${base} enviado para ${app.name}:${remote}.`,
    });
}
function describeLastPush(s) {
    const last = s.context.workspaceState.get(constants_1.STATE_LAST_PUSH_KEY);
    return last ? `Último envio (registro local): ${last.files} arquivos para ${last.appName} em ${(0, format_1.formatTime)(last.at)}` : undefined;
}
//# sourceMappingURL=workspace.js.map