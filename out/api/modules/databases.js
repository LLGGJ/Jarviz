"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.DatabasesApi = void 0;
exports.parseDatabases = parseDatabases;
const normalize_1 = require("../normalize");
const validators_1 = require("../validators");
class DatabasesApi {
    http;
    constructor(http) {
        this.http = http;
    }
    /** GET /instances/databases */
    async list(signal) {
        const payload = await this.http.json('GET', '/instances/databases', { signal });
        return parseDatabases(payload);
    }
    /** POST /instances/databases/create */
    async create(input) {
        (0, validators_1.assertValid)((0, validators_1.checkDbName)(input.name));
        if (!validators_1.DB_ENGINES.includes(input.engine))
            (0, validators_1.assertValid)('Motor de banco inválido.');
        (0, validators_1.assertValid)((0, validators_1.checkDbRam)(input.ram));
        if (!validators_1.REGIONS.includes(input.region))
            (0, validators_1.assertValid)('Região inválida (use BR ou CA).');
        const body = {
            name: input.name,
            engine: input.engine,
            ram: input.ram,
            region: input.region,
            installManager: input.installManager,
        };
        if (input.workspaceId)
            body['workspaceId'] = input.workspaceId;
        const res = await this.http.json('POST', '/instances/databases/create', { json: body });
        return (0, normalize_1.isRecord)(res) ? res : {};
    }
    /** GET /instances/databases/:id/credentials/zip — ZIP binário com ca.pem, client-cert.pem e client-key.pem. */
    async downloadCertificates(id, signal) {
        return this.http.buffer('GET', `/instances/databases/${(0, validators_1.safeId)(id)}/credentials/zip`, { signal, timeoutMs: 60_000 });
    }
    /** DELETE /instances/databases/:id — exclusão permanente. */
    async remove(id) {
        await this.http.json('DELETE', `/instances/databases/${(0, validators_1.safeId)(id)}`);
    }
}
exports.DatabasesApi = DatabasesApi;
function parseDatabases(payload) {
    const out = [];
    for (const rec of (0, normalize_1.extractRecords)(payload, ['databases'])) {
        const id = (0, normalize_1.readId)(rec);
        if (!id)
            continue;
        const statusRaw = (0, normalize_1.readStatusRaw)(rec);
        out.push({
            id,
            name: (0, normalize_1.readName)(rec) ?? id,
            engine: (0, normalize_1.pickString)(rec, ['engine', 'type', 'db_type', 'dbType']),
            region: (0, normalize_1.pickString)(rec, ['region']),
            status: (0, normalize_1.mapStatus)(statusRaw),
            statusRaw,
            ramMb: (0, normalize_1.readRamMb)(rec, ['ram', 'max_ram', 'memory']),
            host: (0, normalize_1.pickString)(rec, ['host', 'hostname', 'address']),
            raw: rec,
        });
    }
    return out;
}
//# sourceMappingURL=databases.js.map