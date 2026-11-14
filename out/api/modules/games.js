"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.GamesApi = void 0;
exports.parseGameServers = parseGameServers;
exports.parseEggs = parseEggs;
exports.documentedEggs = documentedEggs;
const normalize_1 = require("../normalize");
const validators_1 = require("../validators");
class GamesApi {
    http;
    constructor(http) {
        this.http = http;
    }
    /** GET /instances/games */
    async list(signal) {
        const payload = await this.http.json('GET', '/instances/games', { signal });
        return parseGameServers(payload);
    }
    /** GET /instances/games/eggs */
    async eggs(signal) {
        const payload = await this.http.json('GET', '/instances/games/eggs', { signal });
        return parseEggs(payload);
    }
    /** POST /instances/games/create */
    async create(input) {
        (0, validators_1.assertValid)((0, validators_1.checkGameName)(input.name));
        if (!input.game || /\s/.test(input.game) || input.game.length > 64)
            (0, validators_1.assertValid)('ID do egg inválido.');
        (0, validators_1.assertValid)((0, validators_1.checkGameRam)(input.ram));
        if (!validators_1.REGIONS.includes(input.region))
            (0, validators_1.assertValid)('Região inválida (use BR ou CA).');
        const res = await this.http.json('POST', '/instances/games/create', { json: input });
        return (0, normalize_1.isRecord)(res) ? res : {};
    }
    /** POST /instances/games/:id/command  {cmd} */
    async sendCommand(id, cmd) {
        (0, validators_1.assertValid)((0, validators_1.checkConsoleCommand)(cmd));
        await this.http.json('POST', `/instances/games/${(0, validators_1.safeId)(id)}/command`, { json: { cmd } });
    }
    /** DELETE /instances/games/:id */
    async remove(id) {
        await this.http.json('DELETE', `/instances/games/${(0, validators_1.safeId)(id)}`);
    }
}
exports.GamesApi = GamesApi;
function parseGameServers(payload) {
    const out = [];
    for (const rec of (0, normalize_1.extractRecords)(payload, ['games', 'servers'])) {
        const id = (0, normalize_1.readId)(rec);
        if (!id)
            continue;
        const statusRaw = (0, normalize_1.readStatusRaw)(rec);
        const ip = (0, normalize_1.pickString)(rec, ['ip', 'host', 'address', 'public_ip', 'publicIp']);
        const port = (0, normalize_1.pickString)(rec, ['port', 'public_port', 'publicPort']);
        out.push({
            id,
            name: (0, normalize_1.readName)(rec) ?? id,
            game: (0, normalize_1.pickString)(rec, ['game', 'egg', 'type']),
            region: (0, normalize_1.pickString)(rec, ['region']),
            status: (0, normalize_1.mapStatus)(statusRaw),
            statusRaw,
            ramMb: (0, normalize_1.readRamMb)(rec, ['ram', 'max_ram', 'memory']),
            address: ip ? (port ? `${ip}:${port}` : ip) : undefined,
            raw: rec,
        });
    }
    return out;
}
/** Aceita lista de strings ou de objetos; sem resposta utilizável, devolve vazio (o chamador usa os eggs documentados). */
function parseEggs(payload) {
    const out = [];
    for (const item of (0, normalize_1.extractList)(payload, ['eggs'])) {
        if (typeof item === 'string' && item)
            out.push({ id: item, label: item });
        else if ((0, normalize_1.isRecord)(item)) {
            const id = (0, normalize_1.pickString)(item, ['id', 'egg', 'slug', 'key', 'name']);
            if (id)
                out.push({ id, label: (0, normalize_1.pickString)(item, ['name', 'title', 'label', 'id']) ?? id });
        }
    }
    if (out.length === 0 && (0, normalize_1.isRecord)(payload)) {
        // Formato {minecraft_paper: {...}, rust: {...}}
        const inner = (0, normalize_1.isRecord)(payload['response']) ? payload['response'] : payload;
        for (const [key, value] of Object.entries(inner)) {
            if ((0, normalize_1.isRecord)(value) && /^[a-z0-9_]+$/.test(key)) {
                out.push({ id: key, label: (0, normalize_1.pickString)(value, ['name', 'title', 'label']) ?? key });
            }
        }
    }
    return out;
}
function documentedEggs() {
    return validators_1.DOCUMENTED_EGGS.map((id) => ({ id, label: id }));
}
//# sourceMappingURL=games.js.map