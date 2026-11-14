"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.buildDetailEntries = buildDetailEntries;
exports.toPublicFields = toPublicFields;
const normalize_1 = require("../api/normalize");
const redact_1 = require("../security/redact");
function stringify(value) {
    if (value === null || value === undefined)
        return '';
    if (typeof value === 'string')
        return value;
    if (typeof value === 'number' || typeof value === 'boolean')
        return String(value);
    try {
        return JSON.stringify(value);
    }
    catch {
        return String(value);
    }
}
/**
 * Converte o registro devolvido pela API em campos de exibição. Nada é inventado:
 * só aparece o que a API enviou. Campos sensíveis ficam mascarados por padrão.
 */
function buildDetailEntries(raw, preferredOrder = []) {
    const flat = [];
    for (const [key, value] of Object.entries(raw)) {
        if ((0, normalize_1.isRecord)(value) && !(0, redact_1.isSensitiveKey)(key)) {
            for (const [inner, innerValue] of Object.entries(value))
                flat.push([`${key}.${inner}`, innerValue]);
        }
        else {
            flat.push([key, value]);
        }
    }
    const rank = (key) => {
        const i = preferredOrder.indexOf(key);
        return i === -1 ? preferredOrder.length : i;
    };
    flat.sort((a, b) => rank(a[0]) - rank(b[0]));
    const out = [];
    for (const [key, value] of flat) {
        const text = stringify(value);
        if (text === '')
            continue;
        const sensitive = (0, redact_1.isSensitiveKey)(key);
        out.push({ key, label: key, value: text, display: sensitive ? redact_1.MASK : text, sensitive });
    }
    return out;
}
function toPublicFields(entries) {
    return entries.map(({ key, label, display, sensitive }) => ({ key, label, display, sensitive }));
}
//# sourceMappingURL=details.js.map