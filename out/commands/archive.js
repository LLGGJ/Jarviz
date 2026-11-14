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
exports.prepareArchive = prepareArchive;
const vscode = __importStar(require("vscode"));
const constants_1 = require("../constants");
const format_1 = require("../utils/format");
const scanner_1 = require("../workspace/scanner");
const preview_1 = require("../workspace/preview");
const zip_1 = require("../workspace/zip");
const shared_1 = require("./shared");
/**
 * Varre o projeto, mostra a pré-visualização e só então gera o ZIP.
 * Devolve undefined se o usuário cancelar ou houver erro.
 */
async function prepareArchive(s, folder, target) {
    if (!vscode.workspace.isTrusted) {
        void vscode.window.showWarningMessage('Venix Cloud: confie neste workspace para publicar arquivos.');
        return undefined;
    }
    let scan;
    try {
        scan = await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: 'Venix Cloud: analisando o projeto…' }, () => (0, scanner_1.scanProject)(folder.uri.fsPath, { exclude: s.settings().publishExclude }));
    }
    catch (err) {
        (0, shared_1.reportError)(s, 'Análise do projeto', err);
        return undefined;
    }
    if (scan.files.length === 0) {
        void vscode.window.showWarningMessage('Venix Cloud: nenhum arquivo elegível para envio (tudo foi excluído ou bloqueado).');
        return undefined;
    }
    for (;;) {
        const send = `$(cloud-upload) Enviar ${scan.files.length} arquivos (${(0, format_1.formatBytes)(scan.totalBytes)})`;
        const list = '$(list-flat) Ver lista completa de arquivos';
        const picked = await vscode.window.showQuickPick([
            { label: send, description: target, detail: (0, preview_1.summarizeBlocked)(scan) },
            { label: list },
            { label: '$(close) Cancelar' },
        ], { title: 'Pré-visualização da publicação', placeHolder: 'Revise antes de enviar', ignoreFocusOut: true });
        if (!picked || picked.label.includes('Cancelar'))
            return undefined;
        if (picked.label === list) {
            const doc = await vscode.workspace.openTextDocument({ content: (0, preview_1.buildPreviewListing)(scan, target), language: 'plaintext' });
            await vscode.window.showTextDocument(doc, { preview: true });
            continue;
        }
        break;
    }
    try {
        const zip = await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: 'Venix Cloud: compactando…', cancellable: true }, (_p, token) => {
            const controller = new AbortController();
            token.onCancellationRequested(() => controller.abort());
            return (0, zip_1.buildZip)(scan.files, { maxBytes: constants_1.LIMITS.appArchiveMaxBytes, signal: controller.signal });
        });
        return { zip, scan };
    }
    catch (err) {
        if (err instanceof zip_1.ZipLimitError)
            void vscode.window.showErrorMessage(`Venix Cloud: ${err.message}`);
        else if (!(err instanceof Error && err.message === 'cancelado'))
            (0, shared_1.reportError)(s, 'Compactação', err);
        return undefined;
    }
}
//# sourceMappingURL=archive.js.map