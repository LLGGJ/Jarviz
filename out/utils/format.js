"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.formatBytes = formatBytes;
exports.formatMb = formatMb;
exports.formatTime = formatTime;
exports.pluralize = pluralize;
exports.timestampName = timestampName;
function formatBytes(bytes) {
    if (!Number.isFinite(bytes) || bytes < 0)
        return '-';
    if (bytes >= 1 << 30)
        return `${(bytes / (1 << 30)).toFixed(1)} GB`;
    if (bytes >= 1 << 20)
        return `${(bytes / (1 << 20)).toFixed(1)} MB`;
    if (bytes >= 1 << 10)
        return `${(bytes / (1 << 10)).toFixed(1)} KB`;
    return `${bytes} B`;
}
function formatMb(mb) {
    if (mb === undefined)
        return undefined;
    return mb >= 1024 && mb % 1024 === 0 ? `${mb / 1024} GB` : `${mb} MB`;
}
function formatTime(ms, locale = 'pt-BR') {
    if (ms === undefined)
        return '-';
    return new Date(ms).toLocaleString(locale, { dateStyle: 'short', timeStyle: 'medium' });
}
function pluralize(n, one, many) {
    return `${n} ${n === 1 ? one : many}`;
}
function timestampName(prefix = 'backup', date = new Date()) {
    const p = (n) => String(n).padStart(2, '0');
    return `${prefix}-${date.getFullYear()}${p(date.getMonth() + 1)}${p(date.getDate())}-${p(date.getHours())}${p(date.getMinutes())}${p(date.getSeconds())}`;
}
//# sourceMappingURL=format.js.map