import { test, before, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
import * as mock from './mocks/vscode';
import { fakeFetch, jsonResponse, TOKEN, type RecordedCall } from './helpers';
import { activate } from '../src/extension';
import type { Services } from '../src/services';
import { VenixTreeProvider, type TreeNode } from '../src/providers/VenixTreeProvider';
import { buildOverviewState } from '../src/views/OverviewView';
import { readSettings } from '../src/utils/config';
import { SECRET_SESSION_KEY } from '../src/constants';

const m = mock as unknown as {
  pkg: { contributes: { commands: Array<{ command: string; title: string }>; menus: Record<string, Array<{ command: string }>>; configuration: { properties: Record<string, unknown> } } };
  answer(fn: string, ...v: unknown[]): void;
  callsOf(fn: string): unknown[][];
  reset(): void;
  script: { config: Map<string, unknown> };
  panels: Array<{ webview: { html: string; posted: unknown[]; emit(m: unknown): Promise<void> }; disposed: boolean }>;
  providers: Map<string, { resolveWebviewView(v: unknown): void }>;
  authProviders: Map<string, { label: string; provider: { getSessions(): Promise<Array<{ accessToken: string }>>; removeSession(): Promise<void> } }>;
  clipboardState: { text: string };
  makeContext(): ReturnType<typeof mock.makeContext>;
};

// ------------------------------------------------------------------ rede falsa
const APPS = [{ id: 'app-1234', name: 'minha-api', max_ram: 512, runtime: 'nodejs', status: 'ONLINE' }];
const net: { calls: RecordedCall[]; routes: Map<string, (c: RecordedCall) => Response> } = { calls: [], routes: new Map() };

function installNetwork(): void {
  const f = fakeFetch((call) => {
    net.calls.push(call);
    const url = new URL(call.url);
    const key = `${call.method} ${url.pathname.replace(/^\/v1/, '')}`;
    const route = net.routes.get(key);
    if (route) return route(call);
    return jsonResponse(200, { status: 'success', response: {} });
  });
  globalThis.fetch = f.fetch;
}
function defaultRoutes(): void {
  net.routes.clear();
  net.calls = [];
  net.routes.set('GET /me', () => jsonResponse(200, { status: 'success', response: { user: { name: 'Ana', email: 'ana@x.com' }, applications: APPS } }));
  net.routes.set('GET /apps/status', () => jsonResponse(200, { status: 'success', response: {} }));
  net.routes.set('GET /instances/games', () => jsonResponse(200, { response: [{ id: 'game-1', name: 'Survival', game: 'rust', status: 'running' }] }));
  net.routes.set('GET /instances/databases', () => jsonResponse(200, { response: [{ id: 'db-1', name: 'meu-db', engine: 'PostgreSQL', host: 'db.internal', password: 'S3nh@Real!', status: 'running' }] }));
  net.routes.set('GET /objects', () => jsonResponse(200, { response: [{ name: 'banner', url: 'https://cdn.venixcloud.com/banner.png', size: 2048 }] }));
  net.routes.set('GET /snapshots', () => jsonResponse(200, { response: [{ id: 'snap-1', name: 'backup-1' }] }));
}
const deletes = (): RecordedCall[] => net.calls.filter((c) => c.method === 'DELETE');

let services: Services;
let ctx: ReturnType<typeof mock.makeContext>;

before(async () => {
  installNetwork();
  ctx = m.makeContext();
  const out = await activate(ctx as unknown as vscode.ExtensionContext);
  services = out.services;
});

let seq = 0;
beforeEach(async () => {
  m.reset();
  // Um subdomínio confiável novo por teste => cliente HTTP novo (o limitador de 55 req/min é por cliente).
  m.script.config.set('venixCloud.apiBaseUrl', `https://t${++seq}.venixcloud.com/v1`);
  defaultRoutes();
  await services.auth.signOut();
  services.store.clear();
  net.calls = [];
});

async function signIn(): Promise<void> {
  await services.auth.signInWithApiToken(TOKEN);
  await services.store.refreshAll();
  net.calls = [];
}
const run = (id: string, ...args: unknown[]): PromiseLike<unknown> => vscode.commands.executeCommand(id, ...args);
const node = (kind: TreeNode['kind'], item: unknown): TreeNode => ({ kind, item }) as TreeNode;

// ------------------------------------------------------------------ registro de comandos / manifesto

test('todos os comandos do package.json estão registrados — e nenhum a mais', () => {
  const declared = m.pkg.contributes.commands.map((c) => c.command).sort();
  const registered = (vscode.commands as unknown as { registered(): string[] }).registered().filter((c) => c.startsWith('venixCloud.')).sort();
  assert.deepEqual(registered, declared);
  for (const required of ['signIn', 'signOut', 'refresh', 'showAccount', 'openWebPanel', 'checkConnection', 'openSettings']) {
    assert.ok(declared.includes(`venixCloud.${required}`), required);
  }
});

test('menus só referenciam comandos declarados; todos os comandos têm título e categoria', () => {
  const declared = new Set(m.pkg.contributes.commands.map((c) => c.command));
  for (const [menu, items] of Object.entries(m.pkg.contributes.menus)) {
    for (const item of items) assert.ok(declared.has(item.command), `${menu}: ${item.command}`);
  }
  for (const c of m.pkg.contributes.commands as Array<{ title: string; category?: string }>) {
    assert.ok(c.title && c.category === 'Venix Cloud');
  }
});

test('ativação registra view de árvore, webview, URI handler e provedor de autenticação', () => {
  assert.ok(m.providers.has('venixCloud.overview'));
  assert.equal(m.authProviders.get('venixcloud')?.label, 'Venix Cloud');
  assert.ok(ctx.subscriptions.length > 20);
});

test('configurações declaradas existem no package.json e têm padrões seguros', () => {
  const props = m.pkg.contributes.configuration.properties as Record<string, { default: unknown; scope?: string }>;
  for (const key of ['apiBaseUrl', 'autoRefresh', 'refreshInterval', 'confirmDestructiveActions']) assert.ok(props[`venixCloud.${key}`], key);
  assert.equal(props['venixCloud.apiBaseUrl']?.default, 'https://api.venixcloud.com/v1');
  assert.equal(props['venixCloud.apiBaseUrl']?.scope, 'machine', 'workspace não pode redirecionar credenciais');
  assert.equal(props['venixCloud.confirmDestructiveActions']?.default, true);
  assert.equal(props['venixCloud.autoRefresh']?.default, false);
});

test('código-fonte não contém credenciais embutidas', async () => {
  const files: string[] = [];
  const walk = async (dir: string): Promise<void> => {
    for (const e of await fs.readdir(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) await walk(p);
      else if (p.endsWith('.ts') || p.endsWith('.js') || p.endsWith('.json')) files.push(p);
    }
  };
  await walk(path.join(__dirname, '..', 'src'));
  await walk(path.join(__dirname, '..', 'media'));
  for (const f of files) {
    const text = await fs.readFile(f, 'utf8');
    assert.ok(!/client_secret\s*[:=]\s*['"][^'"]+/i.test(text), `client_secret em ${f}`);
    assert.ok(!/Bearer\s+[A-Za-z0-9._-]{16,}/.test(text), `Bearer fixo em ${f}`);
    assert.ok(!/(access_token|api[_-]?key|password)\s*[:=]\s*['"][A-Za-z0-9._-]{12,}['"]/i.test(text), `segredo em ${f}`);
  }
});

// ------------------------------------------------------------------ autenticação e sessão

test('token de API: guardado em SecretStorage, nunca em globalState/workspaceState/settings', async () => {
  m.answer('showInputBox', TOKEN);
  await run('venixCloud.signInWithApiToken');
  assert.equal(services.auth.isSignedIn(), true);
  assert.ok(ctx.secrets.map.get(SECRET_SESSION_KEY)?.includes(TOKEN));
  assert.ok(!JSON.stringify([...ctx.globalState.map]).includes(TOKEN));
  assert.ok(!JSON.stringify([...ctx.workspaceState.map]).includes(TOKEN));
  assert.equal(services.auth.getSessionInfo()?.method, 'apiToken');
  assert.deepEqual(m.callsOf('executeCommand').filter((c) => c[0] === 'setContext').at(-1)?.slice(1), ['venixCloud.signedIn', true]);
});

test('sair apaga sessão, segredos, estado local e dados da árvore', async () => {
  await signIn();
  assert.ok(services.store.snapshot.apps.items.length > 0);
  await run('venixCloud.signOut');
  assert.equal(services.auth.isSignedIn(), false);
  assert.equal(ctx.secrets.map.size, 0);
  assert.equal(services.store.snapshot.apps.items.length, 0);
});

test('provedor de autenticação do VS Code reflete a sessão e permite sair', async () => {
  const entry = m.authProviders.get('venixcloud');
  assert.deepEqual(await entry?.provider.getSessions(), []);
  await signIn();
  const sessions = await entry?.provider.getSessions();
  assert.equal(sessions?.length, 1);
  assert.equal(sessions?.[0]?.accessToken, TOKEN);
  await entry?.provider.removeSession();
  assert.equal(services.auth.isSignedIn(), false);
});

test('401 da API expira a sessão, apaga o segredo e avisa o usuário (uma vez)', async () => {
  await signIn();
  net.routes.set('GET /instances/games', () => jsonResponse(401, { message: 'revogado' }));
  await services.store.refresh('games');
  await new Promise((r) => setImmediate(r));
  assert.equal(services.auth.isSignedIn(), false);
  assert.equal(ctx.secrets.map.size, 0);
  assert.equal(m.callsOf('showWarningMessage').filter((c) => /expirou/.test(String(c[0]))).length, 1);
});

test('URL-base maliciosa nas configurações é rejeitada: token só vai para api.venixcloud.com', async () => {
  m.script.config.set('venixCloud.apiBaseUrl', 'https://evil.example.com/v1');
  const s = readSettings();
  assert.equal(s.apiBaseUrl, 'https://api.venixcloud.com/v1');
  assert.match(s.apiBaseUrlError ?? '', /não é confiável/);
  await signIn();
  await services.store.refreshAll();
  assert.ok(net.calls.length > 0);
  for (const c of net.calls) assert.equal(new URL(c.url).hostname, 'api.venixcloud.com');
  assert.ok(!net.calls.some((c) => c.url.includes('evil')));
});

test('verificar conexão: sucesso e falha com mensagem clara (sem stack)', async () => {
  await signIn();
  await run('venixCloud.checkConnection');
  assert.match(String(m.callsOf('showInformationMessage').at(-1)?.[0]), /conexão OK/);
  net.routes.set('GET /me', () => jsonResponse(503, {}));
  await run('venixCloud.checkConnection');
  const err = String(m.callsOf('showErrorMessage').at(-1)?.[0]);
  assert.match(err, /indisponível/);
  assert.ok(!/at \w+.*\.ts/.test(err));
});

test('comandos exigem login: sem sessão, oferecem entrar e não chamam a API', async () => {
  await run('venixCloud.appCreate');
  await run('venixCloud.appStart');
  assert.equal(net.calls.length, 0);
  assert.ok(m.callsOf('showWarningMessage').some((c) => /não está conectado/.test(String(c[0]))));
});

// ------------------------------------------------------------------ operações destrutivas

test('excluir aplicação: cancelar ou digitar o nome errado NÃO chama a API; nome correto exclui', async () => {
  await signIn();
  const app = node('app', services.store.snapshot.apps.items[0]);
  m.answer('showInputBox', undefined);
  await run('venixCloud.appDelete', app);
  m.answer('showInputBox', 'outro-nome');
  await run('venixCloud.appDelete', app);
  assert.equal(deletes().length, 0);
  m.answer('showInputBox', 'minha-api');
  await run('venixCloud.appDelete', app);
  assert.equal(deletes().length, 1);
  assert.equal(new URL(deletes()[0]!.url).pathname, '/v1/apps/app-1234');
});

test('excluir banco, servidor e snapshot: mesma confirmação por nome', async () => {
  await signIn();
  const { databases, games, snapshots } = services.store.snapshot;
  const cases: Array<[string, TreeNode, string, string]> = [
    ['venixCloud.dbDelete', node('database', databases.items[0]), 'meu-db', '/v1/instances/databases/db-1'],
    ['venixCloud.gameDelete', node('game', games.items[0]), 'Survival', '/v1/instances/games/game-1'],
    ['venixCloud.snapshotDelete', node('snapshot', snapshots.items[0]), 'backup-1', '/v1/snapshots/snap-1'],
  ];
  for (const [cmd, n, name, urlPath] of cases) {
    m.answer('showInputBox', undefined);
    await run(cmd, n);
    assert.equal(deletes().length, 0, `${cmd} sem confirmação`);
    m.answer('showInputBox', name);
    await run(cmd, n);
    assert.equal(new URL(deletes().at(-1)!.url).pathname, urlPath);
    net.calls = [];
  }
});

test('confirmDestructiveActions=false dispensa o prompt (escolha explícita do usuário)', async () => {
  await signIn();
  m.script.config.set('venixCloud.confirmDestructiveActions', false);
  await run('venixCloud.appDelete', node('app', services.store.snapshot.apps.items[0]));
  assert.equal(m.callsOf('showInputBox').length, 0);
  assert.equal(deletes().length, 1);
});

test('deploy exige confirmação modal; parar/iniciar/reiniciar chamam /action', async () => {
  await signIn();
  const app = node('app', services.store.snapshot.apps.items[0]);
  m.answer('showWarningMessage', undefined);
  await run('venixCloud.appDeploy', app);
  assert.ok(!net.calls.some((c) => c.url.includes('/deploy/trigger')));
  m.answer('showWarningMessage', 'Disparar deploy');
  await run('venixCloud.appDeploy', app);
  assert.ok(net.calls.some((c) => c.url.endsWith('/apps/app-1234/deploy/trigger') && c.method === 'POST'));
  await run('venixCloud.appRestart', app);
  const act = net.calls.find((c) => c.url.endsWith('/apps/app-1234/action'));
  assert.equal(act?.body, '{"action":"RESTART"}');
});

// ------------------------------------------------------------------ árvore

test('árvore: vazia sem login; categorias na ordem esperada com login', async () => {
  const tree = new VenixTreeProvider(services.store, services.auth);
  assert.deepEqual(tree.getChildren(), []);
  await signIn();
  assert.deepEqual(tree.getChildren().map((n) => (n.kind === 'category' ? n.id : n.kind)), ['account', 'apps', 'games', 'databases', 'storage', 'snapshots', 'settings']);
});

test('árvore: itens de app com ícone de estado, contextValue e descrição vindos da API', async () => {
  await signIn();
  const tree = new VenixTreeProvider(services.store, services.auth, () => 'app-1234');
  const apps = tree.getChildren({ kind: 'category', id: 'apps' });
  assert.equal(apps.length, 1);
  const item = tree.getTreeItem(apps[0]!);
  assert.equal(item.label, 'minha-api');
  assert.equal(item.contextValue, 'venix.app.online');
  assert.match(String(item.description), /online · 512 MB · ↔ projeto atual/);
  assert.equal((item.iconPath as { color?: { id: string } }).color?.id, 'testing.iconPassed');
  assert.equal(item.command?.command, 'venixCloud.showDetails');
});

test('árvore: estados vazio, carregando, erro e idle', async () => {
  const tree = new VenixTreeProvider(services.store, services.auth);
  await signIn();
  net.routes.set('GET /instances/games', () => jsonResponse(200, { response: [] }));
  net.routes.set('GET /instances/databases', () => jsonResponse(403, { message: 'sem permissão' }));
  await services.store.refreshAll();
  const empty = tree.getChildren({ kind: 'category', id: 'games' });
  assert.match(String(tree.getTreeItem(empty[0]!).label), /Nenhum servidor/);
  const failed = tree.getChildren({ kind: 'category', id: 'databases' });
  const failedItem = tree.getTreeItem(failed[0]!);
  assert.match(String(failedItem.label), /negada|permissão/i);
  assert.equal(failedItem.command?.command, 'venixCloud.refresh');
  assert.equal(tree.getTreeItem({ kind: 'category', id: 'databases' }).description, 'erro');

  services.store.clear();
  const idle = tree.getChildren({ kind: 'category', id: 'apps' });
  assert.match(String(tree.getTreeItem(idle[0]!).label), /Atualizar/);
});

test('falha em uma categoria não impede as demais de carregar', async () => {
  await signIn();
  net.routes.set('GET /objects', () => jsonResponse(500, {}));
  await services.store.refreshAll();
  const s = services.store.snapshot;
  assert.equal(s.storage.state, 'error');
  assert.equal(s.apps.state, 'loaded');
  assert.equal(s.connection, 'ok');
});

test('refresh simultâneo é coalescido em uma única rodada de requisições', async () => {
  await signIn();
  await Promise.all([services.store.refreshAll(), services.store.refreshAll(), services.store.refreshAll()]);
  assert.equal(net.calls.filter((c) => c.url.endsWith('/me')).length, 1);
});

// ------------------------------------------------------------------ webviews

test('detalhes do banco: HTML com CSP restritiva e sem a senha; reveal/copy só para chaves válidas', async () => {
  await signIn();
  await run('venixCloud.showDetails', node('database', services.store.snapshot.databases.items[0]));
  const panel = m.panels.at(-1)!;
  assert.match(panel.webview.html, /Content-Security-Policy/);
  assert.match(panel.webview.html, /script-src 'nonce-/);
  assert.ok(!/unsafe-inline|unsafe-eval/.test(panel.webview.html));
  assert.ok(!/<script(?![^>]*\bsrc=)[^>]*>/.test(panel.webview.html), 'sem script inline');
  assert.ok(!panel.webview.html.includes('S3nh@Real!'));

  await panel.webview.emit({ type: 'ready' });
  const init = panel.webview.posted.at(-1) as { type: string; fields: Array<{ key: string; display: string; sensitive: boolean }> };
  assert.equal(init.type, 'init');
  assert.ok(!JSON.stringify(init).includes('S3nh@Real!'));
  assert.equal(init.fields.find((f) => f.key === 'password')?.sensitive, true);

  await panel.webview.emit({ type: 'reveal', key: 'password' });
  assert.deepEqual(panel.webview.posted.at(-1), { type: 'value', key: 'password', value: 'S3nh@Real!' });
  const before = panel.webview.posted.length;
  await panel.webview.emit({ type: 'reveal', key: '__proto__' });
  await panel.webview.emit({ type: 'reveal', key: 'naoexiste' });
  await panel.webview.emit({ type: 'eval', key: 'password' });
  await panel.webview.emit('lixo');
  assert.equal(panel.webview.posted.length, before, 'mensagens inválidas são descartadas');

  await panel.webview.emit({ type: 'copy', key: 'password' });
  assert.equal(m.clipboardState.text, 'S3nh@Real!');
});

test('visão geral: estado sem token, com contagens reais e avisos de erro', async () => {
  await signIn();
  net.routes.set('GET /snapshots', () => jsonResponse(500, {}));
  await services.store.refreshAll();
  const state = buildOverviewState(services.store, services.auth);
  assert.equal(state.signedIn, true);
  assert.equal(state.account?.label, 'Ana');
  assert.equal(state.counts.apps.count, 1);
  assert.equal(state.counts.games.count, 1);
  assert.equal(state.counts.snapshots.state, 'error');
  assert.deepEqual(state.appStatus, { online: 1, offline: 0, other: 0 });
  assert.ok(state.warnings.some((w) => w.startsWith('Snapshots')));
  assert.ok(!JSON.stringify(state).includes(TOKEN));

  const provider = m.providers.get('venixCloud.overview')!;
  const panel = (vscode.window.createWebviewPanel as unknown as (a: string, b: string) => (typeof m.panels)[number])('x', 'y');
  provider.resolveWebviewView({ webview: panel.webview, onDidDispose: () => new mock.Disposable() });
  assert.match(panel.webview.html, /script-src 'nonce-/);
  await panel.webview.emit({ type: 'ready' });
  const pushed = JSON.stringify(panel.webview.posted.at(-1));
  assert.match(pushed, /"type":"state"/);
  assert.ok(!pushed.includes(TOKEN));
  await panel.webview.emit({ type: 'action', action: 'format-c' }); // ação fora da lista é ignorada
});

// ------------------------------------------------------------------ publicação

test('publicar: pré-visualização, .env nunca vai no ZIP, e o fluxo upload → extract → limpeza → restart', async () => {
  await signIn();
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'venix-pub-'));
  await fs.writeFile(path.join(root, 'index.js'), 'console.log(1)');
  await fs.writeFile(path.join(root, '.env'), 'SEGREDO=abc');
  await fs.mkdir(path.join(root, 'node_modules'));
  await fs.writeFile(path.join(root, 'node_modules', 'x.js'), 'x');
  (vscode.workspace as unknown as { workspaceFolders: unknown }).workspaceFolders = [{ uri: vscode.Uri.file(root), name: 'proj', index: 0 }];
  try {
    const app = services.store.snapshot.apps.items[0];
    m.answer('showQuickPick', (items: Array<{ label: string }>) => items.find((i) => i.label.includes('Enviar')));
    m.answer('showInformationMessage', undefined); // não vincular
    await run('venixCloud.publishWorkspace', node('app', app));

    // O refresh da árvore após publicar é assíncrono; o fluxo de publicação são as 4 primeiras chamadas.
    const order = net.calls.slice(0, 4).map((c) => `${c.method} ${new URL(c.url).pathname.replace('/v1', '')}`);
    assert.deepEqual(order, [
      'POST /apps/app-1234/files/upload',
      'POST /apps/app-1234/files/extract',
      'DELETE /apps/app-1234/files',
      'POST /apps/app-1234/action',
    ]);
    const form = net.calls[0]!.body as FormData;
    assert.equal(form.get('path'), '/__venix_update.zip');
    const zip = Buffer.from(await (form.get('file') as Blob).arrayBuffer());
    assert.ok(zip.includes(Buffer.from('index.js')));
    assert.ok(!zip.includes(Buffer.from('.env')), '.env não pode estar no pacote');
    assert.ok(!zip.includes(Buffer.from('node_modules')));
    assert.equal(net.calls[3]!.body, '{"action":"RESTART"}');
    const preview = m.callsOf('showQuickPick')[0]?.[0] as Array<{ label: string; detail?: string }>;
    assert.match(preview[0]!.detail ?? '', /\.env/, 'a pré-visualização informa o que foi bloqueado');
  } finally {
    (vscode.workspace as unknown as { workspaceFolders: unknown }).workspaceFolders = undefined;
  }
});

test('publicar cancelando na pré-visualização não envia nada', async () => {
  await signIn();
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'venix-pub-'));
  await fs.writeFile(path.join(root, 'index.js'), 'x');
  (vscode.workspace as unknown as { workspaceFolders: unknown }).workspaceFolders = [{ uri: vscode.Uri.file(root), name: 'proj', index: 0 }];
  try {
    m.answer('showQuickPick', (items: Array<{ label: string }>) => items.find((i) => i.label.includes('Cancelar')));
    await run('venixCloud.publishWorkspace', node('app', services.store.snapshot.apps.items[0]));
    assert.equal(net.calls.length, 0);
  } finally {
    (vscode.workspace as unknown as { workspaceFolders: unknown }).workspaceFolders = undefined;
  }
});

test('workspace não confiável: publicação é bloqueada', async () => {
  await signIn();
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'venix-pub-'));
  await fs.writeFile(path.join(root, 'index.js'), 'x');
  const ws = vscode.workspace as unknown as { workspaceFolders: unknown; isTrusted: boolean };
  ws.workspaceFolders = [{ uri: vscode.Uri.file(root), name: 'proj', index: 0 }];
  ws.isTrusted = false;
  try {
    await run('venixCloud.publishWorkspace', node('app', services.store.snapshot.apps.items[0]));
    assert.equal(net.calls.length, 0);
    assert.ok(m.callsOf('showWarningMessage').some((c) => /confie/.test(String(c[0]))));
  } finally {
    ws.isTrusted = true;
    ws.workspaceFolders = undefined;
  }
});

test('upload de arquivo secreto para o CDN exige confirmação explícita', async () => {
  await signIn();
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'venix-cdn-'));
  const file = path.join(dir, '.env');
  await fs.writeFile(file, 'A=1'.padEnd(2048, 'x'));
  m.answer('showWarningMessage', undefined); // recusa
  await run('venixCloud.storageUpload', vscode.Uri.file(file));
  assert.equal(net.calls.filter((c) => c.method === 'POST').length, 0);
  assert.equal(m.callsOf('showInputBox').length, 0);
});

test('CDN: valida tamanho mínimo antes de qualquer pergunta ou envio', async () => {
  await signIn();
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'venix-cdn-'));
  const file = path.join(dir, 'pequeno.png');
  await fs.writeFile(file, 'x');
  await run('venixCloud.storageUpload', vscode.Uri.file(file));
  assert.match(String(m.callsOf('showErrorMessage').at(-1)?.[0]), /1 KB/);
  assert.equal(net.calls.length, 0);
});

test('copiar URL de objeto e ID de recurso usam a área de transferência', async () => {
  await signIn();
  await run('venixCloud.storageCopyUrl', node('object', services.store.snapshot.storage.items[0]));
  assert.equal(m.clipboardState.text, 'https://cdn.venixcloud.com/banner.png');
  await run('venixCloud.copyId', node('app', services.store.snapshot.apps.items[0]));
  assert.equal(m.clipboardState.text, 'app-1234');
});
