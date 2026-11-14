import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AuthenticationManager, validateApiToken, type SecretStore, type StateStore } from '../src/auth/AuthenticationManager';
import type { CallbackReceiver, TokenSet } from '../src/auth/OAuthProvider';
import { VenixOAuthProvider } from '../src/auth/OAuthProvider';
import { makeApiError } from '../src/api/errors';
import type { AccountInfo } from '../src/api/types';
import { SECRET_SESSION_KEY, STATE_ACCOUNT_KEY } from '../src/constants';

class MemSecrets implements SecretStore {
  map = new Map<string, string>();
  async get(k: string) { return this.map.get(k); }
  async store(k: string, v: string) { this.map.set(k, v); }
  async delete(k: string) { this.map.delete(k); }
}
class MemState implements StateStore {
  map = new Map<string, unknown>();
  get<T>(k: string) { return this.map.get(k) as T | undefined; }
  async update(k: string, v: unknown) { if (v === undefined) this.map.delete(k); else this.map.set(k, v); }
}

const TOKEN = 'tok_SESSION_0123456789';

function setup(opts: { tokens?: TokenSet; account?: () => Promise<AccountInfo>; now?: () => number } = {}) {
  const secrets = new MemSecrets();
  const state = new MemState();
  const oauth = { signIn: async () => opts.tokens ?? { accessToken: TOKEN } } as unknown as VenixOAuthProvider;
  const account = opts.account ?? (async () => ({ label: 'Ana', email: 'ana@x.com' }));
  const mgr = new AuthenticationManager(secrets, state, oauth, account, opts.now ?? (() => 1_000));
  const events: string[] = [];
  mgr.onDidChange((e) => events.push(e.reason));
  return { mgr, secrets, state, events };
}
const receiver = {} as CallbackReceiver;
const oauthArgs = { receiver, openUrl: async () => true };

test('OAuth: token vai SOMENTE para o SecretStorage; estado local guarda só o rótulo da conta', async () => {
  const { mgr, secrets, state, events } = setup({ tokens: { accessToken: TOKEN, expiresAt: 99_999 } });
  const info = await mgr.signInWithOAuth(oauthArgs);
  assert.equal(info.method, 'oauth');
  assert.equal(info.account.label, 'Ana');
  assert.equal(mgr.isSignedIn(), true);
  assert.equal(await mgr.getAccessToken(), TOKEN);
  assert.ok(secrets.map.get(SECRET_SESSION_KEY)?.includes(TOKEN));
  assert.ok(!JSON.stringify([...state.map.entries()]).includes(TOKEN), 'o token não pode ir para o estado comum');
  assert.deepEqual(events, ['signed-in']);
});

test('sessão sobrevive a reinício (initialize) e expira conforme expiresAt', async () => {
  let now = 1_000;
  const a = setup({ tokens: { accessToken: TOKEN, expiresAt: 5_000 }, now: () => now });
  await a.mgr.signInWithOAuth(oauthArgs);
  const restarted = new AuthenticationManager(a.secrets, a.state, {} as VenixOAuthProvider, async () => ({ label: 'x' }), () => now);
  await restarted.initialize();
  assert.equal(restarted.isSignedIn(), true);
  assert.equal(restarted.getSessionInfo()?.account.label, 'Ana');

  now = 6_000;
  const events: string[] = [];
  restarted.onDidChange((e) => events.push(e.reason));
  assert.equal(await restarted.getAccessToken(), undefined);
  assert.equal(restarted.isSignedIn(), false);
  assert.equal(a.secrets.map.has(SECRET_SESSION_KEY), false, 'sessão expirada é apagada do SecretStorage');
  assert.deepEqual(events, ['expired']);
});

test('initialize descarta sessão corrompida ou já vencida', async () => {
  const secrets = new MemSecrets();
  const state = new MemState();
  secrets.map.set(SECRET_SESSION_KEY, '{lixo');
  const m1 = new AuthenticationManager(secrets, state, {} as VenixOAuthProvider, async () => ({ label: 'x' }), () => 10);
  await m1.initialize();
  assert.equal(m1.isSignedIn(), false);
  assert.equal(secrets.map.has(SECRET_SESSION_KEY), false);

  secrets.map.set(SECRET_SESSION_KEY, JSON.stringify({ method: 'oauth', accessToken: TOKEN, expiresAt: 5, createdAt: 1 }));
  const m2 = new AuthenticationManager(secrets, state, {} as VenixOAuthProvider, async () => ({ label: 'x' }), () => 10);
  await m2.initialize();
  assert.equal(m2.isSignedIn(), false);
  assert.equal(secrets.map.has(SECRET_SESSION_KEY), false);
});

test('token recusado pela API (401) NÃO é armazenado', async () => {
  const { mgr, secrets, events } = setup({ account: async () => { throw makeApiError('unauthorized', { status: 401 }); } });
  await assert.rejects(mgr.signInWithOAuth(oauthArgs), /recusou o token/);
  assert.equal(secrets.map.size, 0);
  assert.equal(mgr.isSignedIn(), false);
  assert.deepEqual(events, []);
});

test('falha transitória de rede na validação não derruba o login (rótulo genérico)', async () => {
  const { mgr } = setup({ account: async () => { throw makeApiError('network'); } });
  const info = await mgr.signInWithOAuth(oauthArgs);
  assert.equal(info.account.label, 'Conta Venix Cloud');
  assert.equal(mgr.isSignedIn(), true);
});

test('token de API: validação de formato, método "apiToken" (nunca rotulado como OAuth)', async () => {
  const { mgr, secrets } = setup();
  await assert.rejects(mgr.signInWithApiToken('curto'));
  await assert.rejects(mgr.signInWithApiToken('tem espaço no meio do token'));
  assert.equal(secrets.map.size, 0);
  const info = await mgr.signInWithApiToken(`  ${TOKEN}\n`);
  assert.equal(info.method, 'apiToken');
  assert.equal(await mgr.getAccessToken(), TOKEN);
  assert.equal(validateApiToken('x'.repeat(5000)), 'O token é longo demais.');
});

test('token de API recusado (401) não é salvo', async () => {
  const { mgr, secrets } = setup({ account: async () => { throw makeApiError('unauthorized', { status: 401 }); } });
  await assert.rejects(mgr.signInWithApiToken(TOKEN), /Token de API recusado/);
  assert.equal(secrets.map.size, 0);
});

test('signOut apaga segredo e dados locais; handleUnauthorized expira a sessão', async () => {
  const { mgr, secrets, state, events } = setup();
  await mgr.signInWithOAuth(oauthArgs);
  assert.ok(state.map.has(STATE_ACCOUNT_KEY));
  await mgr.signOut();
  assert.equal(secrets.map.size, 0);
  assert.equal(state.map.size, 0);
  assert.equal(mgr.isSignedIn(), false);
  assert.equal(await mgr.getAccessToken(), undefined);

  await mgr.signInWithOAuth(oauthArgs);
  await mgr.handleUnauthorized();
  assert.equal(secrets.map.size, 0);
  assert.deepEqual(events, ['signed-in', 'signed-out', 'signed-in', 'expired']);
  await mgr.signOut(); // sem sessão: não dispara evento
  assert.equal(events.length, 4);
});

test('não permite dois logins simultâneos', async () => {
  let release!: () => void;
  const gate = new Promise<void>((r) => (release = r));
  const { mgr } = setup({ account: async () => { await gate; return { label: 'x' }; } });
  const first = mgr.signInWithApiToken(TOKEN);
  await assert.rejects(mgr.signInWithApiToken(TOKEN), /em andamento/);
  release();
  await first;
});

test('knownSecrets expõe o token ativo apenas para o logger redigir', async () => {
  const { mgr } = setup();
  assert.deepEqual(mgr.knownSecrets(), []);
  await mgr.signInWithOAuth(oauthArgs);
  assert.deepEqual(mgr.knownSecrets(), [TOKEN]);
});
