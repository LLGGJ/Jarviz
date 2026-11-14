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
exports.dbCreate = dbCreate;
exports.dbCertificates = dbCertificates;
exports.dbDelete = dbDelete;
const fs = __importStar(require("node:fs/promises"));
const vscode = __importStar(require("vscode"));
const validators_1 = require("../api/validators");
const shared_1 = require("./shared");
async function pickDatabase(s, arg) {
    const node = (0, shared_1.nodeOf)(arg, 'database');
    if (node)
        return node.item;
    if (!(await (0, shared_1.ensureSignedIn)(s)))
        return undefined;
    if (s.store.snapshot.databases.state === 'idle')
        await s.store.refresh('databases');
    const dbs = s.store.snapshot.databases.items;
    if (dbs.length === 0) {
        void vscode.window.showInformationMessage('Venix Cloud: nenhum banco de dados encontrado.');
        return undefined;
    }
    const picked = await vscode.window.showQuickPick(dbs.map((d) => ({ label: d.name, description: d.engine, detail: d.id, db: d })), { placeHolder: 'Selecione o banco de dados' });
    return picked?.db;
}
async function dbCreate(s) {
    if (!(await (0, shared_1.ensureSignedIn)(s)))
        return;
    const name = await vscode.window.showInputBox({
        title: 'Novo banco (1/5): nome',
        prompt: 'Mínimo 3 caracteres: letras minúsculas, números e hifens.',
        ignoreFocusOut: true,
        validateInput: validators_1.checkDbName,
    });
    if (!name)
        return;
    const engine = await vscode.window.showQuickPick([...validators_1.DB_ENGINES], { title: 'Novo banco (2/5): motor', ignoreFocusOut: true });
    if (!engine)
        return;
    const ram = await (0, shared_1.askNumber)('Novo banco (3/5): RAM em MB (mínimo 512)', '1024', validators_1.checkDbRam);
    if (ram === undefined)
        return;
    const region = await vscode.window.showQuickPick([...validators_1.REGIONS], { title: 'Novo banco (4/5): região', ignoreFocusOut: true });
    if (!region)
        return;
    const manager = await vscode.window.showQuickPick([
        { label: 'Sim', description: 'phpMyAdmin / pgAdmin / Mongo Express', value: true },
        { label: 'Não', value: false },
    ], { title: 'Novo banco (5/5): instalar painel web de gerenciamento?', ignoreFocusOut: true });
    if (!manager)
        return;
    await (0, shared_1.run)(s, `Venix Cloud: criando banco ${name}…`, () => s.api().databases.create({ name, engine: engine, ram, region: region, installManager: manager.value }), { success: `banco ${name} criado.`, refresh: ['databases'] });
}
async function dbCertificates(s, arg) {
    const db = await pickDatabase(s, arg);
    if (!db)
        return;
    const target = await vscode.window.showSaveDialog({
        defaultUri: vscode.Uri.file(`${db.name}-certs.zip`),
        filters: { ZIP: ['zip'] },
        title: 'Salvar certificados mTLS',
    });
    if (!target)
        return;
    const res = await (0, shared_1.run)(s, `Venix Cloud: baixando certificados de ${db.name}…`, (signal) => s.api().databases.downloadCertificates(db.id, signal), { cancellable: true });
    if (!res.ok)
        return;
    if (target.scheme === 'file')
        await fs.writeFile(target.fsPath, res.value, { mode: 0o600 });
    else
        await vscode.workspace.fs.writeFile(target, res.value);
    void vscode.window.showWarningMessage(`Venix Cloud: certificados salvos. O ZIP contém a CHAVE PRIVADA do cliente — não o versione nem o compartilhe.`);
}
async function dbDelete(s, arg) {
    const db = await pickDatabase(s, arg);
    if (!db)
        return;
    const ok = await (0, shared_1.confirmDestructive)(s, {
        action: 'Excluir banco',
        name: db.name,
        typeName: true,
        detail: 'Exclui PERMANENTEMENTE o banco de dados, o volume e o IP na malha. Todos os dados serão perdidos.',
    });
    if (!ok)
        return;
    await (0, shared_1.run)(s, `Venix Cloud: excluindo ${db.name}…`, () => s.api().databases.remove(db.id), { success: `banco ${db.name} excluído.`, refresh: ['databases'] });
}
//# sourceMappingURL=databases.js.map