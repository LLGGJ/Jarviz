import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import { inflateRawSync } from 'node:zlib';
import { crc32, buildZip, ZipLimitError } from '../src/workspace/zip';
import { scanProject } from '../src/workspace/scanner';
import { detectProject, sanitizeAppName, suggestObjectName } from '../src/workspace/projectDetector';
import { readLink, writeLink } from '../src/workspace/link';
import { buildPreviewListing, summarizeBlocked } from '../src/workspace/preview';
import { checkAppName } from '../src/api/validators';
import { checkObjectName } from '../src/api/validators';

async function tmp(): Promise<string> {
  return fs.mkdtemp(path.join(os.tmpdir(), 'venix-test-'));
}
async function put(root: string, rel: string, content: string | Buffer): Promise<void> {
  const abs = path.join(root, rel);
  await fs.mkdir(path.dirname(abs), { recursive: true });
  await fs.writeFile(abs, content);
}

/** Leitor ZIP independente (via diretório central) para validar o que o escritor produziu. */
function readZip(buf: Buffer): Map<string, Buffer> {
  const eocd = buf.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  assert.ok(eocd >= 0, 'EOCD ausente');
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const out = new Map<string, Buffer>();
  for (let i = 0; i < count; i++) {
    assert.equal(buf.readUInt32LE(p), 0x02014b50);
    const method = buf.readUInt16LE(p + 10);
    const crc = buf.readUInt32LE(p + 16);
    const csize = buf.readUInt32LE(p + 20);
    const usize = buf.readUInt32LE(p + 24);
    const nlen = buf.readUInt16LE(p + 28);
    const local = buf.readUInt32LE(p + 42);
    const name = buf.subarray(p + 46, p + 46 + nlen).toString('utf8');
    const dataStart = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
    const raw = buf.subarray(dataStart, dataStart + csize);
    const data = method === 8 ? inflateRawSync(raw) : Buffer.from(raw);
    assert.equal(data.length, usize, `tamanho de ${name}`);
    assert.equal(crc32(data), crc, `crc de ${name}`);
    out.set(name, data);
    p += 46 + nlen;
  }
  return out;
}

test('crc32 bate com o valor conhecido de "123456789"', () => {
  assert.equal(crc32(Buffer.from('123456789')), 0xcbf43926);
});

test('zip: ida e volta com compressão, arquivo vazio, binário e nome UTF-8', async () => {
  const root = await tmp();
  await put(root, 'index.js', 'console.log("olá");\n'.repeat(200));
  await put(root, 'vazio.txt', '');
  await put(root, 'dir/ação.bin', Buffer.from(Array.from({ length: 256 }, (_, i) => i)));
  const scan = await scanProject(root, { exclude: [] });
  const zip = await buildZip(scan.files, { maxBytes: 10 * 1024 * 1024 });
  const entries = readZip(zip);
  assert.deepEqual([...entries.keys()].sort(), ['dir/ação.bin', 'index.js', 'vazio.txt']);
  assert.equal(entries.get('index.js')?.toString(), 'console.log("olá");\n'.repeat(200));
  assert.equal(entries.get('vazio.txt')?.length, 0);
  assert.equal(entries.get('dir/ação.bin')?.length, 256);
  assert.ok(zip.length < 3800, 'conteúdo repetitivo deve ser comprimido');
  await fs.writeFile(path.join(os.tmpdir(), 'venix-test-out.zip'), zip);
});

test('zip: respeita o limite de tamanho', async () => {
  const root = await tmp();
  await put(root, 'a.bin', Buffer.from(Array.from({ length: 5000 }, () => Math.floor(Math.random() * 256))));
  const scan = await scanProject(root, { exclude: [] });
  await assert.rejects(buildZip(scan.files, { maxBytes: 1000 }), ZipLimitError);
});

test('scanner: bloqueia segredos/.git/symlinks SEMPRE; aplica exclusões do usuário e .venixignore', async () => {
  const root = await tmp();
  await put(root, 'index.js', 'x');
  await put(root, 'src/app.js', 'x');
  await put(root, '.env', 'SEGREDO=1');
  await put(root, '.env.production', 'SEGREDO=2');
  await put(root, 'config/server.key', 'PRIVATE');
  await put(root, 'config/ok.json', '{}');
  await put(root, '.git/config', 'x');
  await put(root, '.ssh/id_ed25519', 'x');
  await put(root, 'node_modules/lib/index.js', 'x');
  await put(root, 'logs/app.log', 'x');
  await put(root, 'tmp-cache/a.js', 'x');
  await put(root, '.venix.json', '{"id":"abcdefgh"}');
  await put(root, '.venixignore', '# regras\ntmp-cache/\n');
  await fs.symlink('/etc/passwd', path.join(root, 'link-para-passwd'));

  const scan = await scanProject(root, { exclude: ['node_modules', '*.log'] });
  const included = scan.files.map((f) => f.rel).sort();
  assert.deepEqual(included, ['.venixignore', 'config/ok.json', 'index.js', 'src/app.js']);
  assert.ok(scan.blockedSecrets.includes('.env'));
  assert.ok(scan.blockedSecrets.includes('.env.production'));
  assert.ok(scan.blockedSecrets.includes('config/server.key'));
  assert.ok(scan.blockedSecrets.includes('.venix.json'));
  assert.ok(scan.blockedSecrets.some((s) => s.startsWith('.ssh')));
  assert.equal(scan.skippedSymlinks, 1);
  assert.ok(scan.excludedCount >= 3);
  // Nada secreto pode estar na lista de envio, mesmo com exclusões vazias:
  const none = await scanProject(root, { exclude: [] });
  for (const f of none.files) {
    assert.ok(!/^\.env|\.key$|\.git\/|\.ssh/.test(f.rel), `vazou: ${f.rel}`);
  }
});

test('scanner: .venixignore NÃO consegue reincluir um segredo (negação ignorada)', async () => {
  const root = await tmp();
  await put(root, '.env', 'X=1');
  await put(root, '.venixignore', '!.env\n');
  const scan = await scanProject(root, { exclude: [] });
  assert.ok(!scan.files.some((f) => f.rel === '.env'));
  assert.ok(scan.blockedSecrets.includes('.env'));
});

test('scanner: respeita limite de arquivos e cancelamento', async () => {
  const root = await tmp();
  for (let i = 0; i < 5; i++) await put(root, `f${i}.txt`, 'x');
  const scan = await scanProject(root, { exclude: [], maxFiles: 3 });
  assert.equal(scan.files.length, 3);
  assert.equal(scan.truncated, true);
  const ctrl = new AbortController();
  ctrl.abort();
  await assert.rejects(scanProject(root, { exclude: [], signal: ctrl.signal }));
});

test('preview: lista incluídos, bloqueados e excluídos', async () => {
  const root = await tmp();
  await put(root, 'a.js', 'x');
  await put(root, '.env', 'x');
  await put(root, 'b.log', 'x');
  const scan = await scanProject(root, { exclude: ['*.log'] });
  const text = buildPreviewListing(scan, 'minha-app');
  assert.match(text, /Destino: minha-app/);
  assert.match(text, /✔ a\.js/);
  assert.match(text, /✖ \.env/);
  assert.match(text, /– b\.log/);
  assert.match(summarizeBlocked(scan) ?? '', /\.env/);
});

test('detector de projeto: runtime, nome sugerido e comando de início', async () => {
  const root = await tmp();
  await put(root, 'package.json', JSON.stringify({ name: '@acme/minha-api', scripts: { start: 'node .' } }));
  const info = await detectProject(root);
  assert.equal(info.runtime, 'nodejs');
  assert.equal(info.suggestedName, 'minha-api');
  assert.equal(info.startCommandHint, 'npm start');
  assert.deepEqual(info.evidence, ['package.json']);

  const py = await tmp();
  await put(py, 'requirements.txt', 'flask');
  assert.equal((await detectProject(py)).runtime, 'python');
  const bun = await tmp();
  await put(bun, 'bun.lockb', 'x');
  await put(bun, 'package.json', '{}');
  assert.equal((await detectProject(bun)).runtime, 'bun');
  assert.equal((await detectProject(await tmp())).runtime, undefined);
  assert.deepEqual(await detectProject('/caminho/que/nao/existe'), { evidence: [] });
});

test('nomes sugeridos respeitam as regras documentadas', () => {
  assert.equal(checkAppName(sanitizeAppName('Meu App!! 2026') ?? ''), undefined);
  assert.equal(sanitizeAppName('ab'), undefined);
  assert.equal(checkObjectName(suggestObjectName('banner-topo.final.png')), undefined);
  assert.equal(checkObjectName(suggestObjectName('a.png')), undefined);
  assert.ok(suggestObjectName('x'.repeat(80) + '.png').length <= 32);
});

test('.venix.json: leitura compatível com a CLI, validação e permissão 0600', async () => {
  const root = await tmp();
  assert.equal(await readLink(root), undefined);
  await writeLink(root, { id: 'abcdef123456', name: 'api' });
  assert.deepEqual(await readLink(root), { id: 'abcdef123456', name: 'api' });
  const mode = (await fs.stat(path.join(root, '.venix.json'))).mode & 0o777;
  assert.equal(mode, 0o600);
  await put(root, '.venix.json', '{"id":"../../etc"}');
  assert.equal(await readLink(root), undefined);
  await put(root, '.venix.json', 'não é json');
  assert.equal(await readLink(root), undefined);
});
