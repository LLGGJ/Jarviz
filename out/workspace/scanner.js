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
exports.readIgnoreRules = readIgnoreRules;
exports.scanProject = scanProject;
const fs = __importStar(require("node:fs/promises"));
const path = __importStar(require("node:path"));
const constants_1 = require("../constants");
const secretFiles_1 = require("../security/secretFiles");
const ignore_1 = require("./ignore");
const MAX_SAMPLE = 200;
async function readIgnoreRules(root) {
    try {
        const text = await fs.readFile(path.join(root, constants_1.IGNORE_FILE), 'utf8');
        return text.split(/\r?\n/);
    }
    catch {
        return [];
    }
}
/**
 * Percorre o projeto aplicando, nesta ordem: bloqueios fixos de segurança
 * (segredos, .git, links simbólicos), regras do usuário e `.venixignore`.
 */
async function scanProject(root, options) {
    const matcher = (0, ignore_1.createIgnoreMatcher)([...options.exclude, ...(await readIgnoreRules(root))]);
    const maxFiles = options.maxFiles ?? 65_000;
    const result = {
        files: [],
        totalBytes: 0,
        blockedSecrets: [],
        excluded: [],
        excludedCount: 0,
        skippedSymlinks: 0,
        truncated: false,
    };
    const note = (list, rel) => {
        if (list.length < MAX_SAMPLE)
            list.push(rel);
    };
    async function walk(dirAbs, dirRel) {
        if (options.signal?.aborted)
            throw new Error('cancelado');
        const entries = await fs.readdir(dirAbs, { withFileTypes: true });
        entries.sort((a, b) => a.name.localeCompare(b.name));
        for (const entry of entries) {
            const rel = dirRel ? `${dirRel}/${entry.name}` : entry.name;
            const abs = path.join(dirAbs, entry.name);
            if (entry.isSymbolicLink()) {
                result.skippedSymlinks++;
                continue;
            }
            if (entry.isDirectory()) {
                if ((0, secretFiles_1.isBlockedDirectory)(entry.name)) {
                    if (entry.name !== '.git')
                        note(result.blockedSecrets, `${rel}/`);
                    continue;
                }
                if (matcher(rel, true)) {
                    result.excludedCount++;
                    note(result.excluded, `${rel}/`);
                    continue;
                }
                await walk(abs, rel);
                continue;
            }
            if (!entry.isFile())
                continue;
            if ((0, secretFiles_1.isSecretFile)(rel)) {
                note(result.blockedSecrets, rel);
                continue;
            }
            if (matcher(rel, false)) {
                result.excludedCount++;
                note(result.excluded, rel);
                continue;
            }
            if (result.files.length >= maxFiles) {
                result.truncated = true;
                return;
            }
            const stat = await fs.stat(abs);
            result.files.push({ rel, abs, size: stat.size, mtimeMs: stat.mtimeMs });
            result.totalBytes += stat.size;
        }
    }
    await walk(root, '');
    return result;
}
//# sourceMappingURL=scanner.js.map