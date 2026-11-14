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
exports.isBlockedDirectory = isBlockedDirectory;
exports.isSecretFile = isSecretFile;
const path = __importStar(require("node:path"));
/**
 * Arquivos que NUNCA entram em pacotes de publicação, independentemente das
 * configurações do usuário ou de `.venixignore`.
 */
const SECRET_BASENAME_PATTERNS = [
    /^\.env(\..*)?$/i,
    /^.*\.(pem|key|p12|pfx|jks|keystore|ppk|crt|cer|der)$/i,
    /^id_(rsa|dsa|ecdsa|ed25519)(\.pub)?$/i,
    /^\.(npmrc|netrc|pypirc|git-credentials|htpasswd)$/i,
    /^credentials(\..*)?$/i,
    /^secrets?(\..*)?$/i,
    /^service-account.*\.json$/i,
    /^\.venix\.json$/i,
];
const BLOCKED_DIRS = new Set(['.git', '.venix', '.ssh', '.aws', '.gnupg', '.kube']);
function isBlockedDirectory(name) {
    return BLOCKED_DIRS.has(name);
}
function isSecretFile(relPath) {
    const base = path.posix.basename(relPath.replace(/\\/g, '/'));
    return SECRET_BASENAME_PATTERNS.some((p) => p.test(base));
}
//# sourceMappingURL=secretFiles.js.map