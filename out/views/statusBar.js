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
exports.VenixStatusBar = void 0;
const vscode = __importStar(require("vscode"));
const format_1 = require("../utils/format");
class VenixStatusBar {
    auth;
    store;
    linkedAppName;
    item = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 50);
    subs = [];
    constructor(auth, store, linkedAppName) {
        this.auth = auth;
        this.store = store;
        this.linkedAppName = linkedAppName;
        this.subs.push(auth.onDidChange(() => this.update()), store.onDidChange(() => this.update()));
        this.update();
        this.item.show();
    }
    update() {
        const session = this.auth.getSessionInfo();
        if (!session) {
            this.item.text = '$(cloud) Venix: entrar';
            this.item.tooltip = 'Entrar com a Venix Cloud';
            this.item.command = 'venixCloud.signIn';
            return;
        }
        const linked = this.linkedAppName();
        const online = this.store.snapshot.apps.items.filter((a) => a.status === 'online').length;
        const total = this.store.snapshot.apps.items.length;
        this.item.text = `$(cloud) Venix${linked ? ` • ${linked}` : ''}`;
        this.item.tooltip = new vscode.MarkdownString(`**Venix Cloud** — ${session.account.label}\n\n${(0, format_1.pluralize)(online, 'aplicação online', 'aplicações online')} de ${total}` +
            (linked ? `\n\nProjeto vinculado: \`${linked}\`` : ''));
        this.item.command = 'venixCloud.showAccount';
    }
    dispose() {
        for (const s of this.subs)
            s.dispose();
        this.item.dispose();
    }
}
exports.VenixStatusBar = VenixStatusBar;
//# sourceMappingURL=statusBar.js.map