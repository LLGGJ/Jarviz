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
exports.readSettings = readSettings;
const vscode = __importStar(require("vscode"));
const constants_1 = require("../constants");
const trustedHosts_1 = require("../security/trustedHosts");
const clamp = (n, min, max) => Math.min(max, Math.max(min, Math.round(n)));
function readSettings() {
    const cfg = vscode.workspace.getConfiguration(constants_1.CONFIG_SECTION);
    const requested = cfg.get('apiBaseUrl', constants_1.DEFAULT_API_BASE_URL);
    let apiBaseUrl = constants_1.DEFAULT_API_BASE_URL;
    let apiBaseUrlError;
    try {
        apiBaseUrl = (0, trustedHosts_1.normalizeBaseUrl)(requested);
    }
    catch (err) {
        // Valor inválido ou não confiável: volta ao padrão; o token NUNCA vai para o destino rejeitado.
        apiBaseUrlError = err instanceof Error ? err.message : 'URL-base inválida.';
    }
    const mode = cfg.get('oauth.redirectMode', 'loopback');
    const exclude = cfg.get('publish.exclude', []);
    return {
        apiBaseUrl,
        apiBaseUrlError,
        autoRefresh: cfg.get('autoRefresh', false),
        refreshIntervalSec: clamp(cfg.get('refreshInterval', 60), 30, 3600),
        confirmDestructive: cfg.get('confirmDestructiveActions', true),
        redirectMode: mode === 'vscode' ? 'vscode' : 'loopback',
        callbackPort: clamp(cfg.get('oauth.callbackPort', constants_1.OAUTH_DEFAULT_CALLBACK_PORT), 1024, 65535),
        publishExclude: Array.isArray(exclude) ? exclude.filter((x) => typeof x === 'string') : [],
        restartAfterUpload: cfg.get('publish.restartAfterUpload', true),
    };
}
//# sourceMappingURL=config.js.map