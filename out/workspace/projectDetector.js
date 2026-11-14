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
exports.detectProject = detectProject;
exports.sanitizeAppName = sanitizeAppName;
exports.suggestObjectName = suggestObjectName;
const fs = __importStar(require("node:fs/promises"));
const path = __importStar(require("node:path"));
const SIGNATURES = [
    { file: 'bun.lockb', runtime: 'bun' },
    { file: 'bun.lock', runtime: 'bun' },
    { file: 'package.json', runtime: 'nodejs' },
    { file: 'requirements.txt', runtime: 'python' },
    { file: 'pyproject.toml', runtime: 'python' },
    { file: 'go.mod', runtime: 'go' },
    { file: 'pom.xml', runtime: 'java' },
    { file: 'build.gradle', runtime: 'java' },
    { file: 'composer.json', runtime: 'php' },
    { file: 'Gemfile', runtime: 'ruby' },
    { file: 'index.html', runtime: 'static' },
];
/** Heurística simples e transparente: a primeira assinatura encontrada define o runtime sugerido. */
async function detectProject(root) {
    let names = [];
    try {
        names = await fs.readdir(root);
    }
    catch {
        return { evidence: [] };
    }
    const present = new Set(names);
    const evidence = [];
    let runtime;
    for (const sig of SIGNATURES) {
        if (present.has(sig.file)) {
            evidence.push(sig.file);
            runtime ??= sig.runtime;
        }
    }
    const info = { runtime, evidence, suggestedName: sanitizeAppName(path.basename(root)) };
    if (present.has('package.json')) {
        try {
            const pkg = JSON.parse(await fs.readFile(path.join(root, 'package.json'), 'utf8'));
            if (typeof pkg.name === 'string')
                info.suggestedName = sanitizeAppName(pkg.name) ?? info.suggestedName;
            if (typeof pkg.scripts?.['start'] === 'string')
                info.startCommandHint = 'npm start';
        }
        catch {
            // package.json inválido: mantém a detecção básica
        }
    }
    return info;
}
/** Ajusta um nome candidato às regras documentadas (3–50, [a-zA-Z0-9_-]). */
function sanitizeAppName(raw) {
    const cleaned = raw.replace(/^@[^/]+\//, '').replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 50);
    return cleaned.length >= 3 ? cleaned : undefined;
}
/** Sugere um nome de objeto de CDN válido (3–32, [a-zA-Z0-9_]) a partir do nome do arquivo. */
function suggestObjectName(fileName) {
    const base = fileName.replace(/\.[^.]*$/, '');
    let name = base.replace(/[^a-zA-Z0-9_]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 32);
    if (name.length < 3)
        name = (name + '_file').slice(0, 32);
    return name;
}
//# sourceMappingURL=projectDetector.js.map