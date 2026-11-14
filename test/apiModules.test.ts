import { test } from 'node:test';
import assert from 'node:assert/strict';
import { VenixApi } from '../src/api';
import { VenixApiClient } from '../src/api/VenixApiClient';
import { VenixApiError } from '../src/api/errors';
import { parseAccount } from '../src/api/modules/account';
import { formatStreamLine, mergeStatus, parseApplications } from '../src/api/modules/applications';
import { parseEggs, parseGameServers } from '../src/api/modules/games';
import { parseDatabases } from '../src/api/modules/databases';
import { parseObjects } from '../src/api/modules/storage';
import { parseSnapshots } from '../src/api/modules/snapshots';
import { extractList, mapStatus, resolveWebUrl } from '../src/api/normalize';
import { fakeFetch, jsonResponse, noSleep, TOKEN } from './helpers';

function api(responder: Parameters<typeof fakeFetch>[0] = () => jsonResponse(200, { status: 'success' })) {
  const f = fakeFetch(responder);
  const client = new VenixApiClient({ baseUrl: 'https://api.venixcloud.com/v1', getToken: () => TOKEN, fetch: f.fetch, sleep: noSleep });
  return { api: new VenixApi(client), calls: f.calls };
}

async function rejects(p: Promise<unknown>, kind = 'invalid-input'): Promise<void> {
  await assert.rejects(p, (e: unknown) => e instanceof VenixApiError && e.kind === kind);
}

test('apps: ação, deploy, update e delete usam método, caminho e corpo documentados', async () => {
  const { api: a, calls } = api();
  await a.apps.action('app1', 'RESTART');
  await a.apps.action('app1', 'START', 'r2');
  await a.apps.triggerDeploy('app1');
  await a.apps.update('app1', { max_ram: 1024, startCommand: 'npm start' });
  await a.apps.remove('app1');
  assert.deepEqual(calls.map((c) => `${c.method} ${c.url.replace('https://api.venixcloud.com/v1', '')}`), [
    'POST /apps/app1/action',
    'POST /apps/app1/action?replica=r2',
    'POST /apps/app1/deploy/trigger',
    'PATCH /apps/app1',
    'DELETE /apps/app1',
  ]);
  assert.equal(calls[0]?.body, '{"action":"RESTART"}');
  assert.equal(calls[3]?.body, '{"max_ram":1024,"startCommand":"npm start"}');
});

test('apps.create: valida e envia multipart com os campos documentados', async () => {
  const { api: a, calls } = api(() => jsonResponse(200, { status: 'success', response: { id: 'abc12345', name: 'x' } }));
  const out = await a.apps.create({ appName: 'minha-app', runtime: 'nodejs', ram: 512, isWeb: true, subdomain: 'minha-app', startCommand: 'npm start', file: { filename: 'p.zip', data: new Uint8Array([1, 2, 3]) } });
  assert.equal(out['id'], 'abc12345');
  const form = calls[0]?.body as FormData;
  assert.equal(form.get('appName'), 'minha-app');
  assert.equal(form.get('runtime'), 'nodejs');
  assert.equal(form.get('ram'), '512');
  assert.equal(form.get('isWeb'), 'true');
  assert.equal(form.get('subdomain'), 'minha-app');
  assert.equal((form.get('file') as File).name, 'p.zip');
  assert.equal(calls[0]?.url, 'https://api.venixcloud.com/v1/apps/create');
});

test('apps.create: rejeita entradas inválidas SEM chamar a rede', async () => {
  const { api: a, calls } = api();
  const base = { appName: 'ok-app', runtime: 'nodejs' as const, ram: 512, isWeb: false };
  await rejects(a.apps.create({ ...base, appName: 'a' }));
  await rejects(a.apps.create({ ...base, appName: 'nome inválido!' }));
  await rejects(a.apps.create({ ...base, ram: 64 }));
  await rejects(a.apps.create({ ...base, ram: 99999 }));
  await rejects(a.apps.create({ ...base, runtime: 'cobol' as never }));
  await rejects(a.apps.create({ ...base, isWeb: true }));
  await rejects(a.apps.create({ ...base, isWeb: true, subdomain: 'Inválido_!' }));
  await rejects(a.apps.create({ ...base, startCommand: 'a\nb' }));
  assert.equal(calls.length, 0);
});

test('IDs de recurso não permitem injeção de caminho', async () => {
  const { api: a, calls } = api();
  for (const id of ['../admin', 'a/b', 'a b', '', 'x?y=1', '..']) await rejects(a.apps.remove(id));
  assert.equal(calls.length, 0);
});

test('arquivos: upload usa form {path,file}; extract/delete usam JSON; caminhos com .. são recusados', async () => {
  const { api: a, calls } = api();
  await a.apps.uploadFile('app1', '/__venix_update.zip', { filename: 'update.zip', data: new Uint8Array([1]) });
  await a.apps.extract('app1', '/__venix_update.zip');
  await a.apps.deleteFile('app1', '/__venix_update.zip');
  const form = calls[0]?.body as FormData;
  assert.equal(form.get('path'), '/__venix_update.zip');
  assert.equal(calls[1]?.url.endsWith('/apps/app1/files/extract'), true);
  assert.equal(calls[1]?.body, '{"filePath":"/__venix_update.zip"}');
  assert.equal(calls[2]?.method, 'DELETE');
  assert.equal(calls[2]?.body, '{"path":"/__venix_update.zip"}');
  await rejects(a.apps.uploadFile('app1', '/a/../../etc/passwd', { filename: 'x', data: new Uint8Array([1]) }));
});

test('games: create/command/delete; validação de RAM, região e comando', async () => {
  const { api: a, calls } = api();
  await a.games.create({ name: 'Survival', game: 'minecraft_paper', ram: 2048, region: 'BR' });
  await a.games.sendCommand('g1', 'say olá');
  await a.games.remove('g1');
  assert.equal(calls[0]?.url.endsWith('/instances/games/create'), true);
  assert.equal(calls[0]?.body, '{"name":"Survival","game":"minecraft_paper","ram":2048,"region":"BR"}');
  assert.equal(calls[1]?.body, '{"cmd":"say olá"}');
  assert.equal(calls[2]?.method, 'DELETE');
  await rejects(a.games.create({ name: 'x', game: 'rust', ram: 100, region: 'BR' }));
  await rejects(a.games.create({ name: 'x', game: 'rust', ram: 2048, region: 'US' as never }));
  await rejects(a.games.sendCommand('g1', '   '));
});

test('databases: create envia os campos documentados; certificados vêm como bytes', async () => {
  const { api: a, calls } = api((c) => (c.url.endsWith('/zip') ? new Response(new Uint8Array([80, 75]), { status: 200 }) : jsonResponse(200, { response: { id: 'db1' } })));
  await a.databases.create({ name: 'meu-db', engine: 'PostgreSQL', ram: 1024, region: 'CA', installManager: false });
  assert.equal(calls[0]?.body, '{"name":"meu-db","engine":"PostgreSQL","ram":1024,"region":"CA","installManager":false}');
  assert.deepEqual([...(await a.databases.downloadCertificates('db1'))], [80, 75]);
  assert.equal(calls[1]?.url.endsWith('/instances/databases/db1/credentials/zip'), true);
  await rejects(a.databases.create({ name: 'MeuDB', engine: 'PostgreSQL', ram: 1024, region: 'BR', installManager: false }));
  await rejects(a.databases.create({ name: 'meu-db', engine: 'PostgreSQL', ram: 256, region: 'BR', installManager: false }));
  await rejects(a.databases.create({ name: 'meu-db', engine: 'Oracle' as never, ram: 1024, region: 'BR', installManager: false }));
});

test('storage: upload valida nome/tamanho e devolve url+size; query só com o que foi informado', async () => {
  const { api: a, calls } = api(() => jsonResponse(200, { status: 'success', url: 'https://cdn.venixcloud.com/x.png', size: 2048 }));
  const big = new Uint8Array(2048);
  const out = await a.storage.upload({ name: 'banner_topo', prefix: 'img/site', expireDays: 30, file: { filename: 'b.png', data: big } });
  assert.deepEqual(out, { url: 'https://cdn.venixcloud.com/x.png', size: 2048 });
  assert.equal(calls[0]?.url, 'https://api.venixcloud.com/v1/objects?name=banner_topo&prefix=img%2Fsite&expire=30');
  await a.storage.upload({ name: 'sem_extras', file: { filename: 'b.png', data: big } });
  assert.equal(calls[1]?.url, 'https://api.venixcloud.com/v1/objects?name=sem_extras');
  await rejects(a.storage.upload({ name: 'ab', file: { filename: 'b.png', data: big } }));
  await rejects(a.storage.upload({ name: 'com-hifen', file: { filename: 'b.png', data: big } }));
  await rejects(a.storage.upload({ name: 'pequeno', file: { filename: 'b.png', data: new Uint8Array(100) } })); // < 1 KB
  await rejects(a.storage.upload({ name: 'prefixo_ruim', prefix: '../x', file: { filename: 'b.png', data: big } }));
  await rejects(a.storage.upload({ name: 'expira_mal', expireDays: 0, file: { filename: 'b.png', data: big } }));
  assert.equal(calls.length, 2);
});

test('storage: resposta sem url é inválida', async () => {
  const { api: a } = api(() => jsonResponse(200, { status: 'success' }));
  await rejects(a.storage.upload({ name: 'sem_url', file: { filename: 'b.png', data: new Uint8Array(2048) } }), 'invalid-response');
});

test('snapshots: list com paginação, create, download (URL pré-assinada) e delete', async () => {
  const { api: a, calls } = api((c) => (c.url.includes('/download') ? jsonResponse(200, { response: { url: 'https://r2.example/signed?x=1' } }) : jsonResponse(200, { response: [] })));
  await a.snapshots.list(2, 10);
  await a.snapshots.create('res1', 'backup-1');
  assert.equal(await a.snapshots.downloadUrl('s1'), 'https://r2.example/signed?x=1');
  await a.snapshots.remove('s1');
  assert.equal(calls[0]?.url, 'https://api.venixcloud.com/v1/snapshots?page=2&limit=10');
  assert.equal(calls[1]?.body, '{"resourceId":"res1","name":"backup-1"}');
  assert.equal(calls[3]?.method, 'DELETE');
  await rejects(a.snapshots.create('res1', '   '));
});

// ------------------------------------------------------------------ parsers tolerantes

test('extractList aceita array, {response:[]} e {games:[]}', () => {
  assert.deepEqual(extractList([1]), [1]);
  assert.deepEqual(extractList({ response: [2] }), [2]);
  assert.deepEqual(extractList({ games: [3] }, ['games']), [3]);
  assert.deepEqual(extractList({ data: { items: [4] } }), [4]);
  assert.deepEqual(extractList('x'), []);
});

test('status: mapeia variações sem inventar', () => {
  assert.equal(mapStatus('RUNNING'), 'online');
  assert.equal(mapStatus('stopped'), 'offline');
  assert.equal(mapStatus('Restarting'), 'starting');
  assert.equal(mapStatus('crashed'), 'error');
  assert.equal(mapStatus(undefined), 'unknown');
  assert.equal(mapStatus('qualquer-coisa'), 'unknown');
});

test('parseApplications: lê /me.applications e mescla /apps/status por id ou nome', () => {
  const me = { user: { name: 'Ana' }, applications: [{ id: 'a1', name: 'api', max_ram: 512, runtime: 'nodejs', url: 'api.venixcloud.com' }, { id: 'a2', appName: 'bot' }, { name: 'sem-id' }] };
  const apps = parseApplications(me, { a1: 'ONLINE', bot: { status: 'OFFLINE' } });
  assert.equal(apps.length, 2);
  assert.equal(apps[0]?.status, 'online');
  assert.equal(apps[0]?.ramMb, 512);
  assert.equal(apps[0]?.url, 'https://api.venixcloud.com/');
  assert.equal(apps[1]?.name, 'bot');
  assert.equal(apps[1]?.status, 'offline');
  assert.equal(parseApplications({}).length, 0);
});

test('mergeStatus aceita lista de objetos', () => {
  const apps = [{ id: 'a1', name: 'x' }];
  mergeStatus(apps, [{ id: 'a1', state: 'running' }]);
  assert.equal((apps[0] as Record<string, unknown>)['state'], 'running');
});

test('parseAccount: usa name, depois email, depois rótulo genérico', () => {
  assert.equal(parseAccount({ user: { name: 'Ana', email: 'a@x.com', plan: 'Pro' } }).label, 'Ana');
  assert.equal(parseAccount({ email: 'a@x.com' }).label, 'a@x.com');
  assert.equal(parseAccount({}).label, 'Conta Venix Cloud');
});

test('parsers de games/databases/objetos/snapshots/eggs: leitura tolerante, nada inventado', () => {
  const g = parseGameServers({ response: [{ id: 'g1', name: 'Srv', game: 'rust', ip: '1.2.3.4', port: 28015, status: 'running' }] });
  assert.equal(g[0]?.address, '1.2.3.4:28015');
  assert.equal(g[0]?.status, 'online');
  assert.equal(g[0]?.region, undefined);
  const d = parseDatabases([{ uuid: 'd1', name: 'db', engine: 'MySQL' }]);
  assert.equal(d[0]?.id, 'd1');
  assert.equal(d[0]?.status, 'unknown');
  const o = parseObjects({ objects: [{ url: 'https://cdn/x/banner.png', size: 10 }] });
  assert.equal(o[0]?.name, 'banner.png');
  const s = parseSnapshots({ snapshots: [{ id: 's1', name: 'b', created_at: '2026-01-01' }] });
  assert.equal(s[0]?.createdAt, '2026-01-01');
  assert.deepEqual(parseEggs(['rust', 'samp']).map((e) => e.id), ['rust', 'samp']);
  assert.deepEqual(parseEggs({ response: { rust: { name: 'Rust' } } }).map((e) => e.id), ['rust']);
  assert.deepEqual(parseEggs(null), []);
});

test('resolveWebUrl: só http(s) com domínio; rejeita lixo', () => {
  assert.equal(resolveWebUrl({ url: 'app.venixcloud.com' }), 'https://app.venixcloud.com/');
  assert.equal(resolveWebUrl({ url: 'localhost' }), undefined);
  assert.equal(resolveWebUrl({ url: 'javascript:alert(1)' }), undefined);
  assert.equal(resolveWebUrl({}), undefined);
});

test('formatStreamLine: log JSON, texto puro, status e métrica', () => {
  assert.deepEqual(formatStreamLine('{"log":"olá\\n","time":"10:00"}'), { kind: 'log', text: '10:00 olá' });
  assert.deepEqual(formatStreamLine('texto solto'), { kind: 'log', text: 'texto solto' });
  assert.equal(formatStreamLine('"RUNNING"')?.kind, 'status');
  assert.equal(formatStreamLine('{"cpu":12.34,"memory":{"usage":104857600,"limit":536870912}}')?.text, '[métricas] CPU 12.3% • RAM 100/512 MB');
  assert.equal(formatStreamLine('   '), undefined);
});
