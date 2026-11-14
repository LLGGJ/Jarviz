"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.OVERVIEW_ACTIONS = void 0;
exports.parseOverviewMessage = parseOverviewMessage;
exports.parseDetailsMessage = parseDetailsMessage;
exports.OVERVIEW_ACTIONS = ['refresh', 'openWeb', 'checkConnection', 'signOut'];
function parseOverviewMessage(msg) {
    if (typeof msg !== 'object' || msg === null)
        return undefined;
    const m = msg;
    if (m['type'] === 'ready')
        return { type: 'ready' };
    if (m['type'] === 'action' && typeof m['action'] === 'string' && exports.OVERVIEW_ACTIONS.includes(m['action'])) {
        return { type: 'action', action: m['action'] };
    }
    return undefined;
}
function parseDetailsMessage(msg, validKeys) {
    if (typeof msg !== 'object' || msg === null)
        return undefined;
    const m = msg;
    if (m['type'] === 'ready')
        return { type: 'ready' };
    const key = m['key'];
    if (typeof key !== 'string' || key.length > 300 || !validKeys.has(key))
        return undefined;
    if (m['type'] === 'reveal' || m['type'] === 'hide' || m['type'] === 'copy' || m['type'] === 'open') {
        return { type: m['type'], key };
    }
    return undefined;
}
//# sourceMappingURL=messages.js.map