import { test } from 'node:test';
import assert from 'node:assert/strict';
import { assertTrustedUrl, isSafeExternalUrl, normalizeBaseUrl } from '../src/security/trustedHosts';
import { isSensitiveKey, MASK, redactSecrets, sanitizeMessage } from '../src/security/redact';
import { isBlockedDirectory, isSecretFile } from '../src/security/secretFiles';
import { createIgnoreMatcher } from '../src/workspace/ignore';
import { Logger } from '../src/utils/logger';
import { buildDetailEntries, toPublicFields } from '../src/utils/details';
import { parseDetailsMessage, parseOverviewMessage } from '../src/views/messages';

test('URL-base: aceita o padrão e subdomínios; remove barra final', () => {
  assert.equal(normalizeBaseUrl('https://api.venixcloud.com/v1/'), 'https://api.venixcloud.com/v1');
  assert.equal(normalizeBaseUrl('https://venixcloud.com'), 'https://venixcloud.com');
  assert.equal(normalizeBaseUrl('https://staging.api.venixcloud.com/v1'), 'https://staging.api.venixcloud.com/v1');
});

test('URL-base: rejeita domínios arbitrários, lookalikes, HTTP, userinfo, porta e query', () => {
  for (const bad of [
    'https://evil.com/v1',
    'https://venixcloud.com.evil.com/v1',
    'https://evilvenixcloud.com/v1',
    'http://api.venixcloud.com/v1',
    'https://user:pass@api.venixcloud.com/v1',
    'https://api.venixcloud.com:8443/v1',
    'https://api.venixcloud.com/v1?x=1',
    'https://api.venixcloud.com/v1#frag',
    'not a url',
    'javascript:alert(1)',
  ]) {
    assert.throws(() => assertTrustedUrl(bad), /./, bad);
  }
});

test('URLs externas seguras: só http(s) sem credenciais', () => {
  assert.equal(isSafeExternalUrl('https://app.venixcloud.com'), true);
  assert.equal(isSafeExternalUrl('file:///etc/passwd'), false);
  assert.equal(isSafeExternalUrl('javascript:alert(1)'), false);
  assert.equal(isSafeExternalUrl('https://u:p@x.com'), false);
});

test('redactSecrets remove Bearer, JSON de token, query e segredos conhecidos', () => {
  const secret = 'tok_ABCDEFGH12345678';
  const text = `Authorization: Bearer ${secret} | {"access_token":"${secret}"} | /cb?code=XYZ123&state=abc | raw ${secret}`;
  const out = redactSecrets(text, [secret]);
  assert.ok(!out.includes(secret));
  assert.ok(!out.includes('XYZ123'));
  assert.match(out, /REDACTED/);
});

test('Logger nunca grava o token', () => {
  const lines: string[] = [];
  const secret = 'tok_ABCDEFGH12345678';
  const log = new Logger({ appendLine: (l) => lines.push(l) }, () => [secret]);
  log.info(`chamando com ${secret}`);
  log.error('falhou', new Error(`Bearer ${secret}`));
  assert.ok(lines.every((l) => !l.includes(secret)));
  assert.equal(lines.length, 2);
});

test('sanitizeMessage remove controles e limita tamanho', () => {
  assert.equal(sanitizeMessage('a\u0000b\nc'), 'a b c');
  assert.equal(sanitizeMessage('x'.repeat(500)).length, 301);
});

test('chaves sensíveis são detectadas', () => {
  for (const k of ['password', 'db_password', 'secret', 'accessToken', 'apiKey', 'privateKey', 'connectionString', 'uri', 'client-cert']) {
    assert.equal(isSensitiveKey(k), true, k);
  }
  for (const k of ['name', 'host', 'region', 'engine', 'status']) assert.equal(isSensitiveKey(k), false, k);
});

test('detalhes: sensíveis chegam mascarados ao webview e o valor real não vaza em toPublicFields', () => {
  const entries = buildDetailEntries({ name: 'meu-db', host: 'db.internal', password: 'S3nh@Real', nested: { token: 'tok_X', ok: 1 } }, ['name']);
  const pub = JSON.stringify(toPublicFields(entries));
  assert.ok(!pub.includes('S3nh@Real'));
  assert.ok(!pub.includes('tok_X'));
  assert.ok(pub.includes(MASK));
  assert.equal(entries[0]?.key, 'name');
  assert.equal(entries.find((e) => e.key === 'password')?.sensitive, true);
  assert.equal(entries.find((e) => e.key === 'nested.token')?.sensitive, true);
});

test('arquivos secretos e diretórios bloqueados', () => {
  for (const f of ['.env', '.env.production', 'cert.pem', 'sub/dir/server.key', 'id_rsa', '.npmrc', 'credentials.json', 'service-account-prod.json', '.venix.json', 'keystore.jks']) {
    assert.equal(isSecretFile(f), true, f);
  }
  for (const f of ['index.js', 'README.md', 'src/env.ts', 'package.json']) assert.equal(isSecretFile(f), false, f);
  assert.equal(isBlockedDirectory('.git'), true);
  assert.equal(isBlockedDirectory('.ssh'), true);
  assert.equal(isBlockedDirectory('src'), false);
});

test('ignore matcher: padrões estilo gitignore', () => {
  const m = createIgnoreMatcher(['node_modules', '*.log', '/dist/', 'build/', 'docs/**/*.tmp', '# comentário', '!keep.log']);
  assert.equal(m('node_modules', true), true);
  assert.equal(m('a/b/node_modules', true), true);
  assert.equal(m('node_modules/x/y.js', false), true);
  assert.equal(m('app.log', false), true);
  assert.equal(m('src/deep/app.log', false), true);
  assert.equal(m('dist', true), true);
  assert.equal(m('src/dist', true), false); // ancorado na raiz
  assert.equal(m('build', true), true);
  assert.equal(m('build', false), false); // "build/" só casa pastas
  assert.equal(m('docs/a/b/x.tmp', false), true);
  assert.equal(m('src/index.ts', false), false);
  assert.equal(m('keep.log', false), true); // negações são ignoradas de propósito
});

test('mensagens de webview: formato estrito', () => {
  assert.deepEqual(parseOverviewMessage({ type: 'action', action: 'refresh' }), { type: 'action', action: 'refresh' });
  assert.equal(parseOverviewMessage({ type: 'action', action: 'rm -rf' }), undefined);
  assert.equal(parseOverviewMessage('x'), undefined);
  assert.equal(parseOverviewMessage(null), undefined);
  const keys = new Set(['password', 'host']);
  assert.deepEqual(parseDetailsMessage({ type: 'reveal', key: 'password' }, keys), { type: 'reveal', key: 'password' });
  assert.equal(parseDetailsMessage({ type: 'reveal', key: 'inexistente' }, keys), undefined);
  assert.equal(parseDetailsMessage({ type: 'exec', key: 'host' }, keys), undefined);
  assert.equal(parseDetailsMessage({ type: 'copy', key: 42 }, keys), undefined);
});
