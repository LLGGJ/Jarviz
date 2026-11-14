"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.DB_RAM = exports.GAME_RAM = exports.APP_RAM = exports.SUBDOMAIN_RE = exports.OBJECT_NAME_RE = exports.DB_NAME_RE = exports.APP_NAME_RE = exports.DOCUMENTED_EGGS = exports.DB_ENGINES = exports.REGIONS = exports.RUNTIMES = void 0;
exports.checkAppName = checkAppName;
exports.checkAppRam = checkAppRam;
exports.checkSubdomain = checkSubdomain;
exports.checkStartCommand = checkStartCommand;
exports.checkGameName = checkGameName;
exports.checkGameRam = checkGameRam;
exports.checkDbName = checkDbName;
exports.checkDbRam = checkDbRam;
exports.checkObjectName = checkObjectName;
exports.checkObjectPrefix = checkObjectPrefix;
exports.checkExpireDays = checkExpireDays;
exports.checkCdnSize = checkCdnSize;
exports.checkConsoleCommand = checkConsoleCommand;
exports.checkRemotePath = checkRemotePath;
exports.assertValid = assertValid;
exports.safeId = safeId;
const constants_1 = require("../constants");
const errors_1 = require("./errors");
/** Regras extraídas de api-docs.html (v1.4). */
exports.RUNTIMES = ['nodejs', 'python', 'bun', 'go', 'java', 'ruby', 'static', 'php'];
exports.REGIONS = ['BR', 'CA'];
exports.DB_ENGINES = ['PostgreSQL', 'MySQL', 'mariadb', 'MongoDB', 'Redis'];
/** Eggs citados na documentação; a lista real vem de GET /instances/games/eggs. */
exports.DOCUMENTED_EGGS = ['minecraft_paper', 'minecraft_forge', 'samp', 'mta', 'csgo', 'rust'];
exports.APP_NAME_RE = /^[a-zA-Z0-9_-]{3,50}$/;
exports.DB_NAME_RE = /^[a-z0-9-]{3,}$/;
exports.OBJECT_NAME_RE = /^[a-zA-Z0-9_]{3,32}$/;
/** Rótulo DNS (necessário para *.venixcloud.com); a regra exata do provedor não é documentada. */
exports.SUBDOMAIN_RE = /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/;
const SAFE_ID_RE = /^[A-Za-z0-9._-]{1,128}$/;
const PREFIX_RE = /^[A-Za-z0-9_\-/.]{1,100}$/;
exports.APP_RAM = { min: 128, max: 32768 };
exports.GAME_RAM = { min: 256, max: 65536 };
exports.DB_RAM = { min: 512 };
function checkAppName(v) {
    return exports.APP_NAME_RE.test(v) ? undefined : 'Use 3 a 50 caracteres: letras, números, "_" ou "-".';
}
function checkAppRam(v) {
    return Number.isInteger(v) && v >= exports.APP_RAM.min && v <= exports.APP_RAM.max
        ? undefined
        : `A RAM deve ser um inteiro entre ${exports.APP_RAM.min} e ${exports.APP_RAM.max} MB.`;
}
function checkSubdomain(v) {
    return exports.SUBDOMAIN_RE.test(v) ? undefined : 'Use letras minúsculas, números e "-" (1 a 63 caracteres).';
}
function checkStartCommand(v) {
    // eslint-disable-next-line no-control-regex
    return v.length <= 500 && !/[\u0000-\u001f]/.test(v) ? undefined : 'Comando inválido (máx. 500 caracteres, uma linha).';
}
function checkGameName(v) {
    return v.trim().length >= 1 && v.length <= 60 ? undefined : 'O nome deve ter de 1 a 60 caracteres.';
}
function checkGameRam(v) {
    return Number.isInteger(v) && v >= exports.GAME_RAM.min && v <= exports.GAME_RAM.max
        ? undefined
        : `A RAM deve ser um inteiro entre ${exports.GAME_RAM.min} e ${exports.GAME_RAM.max} MB.`;
}
function checkDbName(v) {
    return exports.DB_NAME_RE.test(v) ? undefined : 'Mínimo 3 caracteres: letras minúsculas, números e hifens.';
}
function checkDbRam(v) {
    return Number.isInteger(v) && v >= exports.DB_RAM.min ? undefined : `A RAM deve ser um inteiro de, no mínimo, ${exports.DB_RAM.min} MB.`;
}
function checkObjectName(v) {
    return exports.OBJECT_NAME_RE.test(v) ? undefined : 'Use 3 a 32 caracteres: letras, números e "_".';
}
function checkObjectPrefix(v) {
    return PREFIX_RE.test(v) && !v.split('/').includes('..') ? undefined : 'Prefixo inválido (letras, números, "_", "-", "/", ".").';
}
function checkExpireDays(v) {
    return Number.isInteger(v) && v >= 1 && v <= 365 ? undefined : 'A expiração deve ser de 1 a 365 dias.';
}
function checkCdnSize(bytes) {
    if (bytes < constants_1.LIMITS.cdnMinBytes)
        return 'O arquivo precisa ter no mínimo 1 KB.';
    if (bytes > constants_1.LIMITS.cdnMaxBytes)
        return 'O arquivo excede o limite de 100 MB do CDN.';
    return undefined;
}
function checkConsoleCommand(v) {
    // eslint-disable-next-line no-control-regex
    return v.trim().length > 0 && v.length <= 1000 && !/[\u0000-\u001f]/.test(v) ? undefined : 'Comando vazio ou inválido (uma linha, máx. 1000 caracteres).';
}
function checkRemotePath(v) {
    // eslint-disable-next-line no-control-regex
    if (!v || v.length > 500 || /[\u0000-\u001f]/.test(v))
        return 'Caminho remoto inválido.';
    if (v.split(/[\\/]/).includes('..'))
        return 'Caminhos com ".." não são permitidos.';
    return undefined;
}
function assertValid(message) {
    if (message) {
        throw (0, errors_1.makeApiError)('invalid-input', { message });
    }
}
/** IDs vão direto para o caminho da URL: aceitar apenas caracteres seguros evita injeção de caminho. */
function safeId(id) {
    if (!SAFE_ID_RE.test(id) || id === '.' || id === '..') {
        throw (0, errors_1.makeApiError)('invalid-input', { message: 'Identificador de recurso inválido.' });
    }
    return encodeURIComponent(id);
}
//# sourceMappingURL=validators.js.map