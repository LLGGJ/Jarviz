"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.StorageApi = void 0;
exports.parseObjects = parseObjects;
const blob_1 = require("../blob");
const normalize_1 = require("../normalize");
const errors_1 = require("../errors");
const validators_1 = require("../validators");
class StorageApi {
    http;
    constructor(http) {
        this.http = http;
    }
    /** POST /objects?name=&prefix=&expire=  (multipart: file) — devolve {url, size}. */
    async upload(input, signal) {
        (0, validators_1.assertValid)((0, validators_1.checkObjectName)(input.name));
        if (input.prefix)
            (0, validators_1.assertValid)((0, validators_1.checkObjectPrefix)(input.prefix));
        if (input.expireDays !== undefined)
            (0, validators_1.assertValid)((0, validators_1.checkExpireDays)(input.expireDays));
        (0, validators_1.assertValid)((0, validators_1.checkCdnSize)(input.file.data.byteLength));
        const form = new FormData();
        form.append('file', (0, blob_1.toBlob)(input.file.data), input.file.filename);
        const res = await this.http.json('POST', '/objects', {
            form,
            signal,
            timeoutMs: 10 * 60_000,
            query: { name: input.name, prefix: input.prefix || undefined, expire: input.expireDays },
        });
        const url = (0, normalize_1.isRecord)(res) ? (0, normalize_1.pickString)(res, ['url']) : undefined;
        if (!url)
            throw (0, errors_1.makeApiError)('invalid-response');
        return { url, size: (0, normalize_1.isRecord)(res) ? (0, normalize_1.pickNumber)(res, ['size']) : undefined };
    }
    /** GET /objects */
    async list(signal) {
        const payload = await this.http.json('GET', '/objects', { signal });
        return parseObjects(payload);
    }
}
exports.StorageApi = StorageApi;
function parseObjects(payload) {
    const out = [];
    for (const rec of (0, normalize_1.extractRecords)(payload, ['objects', 'files'])) {
        const url = (0, normalize_1.pickString)(rec, ['url', 'cdn_url', 'cdnUrl', 'public_url']);
        const name = (0, normalize_1.pickString)(rec, ['name', 'key', 'path', 'filename']) ?? (url ? url.split('/').pop() : undefined);
        if (!name)
            continue;
        out.push({ id: (0, normalize_1.pickString)(rec, ['id', 'key']) ?? name, name, url, size: (0, normalize_1.pickNumber)(rec, ['size', 'bytes']), raw: rec });
    }
    return out;
}
//# sourceMappingURL=storage.js.map