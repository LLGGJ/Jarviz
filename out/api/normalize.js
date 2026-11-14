"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.BOOL_STATUS_KEYS = exports.STATUS_KEYS = exports.NAME_KEYS = exports.ID_KEYS = void 0;
exports.isRecord = isRecord;
exports.pickString = pickString;
exports.pickNumber = pickNumber;
exports.extractList = extractList;
exports.extractRecords = extractRecords;
exports.readStatusRaw = readStatusRaw;
exports.mapStatus = mapStatus;
exports.readId = readId;
exports.readName = readName;
exports.readRamMb = readRamMb;
exports.resolveWebUrl = resolveWebUrl;
/**
 * A documentação v1.4 não descreve o schema das respostas de listagem.
 * Estes helpers fazem leitura TOLERANTE (várias chaves candidatas, espelhando a
 * CLI oficial) e nunca inventam valores: o que não vier da API fica `undefined`.
 */
function isRecord(v) {
    return typeof v === 'object' && v !== null && !Array.isArray(v);
}
function pickString(rec, keys) {
    for (const key of keys) {
        const v = rec[key];
        if (typeof v === 'string' && v.trim() !== '')
            return v;
        if (typeof v === 'number' && Number.isFinite(v))
            return String(v);
    }
    return undefined;
}
function pickNumber(rec, keys) {
    for (const key of keys) {
        const v = rec[key];
        if (typeof v === 'number' && Number.isFinite(v))
            return v;
        if (typeof v === 'string') {
            const n = Number(v.trim().replace(/%$/, ''));
            if (v.trim() !== '' && Number.isFinite(n))
                return n;
        }
    }
    return undefined;
}
const LIST_KEYS = ['response', 'data', 'items', 'results', 'games', 'servers', 'instances', 'databases', 'objects', 'files', 'snapshots', 'eggs', 'applications'];
/** Extrai uma lista de registros de payloads como `[...]`, `{response:[...]}` ou `{games:[...]}`. */
function extractList(payload, extraKeys = []) {
    if (Array.isArray(payload))
        return payload;
    if (!isRecord(payload))
        return [];
    for (const key of [...extraKeys, ...LIST_KEYS]) {
        const v = payload[key];
        if (Array.isArray(v))
            return v;
        if (isRecord(v)) {
            const inner = extractList(v, extraKeys);
            if (inner.length > 0)
                return inner;
        }
    }
    return [];
}
function extractRecords(payload, extraKeys = []) {
    return extractList(payload, extraKeys).filter(isRecord);
}
exports.ID_KEYS = ['id', 'uuid', 'appId', 'app_id', 'instanceId', 'instance_id', '_id'];
exports.NAME_KEYS = ['name', 'appName', 'displayName', 'display_name', 'title', 'identifier'];
exports.STATUS_KEYS = ['status', 'state', 'app_status', 'appStatus', 'container_status', 'instance_status', 'instanceStatus'];
exports.BOOL_STATUS_KEYS = ['running', 'is_running', 'isRunning', 'online', 'is_online', 'isOnline', 'active'];
/** Lê o status bruto: texto, booleano ou objeto aninhado (como a CLI oficial). */
function readStatusRaw(rec) {
    for (const key of exports.STATUS_KEYS) {
        const v = rec[key];
        if (typeof v === 'string' && v !== '')
            return v;
        if (typeof v === 'boolean')
            return v ? 'online' : 'offline';
        if (isRecord(v)) {
            const inner = pickString(v, ['status', 'state', 'name']);
            if (inner)
                return inner;
        }
    }
    for (const key of exports.BOOL_STATUS_KEYS) {
        const v = rec[key];
        if (typeof v === 'boolean')
            return v ? 'online' : 'offline';
    }
    return undefined;
}
function mapStatus(raw) {
    switch ((raw ?? '').trim().toUpperCase()) {
        case 'ONLINE':
        case 'RUNNING':
        case 'ACTIVE':
        case 'STARTED':
        case 'UP':
        case 'HEALTHY':
        case 'READY':
            return 'online';
        case 'OFFLINE':
        case 'STOPPED':
        case 'DOWN':
        case 'EXITED':
        case 'PAUSED':
        case 'SUSPENDED':
            return 'offline';
        case 'STARTING':
        case 'DEPLOYING':
        case 'BUILDING':
        case 'RESTARTING':
        case 'CREATING':
        case 'PROVISIONING':
            return 'starting';
        case 'STOPPING':
            return 'stopping';
        case 'ERROR':
        case 'FAILED':
        case 'CRASHED':
            return 'error';
        default:
            return 'unknown';
    }
}
function readId(rec) {
    return pickString(rec, exports.ID_KEYS);
}
function readName(rec) {
    return pickString(rec, exports.NAME_KEYS);
}
/** Devolve a RAM em MB quando o valor parece estar em MB (valores enormes são tratados como bytes). */
function readRamMb(rec, keys) {
    const v = pickNumber(rec, keys);
    if (v === undefined || v < 0)
        return undefined;
    return v > 1 << 20 ? Math.round(v / (1 << 20)) : v;
}
/** Monta um endereço web a partir dos campos que a API informar (espelha `webURL` da CLI). */
function resolveWebUrl(rec) {
    let raw = pickString(rec, ['url', 'appUrl', 'app_url', 'public_url', 'publicUrl', 'web_url', 'webUrl', 'dashboard_url', 'domain', 'custom_domain', 'customDomain']);
    if (!raw)
        return undefined;
    if (!raw.includes('://'))
        raw = `https://${raw}`;
    let url;
    try {
        url = new URL(raw);
    }
    catch {
        return undefined;
    }
    if (!url.hostname.includes('.') || (url.protocol !== 'https:' && url.protocol !== 'http:'))
        return undefined;
    const route = pickString(rec, ['route', 'base_path', 'basePath', 'web_path']);
    if (route && (url.pathname === '' || url.pathname === '/')) {
        url.pathname = `/${route.replace(/^\/+/, '')}`;
    }
    return url.toString();
}
//# sourceMappingURL=normalize.js.map