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
exports.snapshotCreate = snapshotCreate;
exports.snapshotDownload = snapshotDownload;
exports.snapshotDelete = snapshotDelete;
const vscode = __importStar(require("vscode"));
const trustedHosts_1 = require("../security/trustedHosts");
const format_1 = require("../utils/format");
const shared_1 = require("./shared");
async function snapshotCreate(s, arg) {
    if (!(await (0, shared_1.ensureSignedIn)(s)))
        return;
    let resource;
    const app = (0, shared_1.nodeOf)(arg, 'app');
    const db = (0, shared_1.nodeOf)(arg, 'database');
    if (app)
        resource = { id: app.item.id, name: app.item.name };
    else if (db)
        resource = { id: db.item.id, name: db.item.name };
    else {
        await (0, shared_1.ensureLoaded)(s, 'apps');
        await (0, shared_1.ensureLoaded)(s, 'databases');
        const picked = await vscode.window.showQuickPick([
            ...s.store.snapshot.apps.items.map((a) => ({ label: a.name, description: 'aplicação', detail: a.id, res: { id: a.id, name: a.name } })),
            ...s.store.snapshot.databases.items.map((d) => ({ label: d.name, description: 'banco de dados', detail: d.id, res: { id: d.id, name: d.name } })),
        ], { placeHolder: 'Recurso para o snapshot (aplicações e bancos de dados)', matchOnDetail: true });
        resource = picked?.res;
    }
    if (!resource)
        return;
    const target = resource;
    const name = await vscode.window.showInputBox({
        title: `Snapshot de ${target.name}`,
        prompt: 'Nome do snapshot',
        value: (0, format_1.timestampName)('backup'),
        ignoreFocusOut: true,
        validateInput: (v) => (v.trim() && v.length <= 100 ? undefined : 'Informe um nome de até 100 caracteres.'),
    });
    if (!name)
        return;
    await (0, shared_1.run)(s, `Venix Cloud: criando snapshot de ${target.name}…`, () => s.api().snapshots.create(target.id, name), {
        success: `snapshot "${name}" solicitado.`,
        refresh: ['snapshots'],
    });
}
async function snapshotDownload(s, arg) {
    const node = (0, shared_1.nodeOf)(arg, 'snapshot');
    if (!node)
        return;
    const res = await (0, shared_1.run)(s, 'Venix Cloud: gerando link de download…', () => s.api().snapshots.downloadUrl(node.item.id));
    if (!res.ok)
        return;
    if (!(0, trustedHosts_1.isSafeExternalUrl)(res.value)) {
        void vscode.window.showErrorMessage('Venix Cloud: a API devolveu um link de download inválido.');
        return;
    }
    await vscode.env.openExternal(vscode.Uri.parse(res.value));
}
async function snapshotDelete(s, arg) {
    const node = (0, shared_1.nodeOf)(arg, 'snapshot');
    if (!node)
        return;
    const ok = await (0, shared_1.confirmDestructive)(s, {
        action: 'Excluir snapshot',
        name: node.item.name,
        typeName: true,
        detail: 'Exclui o snapshot e libera o espaço de armazenamento. Esta ação não pode ser desfeita.',
    });
    if (!ok)
        return;
    await (0, shared_1.run)(s, `Venix Cloud: excluindo ${node.item.name}…`, () => s.api().snapshots.remove(node.item.id), {
        success: `snapshot ${node.item.name} excluído.`,
        refresh: ['snapshots'],
    });
}
//# sourceMappingURL=snapshots.js.map