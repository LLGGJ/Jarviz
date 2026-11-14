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
exports.commandTable = commandTable;
exports.registerCommands = registerCommands;
const vscode = __importStar(require("vscode"));
const apps_1 = require("./apps");
const auth_1 = require("./auth");
const databases_1 = require("./databases");
const details_1 = require("./details");
const games_1 = require("./games");
const snapshots_1 = require("./snapshots");
const storage_1 = require("./storage");
const workspace_1 = require("./workspace");
/** Tabela única de comandos: o teste de consistência a compara com o package.json. */
function commandTable(s) {
    return {
        'venixCloud.signIn': () => (0, auth_1.signInOAuth)(s),
        'venixCloud.signInManual': () => (0, auth_1.signInOAuth)(s, 'manual'),
        'venixCloud.signInWithApiToken': () => (0, auth_1.signInApiToken)(s),
        'venixCloud.signOut': () => (0, auth_1.signOut)(s),
        'venixCloud.refresh': async () => {
            if (s.auth.isSignedIn())
                await s.store.refreshAll();
        },
        'venixCloud.showAccount': () => (0, auth_1.showAccount)(s),
        'venixCloud.openWebPanel': () => (0, auth_1.openWebPanel)(),
        'venixCloud.checkConnection': () => (0, auth_1.checkConnection)(s),
        'venixCloud.openSettings': () => (0, auth_1.openSettings)(),
        'venixCloud.showDetails': (node) => (0, details_1.showNodeDetails)(s, node),
        'venixCloud.copyId': (node) => (0, details_1.copyId)(node),
        'venixCloud.appStart': (node) => (0, apps_1.appAction)(s, 'START', node),
        'venixCloud.appStop': (node) => (0, apps_1.appAction)(s, 'STOP', node),
        'venixCloud.appRestart': (node) => (0, apps_1.appAction)(s, 'RESTART', node),
        'venixCloud.appDeploy': (node) => (0, apps_1.appDeploy)(s, node),
        'venixCloud.appEditConfig': (node) => (0, apps_1.appEditConfig)(s, node),
        'venixCloud.appCreate': () => (0, apps_1.appCreate)(s),
        'venixCloud.appDelete': (node) => (0, apps_1.appDelete)(s, node),
        'venixCloud.appLogs': (node) => (0, apps_1.appLogs)(s, node),
        'venixCloud.appOpenUrl': (node) => (0, apps_1.appOpenUrl)(s, node),
        'venixCloud.gameCreate': () => (0, games_1.gameCreate)(s),
        'venixCloud.gameCommand': (node) => (0, games_1.gameCommand)(s, node),
        'venixCloud.gameDelete': (node) => (0, games_1.gameDelete)(s, node),
        'venixCloud.dbCreate': () => (0, databases_1.dbCreate)(s),
        'venixCloud.dbCertificates': (node) => (0, databases_1.dbCertificates)(s, node),
        'venixCloud.dbDelete': (node) => (0, databases_1.dbDelete)(s, node),
        'venixCloud.storageUpload': (arg) => (0, storage_1.storageUpload)(s, arg),
        'venixCloud.storageCopyUrl': (node) => (0, storage_1.storageCopyUrl)(s, node),
        'venixCloud.storageOpenUrl': (node) => (0, storage_1.storageOpenUrl)(s, node),
        'venixCloud.snapshotCreate': (node) => (0, snapshots_1.snapshotCreate)(s, node),
        'venixCloud.snapshotDownload': (node) => (0, snapshots_1.snapshotDownload)(s, node),
        'venixCloud.snapshotDelete': (node) => (0, snapshots_1.snapshotDelete)(s, node),
        'venixCloud.linkWorkspace': (node) => (0, workspace_1.linkWorkspace)(s, node),
        'venixCloud.publishWorkspace': (node) => (0, workspace_1.publishWorkspace)(s, node),
        'venixCloud.uploadFile': (arg) => (0, workspace_1.uploadFile)(s, arg),
    };
}
function registerCommands(s) {
    return Object.entries(commandTable(s)).map(([id, handler]) => vscode.commands.registerCommand(id, handler));
}
//# sourceMappingURL=index.js.map