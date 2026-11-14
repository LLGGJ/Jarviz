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
exports.showDetails = showDetails;
const vscode = __importStar(require("vscode"));
const trustedHosts_1 = require("../security/trustedHosts");
const details_1 = require("../utils/details");
const webviewHtml_1 = require("./webviewHtml");
const messages_1 = require("./messages");
const CLIPBOARD_CLEAR_MS = 30_000;
const panels = new Map();
/**
 * Painel de detalhes. Valores sensíveis chegam MASCARADOS ao webview; o valor real só
 * é enviado quando o usuário clica em "Revelar", e a cópia é feita pela extensão
 * (o valor nunca passa pelo DOM). Chaves recebidas do webview são validadas.
 */
function showDetails(extensionUri, input) {
    const existing = panels.get(input.panelKey);
    if (existing) {
        existing.reveal();
        return;
    }
    const entries = (0, details_1.buildDetailEntries)(input.raw, input.preferredOrder);
    const byKey = new Map(entries.map((e) => [e.key, e]));
    const validKeys = new Set(byKey.keys());
    const panel = vscode.window.createWebviewPanel('venixCloud.details', input.title, vscode.ViewColumn.Active, {
        enableScripts: true,
        localResourceRoots: [vscode.Uri.joinPath(extensionUri, 'media')],
    });
    panels.set(input.panelKey, panel);
    panel.webview.html = (0, webviewHtml_1.buildWebviewHtml)(panel.webview, extensionUri, {
        title: input.title,
        script: 'details.js',
        bodyHtml: '<main id="root"><p class="muted">Carregando…</p></main>',
    });
    const send = () => {
        void panel.webview.postMessage({ type: 'init', title: input.title, subtitle: input.subtitle ?? '', fields: (0, details_1.toPublicFields)(entries) });
    };
    const sub = panel.webview.onDidReceiveMessage(async (raw) => {
        const msg = (0, messages_1.parseDetailsMessage)(raw, validKeys);
        if (!msg)
            return;
        if (msg.type === 'ready') {
            send();
            return;
        }
        const entry = byKey.get(msg.key);
        if (!entry)
            return;
        switch (msg.type) {
            case 'reveal':
                if (entry.sensitive)
                    void panel.webview.postMessage({ type: 'value', key: entry.key, value: entry.value });
                break;
            case 'hide':
                void panel.webview.postMessage({ type: 'value', key: entry.key, value: entry.display });
                break;
            case 'copy':
                await vscode.env.clipboard.writeText(entry.value);
                void vscode.window.showInformationMessage(entry.sensitive ? `"${entry.label}" copiado. A área de transferência será limpa em 30 s.` : `"${entry.label}" copiado.`);
                if (entry.sensitive) {
                    setTimeout(() => {
                        void vscode.env.clipboard.readText().then((current) => {
                            if (current === entry.value)
                                void vscode.env.clipboard.writeText('');
                        });
                    }, CLIPBOARD_CLEAR_MS);
                }
                break;
            case 'open':
                if (!entry.sensitive && (0, trustedHosts_1.isSafeExternalUrl)(entry.value))
                    await vscode.env.openExternal(vscode.Uri.parse(entry.value));
                break;
        }
    });
    panel.onDidDispose(() => {
        sub.dispose();
        panels.delete(input.panelKey);
    });
}
//# sourceMappingURL=DetailsPanel.js.map