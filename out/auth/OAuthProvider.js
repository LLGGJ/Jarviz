"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.VenixOAuthProvider = exports.OAuthError = void 0;
const redact_1 = require("../security/redact");
const trustedHosts_1 = require("../security/trustedHosts");
const pkce_1 = require("./pkce");
class OAuthError extends Error {
    kind;
    constructor(kind, message) {
        super(message);
        this.kind = kind;
        this.name = 'OAuthError';
    }
}
exports.OAuthError = OAuthError;
class VenixOAuthProvider {
    config;
    fetchImpl;
    now;
    constructor(config) {
        this.config = config;
        // Mesma política de domínios confiáveis usada pelo cliente HTTP.
        (0, trustedHosts_1.assertTrustedUrl)(config.authorizeUrl.split('?')[0]);
        (0, trustedHosts_1.assertTrustedUrl)(config.tokenUrl);
        this.fetchImpl = config.fetch ?? globalThis.fetch.bind(globalThis);
        this.now = config.now ?? Date.now;
    }
    buildAuthorizeUrl(redirectUri, challenge, state) {
        const url = new URL(this.config.authorizeUrl);
        url.searchParams.set('client_id', this.config.clientId);
        url.searchParams.set('redirect_uri', redirectUri);
        url.searchParams.set('response_type', 'code');
        url.searchParams.set('code_challenge', challenge);
        url.searchParams.set('code_challenge_method', 'S256');
        url.searchParams.set('state', state);
        return url.toString();
    }
    /** Fluxo Authorization Code + PKCE (S256) para aplicativo instalado (cliente público). */
    async signIn(options) {
        const { receiver, signal } = options;
        const verifier = (0, pkce_1.generateCodeVerifier)();
        const state = (0, pkce_1.generateState)();
        const authUrl = this.buildAuthorizeUrl(receiver.redirectUri, (0, pkce_1.codeChallengeS256)(verifier), state);
        let settled = false;
        let resolveCode;
        let rejectCode;
        const codePromise = new Promise((resolve, reject) => {
            resolveCode = resolve;
            rejectCode = reject;
        });
        codePromise.catch(() => undefined); // evita "unhandled rejection" se a limpeza vier antes
        const handler = (params) => {
            if (settled) {
                return { ok: false, title: 'Link já utilizado', message: 'Este link de autorização já foi usado.' };
            }
            const returnedState = params['state'];
            const stateMatches = returnedState !== undefined && (0, pkce_1.safeEqual)(returnedState, state);
            if (returnedState !== undefined && !stateMatches) {
                // Possível CSRF/requisição forjada: rejeita SEM encerrar o login legítimo em andamento.
                return { ok: false, title: 'Estado inválido', message: 'A resposta não corresponde a este login. Tente novamente.' };
            }
            if (params['error']) {
                settled = true;
                const detail = (0, redact_1.sanitizeMessage)(params['error_description'] || params['error'] || 'acesso negado', 200);
                rejectCode(new OAuthError('denied', `Autorização recusada: ${detail}`));
                return { ok: false, title: 'Login recusado', message: detail };
            }
            if (!stateMatches) {
                return { ok: false, title: 'Estado ausente', message: 'A resposta não trouxe o parâmetro de segurança "state".' };
            }
            const code = params['code'];
            if (!code) {
                settled = true;
                rejectCode(new OAuthError('invalid-response', 'O servidor não devolveu o código de autorização.'));
                return { ok: false, title: 'Código ausente', message: 'O servidor não devolveu o código de autorização.' };
            }
            settled = true;
            resolveCode(code);
            return { ok: true };
        };
        let timer;
        const onAbort = () => rejectCode(new OAuthError('cancelled', 'Login cancelado.'));
        try {
            if (signal?.aborted)
                throw new OAuthError('cancelled', 'Login cancelado.');
            signal?.addEventListener('abort', onAbort, { once: true });
            timer = setTimeout(() => rejectCode(new OAuthError('timeout', 'Tempo esgotado aguardando a autorização no navegador.')), this.config.timeoutMs);
            try {
                await receiver.listen(handler, { authUrl });
            }
            catch (err) {
                if (err instanceof OAuthError)
                    throw err;
                throw new OAuthError('listen-failed', 'Não foi possível abrir o recebimento do callback.');
            }
            await options.openUrl(authUrl);
            const code = await codePromise;
            return await this.exchange(code, receiver.redirectUri, verifier, signal);
        }
        finally {
            if (timer)
                clearTimeout(timer);
            signal?.removeEventListener('abort', onAbort);
            receiver.dispose();
        }
    }
    /** POST JSON no endpoint de token (formato usado pela CLI oficial). */
    async exchange(code, redirectUri, verifier, signal) {
        let res;
        try {
            res = await this.fetchImpl(this.config.tokenUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                body: JSON.stringify({
                    grant_type: 'authorization_code',
                    client_id: this.config.clientId,
                    code,
                    redirect_uri: redirectUri,
                    code_verifier: verifier,
                }),
                redirect: 'manual',
                signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(30_000)]) : AbortSignal.timeout(30_000),
            });
        }
        catch {
            if (signal?.aborted)
                throw new OAuthError('cancelled', 'Login cancelado.');
            throw new OAuthError('exchange-failed', 'Falha de rede ao trocar o código de autorização por tokens.');
        }
        const text = (await res.text().catch(() => '')).slice(0, 64 * 1024);
        let body;
        try {
            body = JSON.parse(text);
        }
        catch {
            body = undefined;
        }
        if (!res.ok) {
            let detail = '';
            if (body && typeof body === 'object') {
                const rec = body;
                for (const key of ['error_description', 'message', 'error', 'detail']) {
                    if (typeof rec[key] === 'string' && rec[key]) {
                        detail = (0, redact_1.sanitizeMessage)(rec[key], 200);
                        break;
                    }
                }
            }
            throw new OAuthError('exchange-failed', `A Venix Cloud recusou a troca do código (HTTP ${res.status})${detail ? `: ${detail}` : '.'}`);
        }
        if (!body || typeof body !== 'object') {
            throw new OAuthError('invalid-response', 'Resposta inválida do endpoint de token.');
        }
        const rec = body;
        // Alguns servidores aninham o resultado em `response`.
        const data = (rec['response'] && typeof rec['response'] === 'object' ? rec['response'] : rec);
        const accessToken = data['access_token'];
        if (typeof accessToken !== 'string' || accessToken.trim() === '') {
            throw new OAuthError('invalid-response', 'A resposta não trouxe um access_token.');
        }
        const expiresIn = Number(data['expires_in']);
        return {
            accessToken,
            expiresAt: Number.isFinite(expiresIn) && expiresIn > 0 ? this.now() + expiresIn * 1000 : undefined,
        };
    }
}
exports.VenixOAuthProvider = VenixOAuthProvider;
//# sourceMappingURL=OAuthProvider.js.map