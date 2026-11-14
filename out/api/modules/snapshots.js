"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SnapshotsApi = void 0;
exports.parseSnapshots = parseSnapshots;
const normalize_1 = require("../normalize");
const errors_1 = require("../errors");
const validators_1 = require("../validators");
class SnapshotsApi {
    http;
    constructor(http) {
        this.http = http;
    }
    /** GET /snapshots?page=&limit= */
    async list(page, limit, signal) {
        const payload = await this.http.json('GET', '/snapshots', { signal, query: { page, limit } });
        return parseSnapshots(payload);
    }
    /** POST /snapshots  {resourceId, name} */
    async create(resourceId, name) {
        (0, validators_1.safeId)(resourceId);
        (0, validators_1.assertValid)(name.trim() && name.length <= 100 ? undefined : 'Informe um nome de até 100 caracteres.');
        await this.http.json('POST', '/snapshots', { json: { resourceId, name } });
    }
    /** GET /snapshots/:id/download — a API devolve uma URL pré-assinada. */
    async downloadUrl(id) {
        const res = await this.http.json('GET', `/snapshots/${(0, validators_1.safeId)(id)}/download`);
        const url = typeof res === 'string' ? res : (0, normalize_1.isRecord)(res) ? (0, normalize_1.pickString)(res, ['url', 'downloadUrl', 'download_url', 'signedUrl', 'signed_url']) : undefined;
        if (!url)
            throw (0, errors_1.makeApiError)('invalid-response');
        return url;
    }
    /** DELETE /snapshots/:id */
    async remove(id) {
        await this.http.json('DELETE', `/snapshots/${(0, validators_1.safeId)(id)}`);
    }
}
exports.SnapshotsApi = SnapshotsApi;
function parseSnapshots(payload) {
    const out = [];
    for (const rec of (0, normalize_1.extractRecords)(payload, ['snapshots'])) {
        const id = (0, normalize_1.readId)(rec);
        if (!id)
            continue;
        out.push({
            id,
            name: (0, normalize_1.readName)(rec) ?? id,
            resourceId: (0, normalize_1.pickString)(rec, ['resourceId', 'resource_id', 'instanceId']),
            status: (0, normalize_1.pickString)(rec, ['status', 'state']),
            size: (0, normalize_1.pickNumber)(rec, ['size', 'bytes']),
            createdAt: (0, normalize_1.pickString)(rec, ['createdAt', 'created_at', 'created']),
            raw: rec,
        });
    }
    return out;
}
//# sourceMappingURL=snapshots.js.map