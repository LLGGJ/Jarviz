import { LIMITS, MAX_BINARY_BYTES, MAX_JSON_BYTES } from '../constants';
import { normalizeBaseUrl } from '../security/trustedHosts';
import { sanitizeMessage } from '../security/redact';
import type { Logger } from '../utils/logger';
import { baseMessageFor, kindFromStatus, makeApiError, VenixApiError } from './errors';
import { isRecord } from './normalize';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type QueryValue = string | number | boolean | undefined;

export interface RequestOptions {
  query?: Record<string, QueryValue>;
  json?: unknown;
  form?: FormData;
  signal?: AbortSignal;
  timeoutMs?: number;
  /** Retentativas automáticas só valem para GET (idempotente) por padrão. */
  retry?: boolean;
}

export interface StreamOptions {
  signal: AbortSignal;
  onLine: (line: string) => void;
}

/** Contrato usado pelos módulos de domínio (facilita mocks nos testes). */
export interface VenixHttp {
  json<T = unknown>(method: HttpMethod, path: string, options?: RequestOptions): Promise<T>;
  buffer(method: HttpMethod, path: string, options?: RequestOptions): Promise<Uint8Array>;
  stream(path: string, options: StreamOptions): Promise<void>;
}

export interface ClientConfig {
  baseUrl: string;
  getToken: () => Promise<string | undefined> | string | undefined;
  /** Chamado quando a API responde 401 a uma requisição autenticada. */
  onUnauthorized?: () => void;
  fetch?: typeof fetch;
  defaultTimeoutMs?: number;
  maxRequestsPerMinute?: number;
  maxRetries?: number;
  sleep?: (ms: number, signal?: AbortSignal) => Promise<void>;
  now?: () => number;
  logger?: Pick<Logger, 'debug' | 'warn'>;
}

const RETRYABLE = new Set([429, 502, 503, 504]);
const MAX_RETRY_WAIT_MS = 10_000;
const MAX_LIMITER_WAIT_MS = 20_000;

export const defaultSleep = (ms: number, signal?: AbortSignal): Promise<void> =>
  new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(makeApiError('cancelled'));
      return;
    }
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    const onAbort = (): void => {
      clearTimeout(timer);
      reject(makeApiError('cancelled'));
    };
    signal?.addEventListener('abort', onAbort, { once: true });
  });

/** Janela deslizante: evita estourar o limite de 60 req/min da API. */
export class SlidingWindowLimiter {
  private readonly stamps: number[] = [];

  constructor(
    private readonly max: number,
    private readonly windowMs: number,
    private readonly now: () => number,
    private readonly sleep: (ms: number, signal?: AbortSignal) => Promise<void>,
  ) {}

  async acquire(signal?: AbortSignal): Promise<void> {
    for (;;) {
      const t = this.now();
      while (this.stamps.length > 0 && t - (this.stamps[0] as number) >= this.windowMs) this.stamps.shift();
      if (this.stamps.length < this.max) {
        this.stamps.push(t);
        return;
      }
      const wait = (this.stamps[0] as number) + this.windowMs - t;
      if (wait > MAX_LIMITER_WAIT_MS) {
        throw makeApiError('rate-limited', { retryAfterMs: wait });
      }
      await this.sleep(Math.max(wait, 1), signal);
    }
  }
}

function parseRetryAfter(header: string | null, now: number): number | undefined {
  if (!header) return undefined;
  const secs = Number(header);
  if (Number.isFinite(secs)) return Math.max(0, secs * 1000);
  const date = Date.parse(header);
  return Number.isNaN(date) ? undefined : Math.max(0, date - now);
}

function extractApiMessage(body: unknown): { message?: string; code?: string } {
  if (typeof body === 'string') return { message: sanitizeMessage(body) || undefined };
  if (!isRecord(body)) return {};
  let message: string | undefined;
  for (const key of ['message', 'error', 'detail', 'msg']) {
    const v = body[key];
    if (typeof v === 'string' && v) {
      message = v;
      break;
    }
    if (isRecord(v) && typeof v['message'] === 'string') {
      message = v['message'];
      break;
    }
  }
  let code: string | undefined;
  for (const key of ['code', 'error_code', 'errorCode', 'error']) {
    const v = body[key];
    if (typeof v === 'string' && /^[A-Z][A-Z0-9_]+$/.test(v)) {
      code = v;
      break;
    }
  }
  return { message: message ? sanitizeMessage(message) : undefined, code };
}

/**
 * Remove o envelope `{status, message, response}` observado nas respostas.
 * Respostas sem envelope (ex.: `{status:"success", url, size}` do CDN) passam inalteradas.
 */
export function unwrapEnvelope(body: unknown): unknown {
  if (!isRecord(body)) return body;
  const status = body['status'];
  if (typeof status === 'string' && status !== '' && status.toLowerCase() !== 'success') {
    const { message } = extractApiMessage(body);
    throw makeApiError('unexpected', { apiMessage: message ?? status });
  }
  if ('response' in body && body['response'] !== undefined && body['response'] !== null) {
    return body['response'];
  }
  return body;
}

export class VenixApiClient implements VenixHttp {
  readonly baseUrl: string;
  private readonly fetchImpl: typeof fetch;
  private readonly limiter: SlidingWindowLimiter;
  private readonly sleep: (ms: number, signal?: AbortSignal) => Promise<void>;
  private readonly now: () => number;

  constructor(private readonly config: ClientConfig) {
    // A política de domínios confiáveis é aplicada aqui: o token nunca sai para outro host.
    this.baseUrl = normalizeBaseUrl(config.baseUrl);
    this.fetchImpl = config.fetch ?? globalThis.fetch.bind(globalThis);
    this.sleep = config.sleep ?? defaultSleep;
    this.now = config.now ?? Date.now;
    this.limiter = new SlidingWindowLimiter(
      config.maxRequestsPerMinute ?? LIMITS.requestsPerMinute - 5,
      60_000,
      this.now,
      this.sleep,
    );
  }

  async json<T = unknown>(method: HttpMethod, path: string, options: RequestOptions = {}): Promise<T> {
    const res = await this.execute(method, path, options, 'text/plain');
    const text = await this.readText(res, MAX_JSON_BYTES);
    if (text.trim() === '') return {} as T;
    let parsed: unknown;
    try {
      parsed = JSON.parse(text);
    } catch {
      throw makeApiError('invalid-response', { status: res.status });
    }
    return unwrapEnvelope(parsed) as T;
  }

  async buffer(method: HttpMethod, path: string, options: RequestOptions = {}): Promise<Uint8Array> {
    const res = await this.execute(method, path, options, '*/*');
    const declared = Number(res.headers.get('content-length') ?? '0');
    if (declared > MAX_BINARY_BYTES) throw makeApiError('invalid-response', { status: res.status });
    const data = new Uint8Array(await res.arrayBuffer());
    if (data.byteLength > MAX_BINARY_BYTES) throw makeApiError('invalid-response', { status: res.status });
    return data;
  }

  async stream(path: string, options: StreamOptions): Promise<void> {
    const token = await this.requireToken();
    await this.limiter.acquire(options.signal);
    const controller = new AbortController();
    const onAbort = (): void => controller.abort();
    options.signal.addEventListener('abort', onAbort, { once: true });
    let connectTimedOut = false;
    const connectTimer = setTimeout(() => {
      connectTimedOut = true;
      controller.abort();
    }, 30_000);
    try {
      let res: Response;
      try {
        res = await this.fetchImpl(this.buildUrl(path), {
          method: 'GET',
          headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
          redirect: 'manual',
          signal: controller.signal,
        });
      } catch (err) {
        throw this.mapFetchError(err, options.signal, connectTimedOut);
      } finally {
        clearTimeout(connectTimer);
      }
      if (!res.ok) throw await this.errorFromResponse(res, true);
      if (!res.body) return;
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      try {
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          if (buffer.length > 1 << 20) buffer = buffer.slice(-(1 << 16));
          let idx: number;
          while ((idx = buffer.indexOf('\n')) >= 0) {
            const line = buffer.slice(0, idx).replace(/\r$/, '');
            buffer = buffer.slice(idx + 1);
            if (line.startsWith('data:')) options.onLine(line.slice(5).trim());
          }
        }
      } catch (err) {
        if (options.signal.aborted) return; // encerramento pedido pelo usuário
        throw this.mapFetchError(err, options.signal, false);
      }
    } finally {
      clearTimeout(connectTimer);
      options.signal.removeEventListener('abort', onAbort);
    }
  }

  // ---------------------------------------------------------------- internos

  private async requireToken(): Promise<string> {
    const token = await this.config.getToken();
    if (!token) throw makeApiError('not-authenticated');
    return token;
  }

  private buildUrl(path: string, query?: Record<string, QueryValue>): string {
    if (!path.startsWith('/') || path.includes('://') || path.includes('..')) {
      throw makeApiError('invalid-input', { message: 'Caminho de API inválido.' });
    }
    const url = new URL(this.baseUrl + path);
    for (const [key, value] of Object.entries(query ?? {})) {
      if (value !== undefined) url.searchParams.set(key, String(value));
    }
    return url.toString();
  }

  private async execute(method: HttpMethod, path: string, options: RequestOptions, accept: string): Promise<Response> {
    const token = await this.requireToken();
    const url = this.buildUrl(path, options.query);
    const retryable = options.retry ?? method === 'GET';
    const maxRetries = retryable ? (this.config.maxRetries ?? 2) : 0;
    const timeoutMs = options.timeoutMs ?? this.config.defaultTimeoutMs ?? 30_000;

    for (let attempt = 0; ; attempt++) {
      await this.limiter.acquire(options.signal);
      const controller = new AbortController();
      let timedOut = false;
      const timer = setTimeout(() => {
        timedOut = true;
        controller.abort();
      }, timeoutMs);
      const onAbort = (): void => controller.abort();
      options.signal?.addEventListener('abort', onAbort, { once: true });
      if (options.signal?.aborted) controller.abort();

      try {
        const headers: Record<string, string> = {
          Accept: accept === 'text/plain' ? 'application/json' : accept,
          Authorization: `Bearer ${token}`,
        };
        let body: BodyInit | undefined;
        if (options.form) {
          body = options.form; // o fetch define o Content-Type multipart com boundary
        } else if (options.json !== undefined) {
          headers['Content-Type'] = 'application/json';
          body = JSON.stringify(options.json);
        }
        this.config.logger?.debug(`${method} ${path}`);
        let res: Response;
        try {
          res = await this.fetchImpl(url, { method, headers, body, redirect: 'manual', signal: controller.signal });
        } catch (err) {
          const mapped = this.mapFetchError(err, options.signal, timedOut);
          if (mapped.kind === 'network' && attempt < maxRetries) {
            await this.sleep(500 * 2 ** attempt, options.signal);
            continue;
          }
          throw mapped;
        }
        this.config.logger?.debug(`${method} ${path} -> ${res.status}`);

        if (res.ok) return res;

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
      } finally {
        clearTimeout(timer);
        options.signal?.removeEventListener('abort', onAbort);
      }
    }
  }

  private async readText(res: Response, limit: number): Promise<string> {
    const declared = Number(res.headers.get('content-length') ?? '0');
    if (declared > limit) throw makeApiError('invalid-response', { status: res.status });
    const text = await res.text();
    if (text.length > limit) throw makeApiError('invalid-response', { status: res.status });
    return text;
  }

  private async errorFromResponse(res: Response, notify: boolean): Promise<VenixApiError> {
    let parsed: unknown;
    try {
      const text = (await res.text()).slice(0, 64 * 1024);
      try {
        parsed = JSON.parse(text);
      } catch {
        parsed = text;
      }
    } catch {
      parsed = undefined;
    }
    const { message, code } = extractApiMessage(parsed);
    const kind = kindFromStatus(res.status, code);
    if (kind === 'unauthorized' && notify) {
      this.config.onUnauthorized?.();
    }
    const retryAfterMs = parseRetryAfter(res.headers.get('retry-after'), this.now());
    return new VenixApiError({
      kind,
      message: baseMessageFor(kind),
      status: res.status,
      code,
      apiMessage: message,
      retryAfterMs,
    });
  }

  private mapFetchError(err: unknown, userSignal: AbortSignal | undefined, timedOut: boolean): VenixApiError {
    if (err instanceof VenixApiError) return err;
    if (timedOut) return makeApiError('timeout');
    if (userSignal?.aborted) return makeApiError('cancelled');
    return makeApiError('network');
  }
}
