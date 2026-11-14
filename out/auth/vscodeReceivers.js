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
exports.ManualReceiver = exports.UriCallbackRouter = void 0;
const vscode = __importStar(require("vscode"));
const constants_1 = require("../constants");
/** Roteia `vscode://<publisher>.<extensão>/callback` para o login em andamento (modo "vscode"). */
class UriCallbackRouter {
    pending;
    handleUri(uri) {
        if (uri.path !== constants_1.OAUTH_CALLBACK_PATH)
            return;
        if (!this.pending) {
            void vscode.window.showWarningMessage('Venix Cloud: nenhum login em andamento. Inicie com "Venix Cloud: Entrar".');
            return;
        }
        const params = {};
        for (const [k, v] of new URLSearchParams(uri.query)) {
            if (!(k in params))
                params[k] = v;
        }
        const result = this.pending(params);
        if (!result.ok)
            void vscode.window.showErrorMessage(`Venix Cloud: ${result.message}`);
    }
    createReceiver(redirectUri) {
        return {
            redirectUri,
            listen: (handler) => {
                this.pending = handler;
                return Promise.resolve();
            },
            dispose: () => {
                this.pending = undefined;
            },
        };
    }
}
exports.UriCallbackRouter = UriCallbackRouter;
/**
 * Fallback para ambientes onde o navegador não alcança 127.0.0.1 (SSH, WSL, Codespaces)
 * ou a porta está ocupada: o usuário cola a URL final. O `state` é validado igualmente.
 */
class ManualReceiver {
    redirectUri;
    box;
    constructor(redirectUri) {
        this.redirectUri = redirectUri;
    }
    listen(handler, context) {
        const box = vscode.window.createInputBox();
        this.box = box;
        box.title = 'Venix Cloud — Entrar (modo manual)';
        box.prompt = 'Autorize no navegador e cole aqui a URL final da página (mesmo que ela mostre erro de conexão).';
        box.placeholder = `${this.redirectUri}?code=…&state=…`;
        box.ignoreFocusOut = true;
        box.buttons = [{ iconPath: new vscode.ThemeIcon('copy'), tooltip: 'Copiar link de autorização' }];
        box.onDidTriggerButton(() => {
            void vscode.env.clipboard.writeText(context.authUrl);
            void vscode.window.showInformationMessage('Link de autorização copiado.');
        });
        box.onDidAccept(() => {
            const text = box.value.trim();
            let url;
            try {
                url = new URL(text);
            }
            catch {
                box.validationMessage = 'Cole a URL completa, começando com http://…';
                return;
            }
            const expected = new URL(this.redirectUri);
            if (url.origin !== expected.origin || url.pathname !== expected.pathname) {
                box.validationMessage = `A URL deve começar com ${this.redirectUri}`;
                return;
            }
            const params = {};
            for (const [k, v] of url.searchParams) {
                if (!(k in params))
                    params[k] = v;
            }
            const result = handler(params);
            if (result.ok)
                box.hide();
            else
                box.validationMessage = result.message;
        });
        box.onDidHide(() => box.dispose());
        box.show();
        return Promise.resolve();
    }
    dispose() {
        this.box?.dispose();
        this.box = undefined;
    }
}
exports.ManualReceiver = ManualReceiver;
//# sourceMappingURL=vscodeReceivers.js.map