import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as http from 'node:http';
import { LoopbackReceiver } from '../src/auth/CallbackServer';
import { escapeHtml } from '../src/auth/callbackPage';
import { OAuthError, VenixOAuthProvider, type CallbackParams, type CallbackReceiver, type CallbackResult } from '../src/auth/OAuthProvider';
import { codeChallengeS256 } from '../src/auth/pkce';
import { OAUTH_AUTHORIZE_URL, OAUTH_CLIENT_ID, OAUTH_TOKEN_URL } from '../src/constants';
import { fakeFetch, jsonResponse } from './helpers';

const REDIRECT = 'http://127.0.0.1:53682/callback';

/** Receptor controlável: o teste decide quando/como o "navegador" volta. */
class FakeReceiver implements CallbackReceiver {
  redirectUri = REDIRECT;
  handler?: (p: CallbackParams) => CallbackResult;
  authUrl = '';
  disposed = false;
  failListen?: Error;
  async listen(handler: (p: CallbackParams) => CallbackResult, ctx: { authUrl: string }): Promise<void> {
    if (this.failListen) throw this.failListen;
    this.handler = handler;
    this.authUrl = ctx.authUrl;
  }
  dispose(): void {
    this.disposed = true;
  }
}

function provider(responder: Parameters<typeof fakeFetch>[0], timeoutMs = 5000) {
  const f = fakeFetch(responder);
  return { p: new VenixOAuthProvider({ clientId: OAUTH_CLIENT_ID, authorizeUrl: OAUTH_AUTHORIZE_URL, tokenUrl: OAUTH_TOKEN_URL, timeoutMs, fetch: f.fetch, now: () => 1_000_000 }), ...f };
}

/** Inicia o login e devolve o state/URL assim que o receptor estiver ouvindo. */
async function begin(p: VenixOAuthProvider, r: FakeReceiver, signal?: AbortSignal) {
  const result = p.signIn({ receiver: r, openUrl: async () => true, signal });
  result.catch(() => undefined);
  while (!r.handler) await new Promise((res) => setImmediate(res));
  const url = new URL(r.authUrl);
  return { result, url, state: url.searchParams.get('state') as string };
}

test('URL de autorização: client_id, redirect_uri, response_type=code, PKCE S256 e state', async () => {
  const { p } = provider(() => jsonResponse(200, {}));
  const r = new FakeReceiver();
  const { url, state } = await begin(p, r);
  assert.equal(url.origin + url.pathname, OAUTH_AUTHORIZE_URL);
  assert.equal(url.searchParams.get('client_id'), 'venix_54620c6a2b626ec6d2');
  assert.equal(url.searchParams.get('redirect_uri'), REDIRECT);
  assert.equal(url.searchParams.get('response_type'), 'code');
  assert.equal(url.searchParams.get('code_challenge_method'), 'S256');
  assert.match(url.searchParams.get('code_challenge') ?? '', /^[A-Za-z0-9_-]{43}$/);
  assert.ok(state.length >= 24);
  assert.ok(!url.search.includes('code_verifier'));
  r.handler?.({ error: 'access_denied', state });
});

test('fluxo feliz: troca o código com code_verifier correspondente ao challenge e calcula expiração', async () => {
  const { p, calls } = provider(() => jsonResponse(200, { access_token: 'acc_123456789', refresh_token: 'ref_1', expires_in: 3600 }));
  const r = new FakeReceiver();
  const { result, url, state } = await begin(p, r);
  const outcome = r.handler?.({ code: 'CODE-1', state });
  assert.deepEqual(outcome, { ok: true });
  const tokens = await result;
  assert.equal(tokens.accessToken, 'acc_123456789');
  assert.equal(tokens.expiresAt, 1_000_000 + 3_600_000);
  assert.equal(r.disposed, true);

  const call = calls[0];
  assert.equal(call?.url, OAUTH_TOKEN_URL);
  assert.equal(call?.method, 'POST');
  assert.equal(call?.headers['content-type'], 'application/json');
  const body = JSON.parse(call?.body as string) as Record<string, string>;
  assert.equal(body['grant_type'], 'authorization_code');
  assert.equal(body['client_id'], OAUTH_CLIENT_ID);
  assert.equal(body['code'], 'CODE-1');
  assert.equal(body['redirect_uri'], REDIRECT);
  assert.equal(codeChallengeS256(body['code_verifier'] as string), url.searchParams.get('code_challenge'));
  assert.equal(body['client_secret'], undefined); // cliente público: sem segredo
  assert.equal(call?.headers['authorization'], undefined);
});

test('state divergente é rejeitado SEM abortar o login legítimo (anti-CSRF)', async () => {
  const { p } = provider(() => jsonResponse(200, { access_token: 'acc_123456789' }));
  const r = new FakeReceiver();
  const { result, state } = await begin(p, r);
  const forged = r.handler?.({ code: 'ATTACKER', state: 'forjado' });
  assert.equal(forged?.ok, false);
  const genuine = r.handler?.({ code: 'REAL', state });
  assert.deepEqual(genuine, { ok: true });
  assert.equal((await result).accessToken, 'acc_123456789');
});

test('callback sem state é rejeitado (não troca o código)', async () => {
  const { p, calls } = provider(() => jsonResponse(200, { access_token: 'acc_123456789' }));
  const r = new FakeReceiver();
  const { result, state } = await begin(p, r);
  assert.equal(r.handler?.({ code: 'X' })?.ok, false);
  assert.equal(calls.length, 0);
  r.handler?.({ error: 'x', state });
  await assert.rejects(result);
});

test('usuário nega: error/error_description viram OAuthError("denied")', async () => {
  const { p } = provider(() => jsonResponse(200, {}));
  const r = new FakeReceiver();
  const { result, state } = await begin(p, r);
  const out = r.handler?.({ error: 'access_denied', error_description: 'O usuário negou', state });
  assert.equal(out?.ok, false);
  await assert.rejects(result, (e: unknown) => e instanceof OAuthError && e.kind === 'denied' && /negou/.test(e.message));
});

test('o código só pode ser usado uma vez', async () => {
  const { p, calls } = provider(() => jsonResponse(200, { access_token: 'acc_123456789' }));
  const r = new FakeReceiver();
  const { result, state } = await begin(p, r);
  r.handler?.({ code: 'A', state });
  const second = r.handler?.({ code: 'B', state });
  assert.equal(second?.ok, false);
  await result;
  assert.equal(calls.length, 1);
});

test('servidor recusa a troca (HTTP 400): erro claro, sem vazar código/verifier', async () => {
  const { p } = provider(() => jsonResponse(400, { error_description: 'código inválido' }));
  const r = new FakeReceiver();
  const { result, state } = await begin(p, r);
  r.handler?.({ code: 'SECRET-CODE-XYZ', state });
  await assert.rejects(result, (e: unknown) => e instanceof OAuthError && e.kind === 'exchange-failed' && /400/.test(e.message) && !e.message.includes('SECRET-CODE-XYZ'));
});

test('resposta sem access_token ou fora de JSON é inválida', async () => {
  for (const res of [jsonResponse(200, { token_type: 'bearer' }), new Response('<html>', { status: 200 })]) {
    const { p } = provider(() => res.clone());
    const r = new FakeReceiver();
    const { result, state } = await begin(p, r);
    r.handler?.({ code: 'A', state });
    await assert.rejects(result, (e: unknown) => e instanceof OAuthError && e.kind === 'invalid-response');
  }
});

test('resposta aninhada em "response" e expires_in ausente são aceitos', async () => {
  const { p } = provider(() => jsonResponse(200, { status: 'success', response: { access_token: 'acc_123456789' } }));
  const r = new FakeReceiver();
  const { result, state } = await begin(p, r);
  r.handler?.({ code: 'A', state });
  const t = await result;
  assert.equal(t.accessToken, 'acc_123456789');
  assert.equal(t.expiresAt, undefined);
});

test('cancelamento e timeout encerram o fluxo e liberam o receptor', async () => {
  const { p } = provider(() => jsonResponse(200, {}));
  const r1 = new FakeReceiver();
  const ctrl = new AbortController();
  const { result } = await begin(p, r1, ctrl.signal);
  ctrl.abort();
  await assert.rejects(result, (e: unknown) => e instanceof OAuthError && e.kind === 'cancelled');
  assert.equal(r1.disposed, true);

  const { p: fast } = provider(() => jsonResponse(200, {}), 30);
  const r2 = new FakeReceiver();
  await assert.rejects(fast.signIn({ receiver: r2, openUrl: async () => true }), (e: unknown) => e instanceof OAuthError && e.kind === 'timeout');
  assert.equal(r2.disposed, true);
});

test('falha ao abrir o receptor vira listen-failed', async () => {
  const { p } = provider(() => jsonResponse(200, {}));
  const r = new FakeReceiver();
  r.failListen = new Error('EADDRINUSE');
  await assert.rejects(p.signIn({ receiver: r, openUrl: async () => true }), (e: unknown) => e instanceof OAuthError && e.kind === 'listen-failed');
});

test('endpoints OAuth fora do domínio confiável são recusados na construção', () => {
  assert.throws(() => new VenixOAuthProvider({ clientId: 'x', authorizeUrl: 'https://evil.com/auth', tokenUrl: OAUTH_TOKEN_URL, timeoutMs: 1 }));
  assert.throws(() => new VenixOAuthProvider({ clientId: 'x', authorizeUrl: OAUTH_AUTHORIZE_URL, tokenUrl: 'http://api.venixcloud.com/t', timeoutMs: 1 }));
});

// ----------------------------------------------------------------- servidor loopback real

function get(port: number, path: string, headers: Record<string, string> = {}, method = 'GET'): Promise<{ status: number; body: string; headers: http.IncomingHttpHeaders }> {
  return new Promise((resolve, reject) => {
    const req = http.request({ host: '127.0.0.1', port, path, method, headers }, (res) => {
      let body = '';
      res.on('data', (c) => (body += c));
      res.on('end', () => resolve({ status: res.statusCode ?? 0, body, headers: res.headers }));
    });
    req.on('error', reject);
    req.end();
  });
}

test('servidor loopback: só 127.0.0.1, valida Host/método/caminho e repassa os parâmetros', async () => {
  const seen: CallbackParams[] = [];
  const rx = new LoopbackReceiver(0);
  await rx.listen((p) => {
    seen.push(p);
    return p['code'] ? { ok: true } : { ok: false, title: 'Ruim', message: 'sem código' };
  });
  const port = Number(new URL(rx.redirectUri).port);
  try {
    assert.ok(port > 0);
    assert.ok(rx.redirectUri.startsWith('http://127.0.0.1:'));

    const ok = await get(port, '/callback?code=abc&state=s1');
    assert.equal(ok.status, 200);
    assert.deepEqual(seen[0], { code: 'abc', state: 's1' });
    assert.match(String(ok.headers['content-security-policy']), /default-src 'none'/);
    assert.equal(ok.headers['cache-control'], 'no-store');
    assert.equal(ok.headers['referrer-policy'], 'no-referrer');

    assert.equal((await get(port, '/callback?x=1')).status, 400); // handler recusou
    assert.equal((await get(port, '/outro?code=1')).status, 404);
    assert.equal((await get(port, '/callback?code=1', {}, 'POST')).status, 405);
    const rebinding = await get(port, '/callback?code=zzz', { Host: 'evil.example.com' });
    assert.equal(rebinding.status, 400);
    assert.ok(!seen.some((p) => p['code'] === 'zzz'), 'Host forjado não pode chegar ao handler');
    assert.equal((await get(port, '/callback?code=1', { Host: `localhost:${port}` })).status, 200);
  } finally {
    rx.dispose();
  }
  await assert.rejects(get(port, '/callback'));
});

test('servidor loopback: porta ocupada → listen-failed', async () => {
  const a = new LoopbackReceiver(0);
  await a.listen(() => ({ ok: true }));
  const port = Number(new URL(a.redirectUri).port);
  const b = new LoopbackReceiver(port);
  try {
    await assert.rejects(b.listen(() => ({ ok: true })), (e: unknown) => e instanceof OAuthError && e.kind === 'listen-failed' && /em uso/.test(e.message));
  } finally {
    a.dispose();
    b.dispose();
  }
});

test('página de callback escapa HTML (sem XSS via error_description)', async () => {
  const rx = new LoopbackReceiver(0);
  await rx.listen((p) => ({ ok: false, title: 'Recusado', message: p['error_description'] ?? '' }));
  const port = Number(new URL(rx.redirectUri).port);
  try {
    const res = await get(port, `/callback?error_description=${encodeURIComponent('<script>alert(1)</script>')}`);
    assert.ok(!res.body.includes('<script>'));
    assert.ok(res.body.includes('&lt;script&gt;'));
  } finally {
    rx.dispose();
  }
  assert.equal(escapeHtml(`<a href="x">&'`), '&lt;a href=&quot;x&quot;&gt;&amp;&#39;');
});
