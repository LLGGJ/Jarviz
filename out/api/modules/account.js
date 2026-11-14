"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.AccountApi = void 0;
exports.parseAccount = parseAccount;
const normalize_1 = require("../normalize");
/**
 * GET /me — NÃO consta em api-docs.html v1.4; é o endpoint usado pela CLI oficial
 * (`venix whoami` / `venix apps`) e devolve a conta e a lista `applications`.
 */
class AccountApi {
    http;
    constructor(http) {
        this.http = http;
    }
    async getMe(signal) {
        const me = await this.http.json('GET', '/me', { signal });
        return (0, normalize_1.isRecord)(me) ? me : {};
    }
}
exports.AccountApi = AccountApi;
function parseAccount(me) {
    const user = (0, normalize_1.isRecord)(me['user']) ? me['user'] : me;
    const name = (0, normalize_1.pickString)(user, ['name', 'username', 'login']);
    const email = (0, normalize_1.pickString)(user, ['email']);
    return {
        id: (0, normalize_1.pickString)(user, ['id', 'uuid', '_id']),
        label: name ?? email ?? 'Conta Venix Cloud',
        email,
        plan: (0, normalize_1.pickString)(user, ['plan', 'planName', 'plan_name']),
    };
}
//# sourceMappingURL=account.js.map