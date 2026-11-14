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
exports.VenixAuthenticationProvider = void 0;
const vscode = __importStar(require("vscode"));
const constants_1 = require("../constants");
const SESSION_ID = 'venixcloud-session';
/**
 * Adaptador fino que expõe a sessão ao menu "Contas" do VS Code. A lógica de OAuth,
 * PKCE e armazenamento seguro continua no AuthenticationManager.
 * Observação: extensões de terceiros só obtêm o token após consentimento explícito do usuário.
 */
class VenixAuthenticationProvider {
    auth;
    signIn;
    emitter = new vscode.EventEmitter();
    onDidChangeSessions = this.emitter.event;
    sub;
    hadSession;
    constructor(auth, signIn) {
        this.auth = auth;
        this.signIn = signIn;
        this.hadSession = auth.isSignedIn();
        this.sub = auth.onDidChange(() => {
            const has = auth.isSignedIn();
            const session = this.currentSession();
            if (has && !this.hadSession && session)
                this.emitter.fire({ added: [session], removed: [], changed: [] });
            else if (!has && this.hadSession)
                this.emitter.fire({ added: [], removed: [{ id: SESSION_ID, accessToken: '', account: { id: 'venix', label: 'Venix Cloud' }, scopes: [] }], changed: [] });
            this.hadSession = has;
        });
    }
    static register(auth, signIn) {
        const provider = new VenixAuthenticationProvider(auth, signIn);
        const registration = vscode.authentication.registerAuthenticationProvider(constants_1.AUTH_PROVIDER_ID, 'Venix Cloud', provider);
        return { provider, registration };
    }
    currentSession() {
        const info = this.auth.getSessionInfo();
        const token = this.auth.knownSecrets()[0];
        if (!info || !token)
            return undefined;
        return { id: SESSION_ID, accessToken: token, account: { id: info.account.id ?? 'venix', label: info.account.label }, scopes: [] };
    }
    async getSessions() {
        const s = this.currentSession();
        return s ? [s] : [];
    }
    async createSession() {
        await this.signIn();
        const s = this.currentSession();
        if (!s)
            throw new Error('Login não concluído.');
        return s;
    }
    async removeSession() {
        await this.auth.signOut();
    }
    dispose() {
        this.sub.dispose();
        this.emitter.dispose();
    }
}
exports.VenixAuthenticationProvider = VenixAuthenticationProvider;
//# sourceMappingURL=VenixAuthenticationProvider.js.map