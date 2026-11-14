import { test } from 'node:test';
import assert from 'node:assert/strict';
import { VenixApiClient, SlidingWindowLimiter, unwrapEnvelope } from '../src/api/VenixApiClient';
import { describeError, VenixApiError } from '../src/api/errors';
import { fakeFetch, jsonResponse, noSleep, TOKEN } from './helpers';

const BASE = 'https://api.venixcloud.com/v1';

function client(responder: Parameters<typeof fakeFetch>[0], extra: Partial<ConstructorParameters<typeof VenixApiClient>[0]> = {}) {
  const f = fakeFetch(responder);
  const c = new VenixApiClient({ baseUrl: BASE, getToken: () => TOKEN, fetch: f.fetch, sleep: noSleep, ...extra });
  return { c, ...f };
}

async function kindOf(p: Promise<unknown>): Promise<string> {
  try {
    await p;
  } catch (e) {
    if (e instanceof VenixApiError) return e.kind;
    throw e;
  }
  return 'no-error';
}

test('envia Bearer, Accept e serializa query ignorando undefined', async () => {
  const { c, calls } = client(() => jsonResponse(200, { status: 'success', response: { ok: 1 } }));
  const out = await c.json('GET', '/snapshots', { query: { page: 2, limit: undefined, q: 'a b' } });
  assert.deepEqual(out, { ok: 1 });
  assert.equal(calls[0]?.url, `${BASE}/snapshots?page=2&q=a+b`);
  assert.equal(calls[0]?.headers['authorization'], `Bearer ${TOKEN}`);
  assert.equal(calls[0]?.headers['accept'], 'application/json');
});

test('corpo JSON recebe Content-Type; multipart deixa o boundary para o fetch', async () => {
  const { c, calls } = client(() => jsonResponse(200, {}));
  await c.json('POST', '/apps/x/action', { json: { action: 'START' } });
  assert.equal(calls[0]?.headers['content-type'], 'application/json');
  assert.equal(calls[0]?.body, '{"action":"START"}');
  const form = new FormData();
  form.append('a', 'b');
  await c.json('POST', '/objects', { form });
  assert.equal(calls[1]?.headers['content-type'], undefined);
  assert.ok(calls[1]?.body instanceof FormData);
});

test('sem token: não-autenticado e NENHUMA requisição de rede', async () => {
  const { c, calls } = client(() => jsonResponse(200, {}), { getToken: () => undefined });
  assert.equal(await kindOf(c.json('GET', '/me')), 'not-authenticated');
  assert.equal(calls.length, 0);
});

test('URL-base fora do domínio confiável é recusada no construtor (token nunca sai)', () => {
  for (const baseUrl of ['https://evil.example/v1', 'http://api.venixcloud.com/v1']) {
    assert.throws(() => new VenixApiClient({ baseUrl, getToken: () => TOKEN }));
  }
});

test('caminhos suspeitos são recusados antes de qualquer requisição', async () => {
  const { c, calls } = client(() => jsonResponse(200, {}));
  for (const p of ['/../admin', 'me', 'https://evil.com/x']) assert.equal(await kindOf(c.json('GET', p)), 'invalid-input');
  assert.equal(calls.length, 0);
});

test('mapeia status HTTP para tipos de erro', async () => {
  const table: Array<[number, string, unknown?]> = [
    [400, 'invalid-input'],
    [401, 'unauthorized'],
    [403, 'forbidden'],
    [403, 'quota-exceeded', { code: 'QUOTA_EXCEEDED' }],
    [404, 'not-found'],
    [405, 'not-allowed'],
    [409, 'conflict'],
    [413, 'payload-too-large'],
    [500, 'server-error'],
    [302, 'invalid-response'],
  ];
  for (const [status, kind, body] of table) {
    const { c } = client(() => jsonResponse(status, body ?? { message: 'x' }));
    assert.equal(await kindOf(c.json('POST', '/x')), kind, `${status}`);
  }
});

test('401 aciona onUnauthorized exatamente uma vez', async () => {
  let n = 0;
  const { c } = client(() => jsonResponse(401, { message: 'token revogado' }), { onUnauthorized: () => void n++ });
  assert.equal(await kindOf(c.json('GET', '/me')), 'unauthorized');
  assert.equal(n, 1);
});

test('GET repete em 503 e 429 (com Retry-After) e depois tem sucesso', async () => {
  let i = 0;
  const sleeps: number[] = [];
  const { c, calls } = client(
    () => {
      i++;
      if (i === 1) return jsonResponse(503, {});
      if (i === 2) return jsonResponse(429, {}, { 'retry-after': '2' });
      return jsonResponse(200, { response: [1] });
    },
    { sleep: async (ms) => void sleeps.push(ms) },
  );
  assert.deepEqual(await c.json('GET', '/apps/status'), [1]);
  assert.equal(calls.length, 3);
  assert.deepEqual(sleeps, [500, 2000]);
});

test('POST nunca é repetido automaticamente', async () => {
  const { c, calls } = client(() => jsonResponse(503, {}));
  assert.equal(await kindOf(c.json('POST', '/apps/create')), 'server-unavailable');
  assert.equal(calls.length, 1);
});

test('429 com espera longa não bloqueia: devolve rate-limited com retryAfter', async () => {
  const { c } = client(() => jsonResponse(429, {}, { 'retry-after': '120' }));
  try {
    await c.json('GET', '/me');
    assert.fail('deveria falhar');
  } catch (e) {
    assert.ok(e instanceof VenixApiError);
    assert.equal(e.kind, 'rate-limited');
    assert.equal(e.retryAfterMs, 120_000);
    assert.match(describeError(e), /120s/);
  }
});

test('timeout vira erro "timeout" (sem retentar)', async () => {
  const f = fakeFetch(() => new Promise<Response>(() => undefined));
  // fetch que respeita o abort
  const hanging = ((_url: string, init?: RequestInit) =>
    new Promise<Response>((_res, rej) => init?.signal?.addEventListener('abort', () => rej(new DOMException('abort', 'AbortError'))))) as unknown as typeof fetch;
  const c = new VenixApiClient({ baseUrl: BASE, getToken: () => TOKEN, fetch: hanging, defaultTimeoutMs: 20 });
  assert.equal(await kindOf(c.json('GET', '/me')), 'timeout');
  void f;
});

test('falha de rede vira "network" e GET é retentado até o limite', async () => {
  let n = 0;
  const f = (() => {
    n++;
    return Promise.reject(new TypeError('fetch failed'));
  }) as unknown as typeof fetch;
  const c = new VenixApiClient({ baseUrl: BASE, getToken: () => TOKEN, fetch: f, sleep: noSleep });
  assert.equal(await kindOf(c.json('GET', '/me')), 'network');
  assert.equal(n, 3);
});

test('cancelamento pelo usuário vira "cancelled"', async () => {
  const ctrl = new AbortController();
  ctrl.abort();
  const { c } = client(() => jsonResponse(200, {}));
  assert.equal(await kindOf(c.json('GET', '/me', { signal: ctrl.signal })), 'cancelled');
});

test('JSON inválido em 200 → invalid-response; corpo vazio → {}', async () => {
  assert.equal(await kindOf(client(() => new Response('<html>', { status: 200 })).c.json('GET', '/x')), 'invalid-response');
  assert.deepEqual(await client(() => new Response('', { status: 200 })).c.json('GET', '/x'), {});
});

test('envelope: status != success vira erro; response é desembrulhado; objetos do CDN passam intactos', () => {
  assert.throws(() => unwrapEnvelope({ status: 'error', message: 'falhou' }), VenixApiError);
  assert.deepEqual(unwrapEnvelope({ status: 'success', response: { id: 1 } }), { id: 1 });
  assert.deepEqual(unwrapEnvelope({ status: 'success', url: 'https://cdn/x', size: 3 }), { status: 'success', url: 'https://cdn/x', size: 3 });
  assert.deepEqual(unwrapEnvelope([1, 2]), [1, 2]);
});

test('buffer devolve bytes (ex.: ZIP de certificados)', async () => {
  const { c } = client(() => new Response(new Uint8Array([80, 75, 3, 4]), { status: 200 }));
  assert.deepEqual([...(await c.buffer('GET', '/instances/databases/1/credentials/zip'))], [80, 75, 3, 4]);
});

test('mensagens de erro nunca contêm o token nem cabeçalhos', async () => {
  const { c } = client(() => jsonResponse(500, { message: 'falha interna' }));
  try {
    await c.json('GET', '/me');
  } catch (e) {
    const text = `${describeError(e)} ${JSON.stringify(e)} ${(e as Error).message}`;
    assert.ok(!text.includes(TOKEN));
    assert.ok(!/authorization/i.test(text));
  }
});

test('mensagem da API é sanitizada e truncada', async () => {
  const { c } = client(() => jsonResponse(400, { message: `x\u0000${'y'.repeat(600)}` }));
  try {
    await c.json('POST', '/x');
  } catch (e) {
    assert.ok((e as VenixApiError).apiMessage!.length <= 301);
    assert.ok(!(e as VenixApiError).apiMessage!.includes('\u0000'));
  }
});

test('limitador de janela: espera quando o limite é atingido', async () => {
  let t = 0;
  const waited: number[] = [];
  const lim = new SlidingWindowLimiter(2, 1000, () => t, async (ms) => {
    waited.push(ms);
    t += ms;
  });
  await lim.acquire();
  await lim.acquire();
  await lim.acquire(); // precisa esperar 1000 ms
  assert.deepEqual(waited, [1000]);
});

test('stream SSE entrega apenas linhas data: e respeita abort', async () => {
  const enc = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(ctrl) {
      ctrl.enqueue(enc.encode('event: x\ndata: {"log":"a"}\n\n'));
      ctrl.enqueue(enc.encode('data: lin'));
      ctrl.enqueue(enc.encode('ha2\r\n: ping\n'));
      ctrl.close();
    },
  });
  const { c, calls } = client(() => new Response(body, { status: 200, headers: { 'content-type': 'text/event-stream' } }));
  const lines: string[] = [];
  await c.stream('/instances/stream/abc', { signal: new AbortController().signal, onLine: (l) => lines.push(l) });
  assert.deepEqual(lines, ['{"log":"a"}', 'linha2']);
  assert.equal(calls[0]?.headers['accept'], 'text/event-stream');
  assert.equal(calls[0]?.headers['authorization'], `Bearer ${TOKEN}`);
});
