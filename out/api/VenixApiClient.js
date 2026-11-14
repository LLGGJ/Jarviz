"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.VenixApiClient = exports.SlidingWindowLimiter = exports.defaultSleep = void 0;
exports.unwrapEnvelope = unwrapEnvelope;
const constants_1 = require("../constants");
const trustedHosts_1 = require("../security/trustedHosts");
const redact_1 = require("../security/redact");
const errors_1 = require("./errors");
const normalize_1 = require("./normalize");
const RETRYABLE = new Set([429, 502, 503, 504]);
const MAX_RETRY_WAIT_MS = 10_000;
const MAX_LIMITER_WAIT_MS = 20_000;
const defaultSleep = (ms, signal) => new Promise((resolve, reject) => {
    if (signal?.aborted) {
        reject((0, errors_1.makeApiError)('cancelled'));
        return;
    }
    const timer = setTimeout(() => {
        signal?.removeEventListener('abort', onAbort);
        resolve();
    }, ms);
    const onAbort = () => {
        clearTimeout(timer);
        reject((0, errors_1.makeApiError)('cancelled'));
    };
    signal?.addEventListener('abort', onAbort, { once: true });
});
exports.defaultSleep = defaultSleep;
/** Janela deslizante: evita estourar o limite de 60 req/min da API. */
class SlidingWindowLimiter {
    max;
    windowMs;
    now;
    sleep;
    stamps = [];
    constructor(max, windowMs, now, sleep) {
        this.max = max;
        this.windowMs = windowMs;
        this.now = now;
        this.sleep = sleep;
    }
    async acquire(signal) {
        for (;;) {
            const t = this.now();
            while (this.stamps.length > 0 && t - this.stamps[0] >= this.windowMs)
                this.stamps.shift();
            if (this.stamps.length < this.max) {
                this.stamps.push(t);
                return;
            }
            const wait = this.stamps[0] + this.windowMs - t;
            if (wait > MAX_LIMITER_WAIT_MS) {
                throw (0, errors_1.makeApiError)('rate-limited', { retryAfterMs: wait });
            }
            await this.sleep(Math.max(wait, 1), signal);
        }
    }
}
exports.SlidingWindowLimiter = SlidingWindowLimiter;
function parseRetryAfter(header, now) {
    if (!header)
        return undefined;
    const secs = Number(header);
    if (Number.isFinite(secs))
        return Math.max(0, secs * 1000);
    const date = Date.parse(header);
    return Number.isNaN(date) ? undefined : Math.max(0, date - now);
}
function extractApiMessage(body) {
    if (typeof body === 'string')
        return { message: (0, redact_1.sanitizeMessage)(body) || undefined };
    if (!(0, normalize_1.isRecord)(body))
        return {};
    let message;
    for (const key of ['message', 'error', 'detail', 'msg']) {
        const v = body[key];
        if (typeof v === 'string' && v) {
            message = v;
            break;
        }
        if ((0, normalize_1.isRecord)(v) && typeof v['message'] === 'string') {
            message = v['message'];
            break;
        }
    }
    let code;
    for (const key of ['code', 'error_code', 'errorCode', 'error']) {
        const v = body[key];
        if (typeof v === 'string' && /^[A-Z][A-Z0-9_]+$/.test(v)) {
            code = v;
            break;
        }
    }
    return { message: message ? (0, redact_1.sanitizeMessage)(message) : undefined, code };
}
/**
 * Remove o envelope `{status, message, response}` observado nas respostas.
 * Respostas sem envelope (ex.: `{status:"success", url, size}` do CDN) passam inalteradas.
 */
function unwrapEnvelope(body) {
    if (!(0, normalize_1.isRecord)(body))
        return body;
    const status = body['status'];
    if (typeof status === 'string' && status !== '' && status.toLowerCase() !== 'success') {
        const { message } = extractApiMessage(body);
        throw (0, errors_1.makeApiError)('unexpected', { apiMessage: message ?? status });
    }
    if ('response' in body && body['response'] !== undefined && body['response'] !== null) {
        return body['response'];
    }
    return body;
}
class VenixApiClient {
    config;
    baseUrl;
    fetchImpl;
    limiter;
    sleep;
    now;
    constructor(config) {
        this.config = config;
        // A política de domínios confiáveis é aplicada aqui: o token nunca sai para outro host.
        this.baseUrl = (0, trustedHosts_1.normalizeBaseUrl)(config.baseUrl);
        this.fetchImpl = config.fetch ?? globalThis.fetch.bind(globalThis);
        this.sleep = config.sleep ?? exports.defaultSleep;
        this.now = config.now ?? Date.now;
        this.limiter = new SlidingWindowLimiter(config.maxRequestsPerMinute ?? constants_1.LIMITS.requestsPerMinute - 5, 60_000, this.now, this.sleep);
    }
    async json(method, path, options = {}) {
        const res = await this.execute(method, path, options, 'text/plain');
        const text = await this.readText(res, constants_1.MAX_JSON_BYTES);
        if (text.trim() === '')
            return {};
        let parsed;
        try {
            parsed = JSON.parse(text);
        }
        catch {
            throw (0, errors_1.makeApiError)('invalid-response', { status: res.status });
        }
        return unwrapEnvelope(parsed);
    }
    async buffer(method, path, options = {}) {
        const res = await this.execute(method, path, options, '*/*');
        const declared = Number(res.headers.get('content-length') ?? '0');
        if (declared > constants_1.MAX_BINARY_BYTES)
            throw (0, errors_1.makeApiError)('invalid-response', { status: res.status });
        const data = new Uint8Array(await res.arrayBuffer());
        if (data.byteLength > constants_1.MAX_BINARY_BYTES)
            throw (0, errors_1.makeApiError)('invalid-response', { status: res.status });
        return data;
    }
    async stream(path, options) {
        const token = await this.requireToken();
        await this.limiter.acquire(options.signal);
        const controller = new AbortController();
        const onAbort = () => controller.abort();
        options.signal.addEventListener('abort', onAbort, { once: true });
        let connectTimedOut = false;
        const connectTimer = setTimeout(() => {
            connectTimedOut = true;
            controller.abort();
        }, 30_000);
        try {
            let res;
            try {
                res = await this.fetchImpl(this.buildUrl(path), {
                    method: 'GET',
                    headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
                    redirect: 'manual',
                    signal: controller.signal,
                });
            }
            catch (err) {
                throw this.mapFetchError(err, options.signal, connectTimedOut);
            }
            finally {
                clearTimeout(connectTimer);
            }
            if (!res.ok)
                throw await this.errorFromResponse(res, true);
            if (!res.body)
                return;
            const reader = res.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            try {
                for (;;) {
                    const { done, value } = await reader.read();
                    if (done)
                        break;
                    buffer += decoder.decode(value, { stream: true });
                    if (buffer.length > 1 << 20)
                        buffer = buffer.slice(-(1 << 16));
                    let idx;
                    while ((idx = buffer.indexOf('\n')) >= 0) {
                        const line = buffer.slice(0, idx).replace(/\r$/, '');
                        buffer = buffer.slice(idx + 1);
                        if (line.startsWith('data:'))
                            options.onLine(line.slice(5).trim());
                    }
                }
            }
            catch (err) {
                if (options.signal.aborted)
                    return; // encerramento pedido pelo usuário
                throw this.mapFetchError(err, options.signal, false);
            }
        }
        finally {
            clearTimeout(connectTimer);
            options.signal.removeEventListener('abort', onAbort);
        }
    }
    // ---------------------------------------------------------------- internos
    async requireToken() {
        const token = await this.config.getToken();
        if (!token)
            throw (0, errors_1.makeApiError)('not-authenticated');
        return token;
    }
    buildUrl(path, query) {
        if (!path.startsWith('/') || path.includes('://') || path.includes('..')) {
            throw (0, errors_1.makeApiError)('invalid-input', { message: 'Caminho de API inválido.' });
        }
        const url = new URL(this.baseUrl + path);
        for (const [key, value] of Object.entries(query ?? {})) {
            if (value !== undefined)
                url.searchParams.set(key, String(value));
        }
        return url.toString();
    }
    async execute(method, path, options, accept) {
        const token = await this.requireToken();
        const url = this.buildUrl(path, options.query);
        const retryable = options.retry ?? method === 'GET';
        const maxRetries = retryable ? (this.config.maxRetries ?? 2) : 0;
        const timeoutMs = options.timeoutMs ?? this.config.defaultTimeoutMs ?? 30_000;
        for (let attempt = 0;; attempt++) {
            await this.limiter.acquire(options.signal);
            const controller = new AbortController();
            let timedOut = false;
            const timer = setTimeout(() => {
                timedOut = true;
                controller.abort();
            }, timeoutMs);
            const onAbort = () => controller.abort();
            options.signal?.addEventListener('abort', onAbort, { once: true });
            if (options.signal?.aborted)
                controller.abort();
            try {
                const headers = {
                    Accept: accept === 'text/plain' ? 'application/json' : accept,
                    Authorization: `Bearer ${token}`,
                };
                let body;
                if (options.form) {
                    body = options.form; // o fetch define o Content-Type multipart com boundary
                }
                else if (options.json !== undefined) {
                    headers['Content-Type'] = 'application/json';
                    body = JSON.stringify(options.json);
                }
                this.config.logger?.debug(`${method} ${path}`);
                let res;
                try {
                    res = await this.fetchImpl(url, { method, headers, body, redirect: 'manual', signal: controller.signal });
                }
                catch (err) {
                    const mapped = this.mapFetchError(err, options.signal, timedOut);
                    if (mapped.kind === 'network' && attempt < maxRetries) {
                        await this.sleep(500 * 2 ** attempt, options.signal);
                        continue;
                    }
                    throw mapped;
                }
                this.config.logger?.debug(`${method} ${path} -> ${res.status}`);
                if (res.ok)
                    return res;
                if (RETRYABLE.has(res.status) && attempt < maxRetries) {
                    const retryAfter = parseRetryAfter(res.headers.get('retry-after'), this.now());
                    const wait = retryAfter ?? 500 * 2 ** attempt;
                    if (wait <= MAX_RETRY_WAIT_MS) {
                        await res.body?.cancel().catch(() => undefined);
                        await this.sleep(wait, options.signal);
                        continue;
                    }
                }
                throw await this.errorFromResponse(res, true);
            }
            finally {
                clearTimeout(timer);
                options.signal?.removeEventListener('abort', onAbort);
            }
        }
    }
    async readText(res, limit) {
        const declared = Number(res.headers.get('content-length') ?? '0');
        if (declared > limit)
            throw (0, errors_1.makeApiError)('invalid-response', { status: res.status });
        const text = await res.text();
        if (text.length > limit)
            throw (0, errors_1.makeApiError)('invalid-response', { status: res.status });
        return text;
    }
    async errorFromResponse(res, notify) {
        let parsed;
        try {
            const text = (await res.text()).slice(0, 64 * 1024);
            try {
                parsed = JSON.parse(text);
            }
            catch {
                parsed = text;
            }
        }
        catch {
            parsed = undefined;
        }
        const { message, code } = extractApiMessage(parsed);
        const kind = (0, errors_1.kindFromStatus)(res.status, code);
        if (kind === 'unauthorized' && notify) {
            this.config.onUnauthorized?.();
        }
        const retryAfterMs = parseRetryAfter(res.headers.get('retry-after'), this.now());
        return new errors_1.VenixApiError({
            kind,
            message: (0, errors_1.baseMessageFor)(kind),
            status: res.status,
            code,
            apiMessage: message,
            retryAfterMs,
        });
    }
    mapFetchError(err, userSignal, timedOut) {
        if (err instanceof errors_1.VenixApiError)
            return err;
        if (timedOut)
            return (0, errors_1.makeApiError)('timeout');
        if (userSignal?.aborted)
            return (0, errors_1.makeApiError)('cancelled');
        return (0, errors_1.makeApiError)('network');
    }
}
exports.VenixApiClient = VenixApiClient;
//# sourceMappingURL=VenixApiClient.js.map