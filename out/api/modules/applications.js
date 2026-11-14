"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ApplicationsApi = void 0;
exports.parseApplications = parseApplications;
exports.mergeStatus = mergeStatus;
exports.formatStreamLine = formatStreamLine;
const blob_1 = require("../blob");
const normalize_1 = require("../normalize");
const validators_1 = require("../validators");
const errors_1 = require("../errors");
class ApplicationsApi {
    http;
    constructor(http) {
        this.http = http;
    }
    /** GET /apps/status — usado pela CLI oficial; opcional (falhas são toleradas pelo chamador). */
    async status(signal) {
        return this.http.json('GET', '/apps/status', { signal });
    }
    /** POST /apps/:id/action  {action}  [?replica=] */
    async action(id, action, replica) {
        await this.http.json('POST', `/apps/${(0, validators_1.safeId)(id)}/action`, { json: { action }, query: { replica } });
    }
    /** POST /apps/create (multipart/form-data) */
    async create(input, signal) {
        (0, validators_1.assertValid)((0, validators_1.checkAppName)(input.appName));
        if (!validators_1.RUNTIMES.includes(input.runtime))
            (0, validators_1.assertValid)('Runtime inválido.');
        (0, validators_1.assertValid)((0, validators_1.checkAppRam)(input.ram));
        if (input.isWeb) {
            (0, validators_1.assertValid)(input.subdomain ? (0, validators_1.checkSubdomain)(input.subdomain) : 'Informe um subdomínio para aplicações web.');
        }
        if (input.startCommand)
            (0, validators_1.assertValid)((0, validators_1.checkStartCommand)(input.startCommand));
        const form = new FormData();
        form.append('appName', input.appName);
        form.append('runtime', input.runtime);
        form.append('ram', String(input.ram));
        form.append('isWeb', input.isWeb ? 'true' : 'false');
        if (input.isWeb && input.subdomain)
            form.append('subdomain', input.subdomain);
        if (input.startCommand)
            form.append('startCommand', input.startCommand);
        if (input.file)
            form.append('file', (0, blob_1.toBlob)(input.file.data), input.file.filename);
        const res = await this.http.json('POST', '/apps/create', { form, signal, timeoutMs: 10 * 60_000 });
        return (0, normalize_1.isRecord)(res) ? res : {};
    }
    /** DELETE /apps/:id — exclusão permanente. */
    async remove(id) {
        await this.http.json('DELETE', `/apps/${(0, validators_1.safeId)(id)}`);
    }
    /** POST /apps/:id/deploy/trigger — deploy a partir do repositório GitHub vinculado. */
    async triggerDeploy(id) {
        await this.http.json('POST', `/apps/${(0, validators_1.safeId)(id)}/deploy/trigger`);
    }
    /** PATCH /apps/:id */
    async update(id, input) {
        if (input.max_ram === undefined && input.startCommand === undefined && input.runtime === undefined) {
            throw (0, errors_1.makeApiError)('invalid-input', { message: 'Nada para atualizar.' });
        }
        if (input.max_ram !== undefined)
            (0, validators_1.assertValid)((0, validators_1.checkAppRam)(input.max_ram));
        if (input.startCommand !== undefined)
            (0, validators_1.assertValid)((0, validators_1.checkStartCommand)(input.startCommand));
        if (input.runtime !== undefined && !validators_1.RUNTIMES.includes(input.runtime))
            (0, validators_1.assertValid)('Runtime inválido.');
        await this.http.json('PATCH', `/apps/${(0, validators_1.safeId)(id)}`, { json: input });
    }
    /** POST /apps/:id/files/upload (form: file, path) */
    async uploadFile(id, remotePath, file, signal) {
        (0, validators_1.assertValid)((0, validators_1.checkRemotePath)(remotePath));
        const form = new FormData();
        form.append('path', remotePath);
        form.append('file', (0, blob_1.toBlob)(file.data), file.filename);
        await this.http.json('POST', `/apps/${(0, validators_1.safeId)(id)}/files/upload`, { form, signal, timeoutMs: 10 * 60_000 });
    }
    /** POST /apps/:id/files/extract  {filePath} */
    async extract(id, filePath, signal) {
        (0, validators_1.assertValid)((0, validators_1.checkRemotePath)(filePath));
        await this.http.json('POST', `/apps/${(0, validators_1.safeId)(id)}/files/extract`, { json: { filePath }, signal, timeoutMs: 5 * 60_000 });
    }
    /** DELETE /apps/:id/files  {path} */
    async deleteFile(id, path) {
        (0, validators_1.assertValid)((0, validators_1.checkRemotePath)(path));
        await this.http.json('DELETE', `/apps/${(0, validators_1.safeId)(id)}/files`, { json: { path } });
    }
}
exports.ApplicationsApi = ApplicationsApi;
/** Une `GET /me` (lista `applications`) com o payload de `GET /apps/status`, quando existir. */
function parseApplications(me, statusPayload) {
    const apps = (0, normalize_1.extractRecords)(me['applications'] ?? []).map((rec) => ({ ...rec }));
    if (statusPayload !== undefined)
        mergeStatus(apps, statusPayload);
    const out = [];
    for (const rec of apps) {
        const id = (0, normalize_1.readId)(rec);
        if (!id)
            continue;
        const statusRaw = (0, normalize_1.readStatusRaw)(rec);
        out.push({
            id,
            name: (0, normalize_1.readName)(rec) ?? id,
            status: (0, normalize_1.mapStatus)(statusRaw),
            statusRaw,
            runtime: (0, normalize_1.pickString)(rec, ['runtime', 'language']),
            ramMb: (0, normalize_1.readRamMb)(rec, ['max_ram', 'ram', 'memory']),
            url: (0, normalize_1.resolveWebUrl)(rec),
            raw: rec,
        });
    }
    return out;
}
function collectStatus(value, byKey, depth) {
    if (Array.isArray(value)) {
        for (const item of value) {
            if (!(0, normalize_1.isRecord)(item))
                continue;
            const id = (0, normalize_1.readId)(item);
            if (id)
                byKey.set(id, item);
            const name = (0, normalize_1.readName)(item);
            if (name)
                byKey.set(name, item);
        }
        return;
    }
    if (!(0, normalize_1.isRecord)(value))
        return;
    for (const [key, item] of Object.entries(value)) {
        if (typeof item === 'string')
            byKey.set(key, { status: item });
        else if (typeof item === 'boolean')
            byKey.set(key, { running: item });
        else if ((0, normalize_1.isRecord)(item)) {
            if ((0, normalize_1.readStatusRaw)(item) !== undefined)
                byKey.set(key, item);
            else if (depth < 2)
                collectStatus(item, byKey, depth + 1);
        }
        else if (Array.isArray(item) && depth < 2)
            collectStatus(item, byKey, depth + 1);
    }
}
function mergeStatus(apps, statusPayload) {
    const byKey = new Map();
    collectStatus(statusPayload, byKey, 0);
    for (const app of apps) {
        const id = (0, normalize_1.readId)(app);
        const name = (0, normalize_1.readName)(app);
        const extra = (id ? byKey.get(id) : undefined) ?? (name ? byKey.get(name) : undefined);
        if (!extra)
            continue;
        for (const [k, v] of Object.entries(extra)) {
            if (v !== null && v !== undefined)
                app[k] = v;
        }
    }
}
/** Converte uma linha SSE de `/instances/stream/:id` em texto legível (log, status ou métrica). */
function formatStreamLine(raw) {
    const trimmed = raw.trim();
    if (!trimmed)
        return undefined;
    let parsed;
    try {
        parsed = JSON.parse(trimmed);
    }
    catch {
        return { kind: 'log', text: raw };
    }
    if (typeof parsed === 'string')
        return { kind: 'status', text: `[status] ${parsed}` };
    if (!(0, normalize_1.isRecord)(parsed))
        return { kind: 'log', text: raw };
    if ('cpu' in parsed) {
        const parts = [];
        if (typeof parsed['cpu'] === 'number')
            parts.push(`CPU ${parsed['cpu'].toFixed(1)}%`);
        const mem = parsed['memory'];
        if ((0, normalize_1.isRecord)(mem) && typeof mem['usage'] === 'number') {
            const used = Math.round(mem['usage'] / (1 << 20));
            const limit = typeof mem['limit'] === 'number' && mem['limit'] > 0 ? `/${Math.round(mem['limit'] / (1 << 20))}` : '';
            parts.push(`RAM ${used}${limit} MB`);
        }
        return { kind: 'metric', text: `[métricas] ${parts.join(' • ')}` };
    }
    const text = (0, normalize_1.pickString)(parsed, ['log', 'message', 'msg', 'line', 'text', 'data']);
    if (text === undefined)
        return { kind: 'log', text: raw };
    const time = (0, normalize_1.pickString)(parsed, ['time', 'timestamp', 'ts', '@timestamp']);
    return { kind: 'log', text: `${time ? `${time} ` : ''}${text.replace(/[\r\n]+$/, '')}` };
}
//# sourceMappingURL=applications.js.map