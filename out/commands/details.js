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
exports.showNodeDetails = showNodeDetails;
exports.copyId = copyId;
const vscode = __importStar(require("vscode"));
const DetailsPanel_1 = require("../views/DetailsPanel");
const ORDER = ['id', 'name', 'appName', 'status', 'state', 'runtime', 'engine', 'game', 'region', 'host', 'ip', 'port', 'url'];
/** Abre o painel de detalhes mostrando exatamente os campos devolvidos pela API (sensíveis mascarados). */
function showNodeDetails(s, node) {
    const n = node;
    if (!n || typeof n !== 'object' || !('kind' in n))
        return;
    switch (n.kind) {
        case 'app':
            return (0, DetailsPanel_1.showDetails)(s.context.extensionUri, { panelKey: `app:${n.item.id}`, title: n.item.name, subtitle: 'Aplicação', raw: n.item.raw, preferredOrder: ORDER });
        case 'game':
            return (0, DetailsPanel_1.showDetails)(s.context.extensionUri, { panelKey: `game:${n.item.id}`, title: n.item.name, subtitle: 'Servidor de jogo', raw: n.item.raw, preferredOrder: ORDER });
        case 'database':
            return (0, DetailsPanel_1.showDetails)(s.context.extensionUri, { panelKey: `db:${n.item.id}`, title: n.item.name, subtitle: 'Banco de dados — credenciais ocultas por padrão', raw: n.item.raw, preferredOrder: ORDER });
        case 'object':
            return (0, DetailsPanel_1.showDetails)(s.context.extensionUri, { panelKey: `obj:${n.item.id}`, title: n.item.name, subtitle: 'Objeto do CDN', raw: n.item.raw, preferredOrder: ['name', 'url', 'size'] });
        case 'snapshot':
            return (0, DetailsPanel_1.showDetails)(s.context.extensionUri, { panelKey: `snap:${n.item.id}`, title: n.item.name, subtitle: 'Snapshot', raw: n.item.raw, preferredOrder: ORDER });
        default:
            return;
    }
}
async function copyId(arg) {
    const item = arg?.item;
    if (typeof item?.id !== 'string')
        return;
    await vscode.env.clipboard.writeText(item.id);
    void vscode.window.showInformationMessage('Venix Cloud: ID copiado.');
}
//# sourceMappingURL=details.js.map