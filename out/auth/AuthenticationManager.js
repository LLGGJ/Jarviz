"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.AuthenticationManager = void 0;
exports.validateApiToken = validateApiToken;
const constants_1 = require("../constants");
const errors_1 = require("../api/errors");
const events_1 = require("../utils/events");
class AuthenticationManager {
    secrets;
    state;
    oauth;
    resolveAccount;
    now;
    session;
    account;
    changes = new events_1.Emitter();
    signingIn = false;
    onDidChange = this.changes.event;
    constructor(secrets, state, oauth, resolveAccount, now = Date.now) {
        this.secrets = secrets;
        this.state = state;
        this.oauth = oauth;
        this.resolveAccount = resolveAccount;
        this.now = now;
    }
    /** Restaura a sessão do SecretStorage (descartando dados corrompidos ou expirados). */
    async initialize() {
        const raw = await this.secrets.get(constants_1.SECRET_SESSION_KEY);
        if (!raw)
            return;
        try {
            const parsed = JSON.parse(raw);
            if (typeof parsed.accessToken !== 'string' ||
                !parsed.accessToken ||
                (parsed.method !== 'oauth' && parsed.method !== 'apiToken')) {
                throw new Error('formato inválido');
            }
            this.session = {
                method: parsed.method,
                accessToken: parsed.accessToken,
                expiresAt: typeof parsed.expiresAt === 'number' ? parsed.expiresAt : undefined,
                createdAt: typeof parsed.createdAt === 'number' ? parsed.createdAt : this.now(),
            };
            this.account = this.state.get(constants_1.STATE_ACCOUNT_KEY);
        }
        catch {
            await this.clearStorage();
            return;
        }
        if (this.isExpired(this.session)) {
            await this.expire();
        }
    }
    isSignedIn() {
        return this.session !== undefined && !this.isExpired(this.session);
    }
    getSessionInfo() {
        if (!this.session || this.isExpired(this.session))
            return undefined;
        return {
            method: this.session.method,
            account: this.account ?? { label: 'Conta Venix Cloud' },
            expiresAt: this.session.expiresAt,
        };
    }
    /** Token em memória para o cliente HTTP; expira a sessão se já venceu. */
    async getAccessToken() {
        if (!this.session)
            return undefined;
        if (this.isExpired(this.session)) {
            await this.expire();
            return undefined;
        }
        return this.session.accessToken;
    }
    /** Segredos conhecidos, para o logger removê-los de qualquer mensagem. */
    knownSecrets() {
        return this.session ? [this.session.accessToken] : [];
    }
    async signInWithOAuth(options) {
        return this.guard(async () => {
            const tokens = await this.oauth.signIn(options);
            return this.commit('oauth', tokens);
        });
    }
    /** Alternativa documentada (Bearer com token gerado no painel). NÃO é OAuth. */
    async signInWithApiToken(token) {
        const clean = token.trim();
        const problem = validateApiToken(clean);
        if (problem)
            throw new Error(problem);
        return this.guard(() => this.commit('apiToken', { accessToken: clean }));
    }
    /** Encerra a sessão e apaga TODOS os dados locais relacionados. */
    async signOut() {
        const had = this.session !== undefined;
        await this.clearStorage();
        if (had)
            this.changes.fire({ reason: 'signed-out' });
    }
    /** A API respondeu 401: o token foi revogado/expirou. */
    async handleUnauthorized() {
        if (!this.session)
            return;
        await this.expire();
    }
    dispose() {
        this.changes.dispose();
    }
    // ---------------------------------------------------------------- internos
    async guard(fn) {
        if (this.signingIn)
            throw new Error('Já existe um login em andamento.');
        this.signingIn = true;
        try {
            return await fn();
        }
        finally {
            this.signingIn = false;
        }
    }
    async commit(method, tokens) {
        // Valida o resultado antes de persistir: um token que a API recusa nunca é salvo.
        let account = { label: 'Conta Venix Cloud' };
        try {
            account = await this.resolveAccount(tokens.accessToken);
        }
        catch (err) {
            if (err instanceof errors_1.VenixApiError && (err.kind === 'unauthorized' || err.kind === 'forbidden')) {
                throw new Error(method === 'apiToken'
                    ? 'Token de API recusado pela Venix Cloud. Verifique se ele está correto e ativo.'
                    : 'A Venix Cloud recusou o token recebido. Tente entrar novamente.');
            }
            // Falhas transitórias (rede/5xx) não invalidam o login; o rótulo é atualizado depois.
        }
        const session = {
            method,
            accessToken: tokens.accessToken,
            expiresAt: tokens.expiresAt,
            createdAt: this.now(),
        };
        await this.secrets.store(constants_1.SECRET_SESSION_KEY, JSON.stringify(session));
        await this.state.update(constants_1.STATE_ACCOUNT_KEY, account);
        this.session = session;
        this.account = account;
        this.changes.fire({ reason: 'signed-in' });
        return { method, account, expiresAt: session.expiresAt };
    }
    /** Atualiza o rótulo da conta (ex.: após `GET /me`). */
    async updateAccount(account) {
        if (!this.session)
            return;
        this.account = account;
        await this.state.update(constants_1.STATE_ACCOUNT_KEY, account);
        this.changes.fire({ reason: 'account-updated' });
    }
    isExpired(session) {
        return session.expiresAt !== undefined && session.expiresAt <= this.now();
    }
    async expire() {
        await this.clearStorage();
        this.changes.fire({ reason: 'expired' });
    }
    async clearStorage() {
        this.session = undefined;
        this.account = undefined;
        await this.secrets.delete(constants_1.SECRET_SESSION_KEY);
        await this.state.update(constants_1.STATE_ACCOUNT_KEY, undefined);
    }
}
exports.AuthenticationManager = AuthenticationManager;
function validateApiToken(token) {
    if (token.length < 8)
        return 'O token parece curto demais.';
    if (token.length > 4096)
        return 'O token é longo demais.';
    // eslint-disable-next-line no-control-regex
    if (/[\s\u0000-\u001f]/.test(token))
        return 'O token não pode conter espaços ou quebras de linha.';
    return undefined;
}
//# sourceMappingURL=AuthenticationManager.js.map