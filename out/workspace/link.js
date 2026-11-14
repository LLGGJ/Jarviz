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
exports.readLink = readLink;
exports.writeLink = writeLink;
const fs = __importStar(require("node:fs/promises"));
const path = __importStar(require("node:path"));
const constants_1 = require("../constants");
const ID_RE = /^[A-Za-z0-9_-]{8,64}$/;
/** Lê `.venix.json` (formato da CLI oficial: `{id, name}`). Devolve undefined se ausente ou inválido. */
async function readLink(root) {
    try {
        const parsed = JSON.parse(await fs.readFile(path.join(root, constants_1.LINK_FILE), 'utf8'));
        const id = parsed['id'];
        if (typeof id !== 'string' || !ID_RE.test(id))
            return undefined;
        const name = typeof parsed['name'] === 'string' ? parsed['name'].slice(0, 100) : undefined;
        return { id, name };
    }
    catch {
        return undefined;
    }
}
async function writeLink(root, link) {
    const body = JSON.stringify({ id: link.id, name: link.name }, null, 2) + '\n';
    await fs.writeFile(path.join(root, constants_1.LINK_FILE), body, { mode: 0o600 });
}
//# sourceMappingURL=link.js.map