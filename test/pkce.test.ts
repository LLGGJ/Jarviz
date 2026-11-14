import { test } from 'node:test';
import assert from 'node:assert/strict';
import { codeChallengeS256, generateCodeVerifier, generateState, safeEqual } from '../src/auth/pkce';

test('S256 bate com o vetor de teste da RFC 7636 (Apêndice B)', () => {
  assert.equal(codeChallengeS256('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'), 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
});

test('verifier tem 43 caracteres base64url e não se repete', () => {
  const a = generateCodeVerifier();
  const b = generateCodeVerifier();
  assert.match(a, /^[A-Za-z0-9_-]{43}$/);
  assert.notEqual(a, b);
});

test('state é imprevisível e seguro para URL', () => {
  const s = generateState();
  assert.match(s, /^[A-Za-z0-9_-]{32}$/);
  assert.notEqual(s, generateState());
});

test('safeEqual compara em tempo constante e rejeita tamanhos diferentes', () => {
  assert.equal(safeEqual('abc', 'abc'), true);
  assert.equal(safeEqual('abc', 'abd'), false);
  assert.equal(safeEqual('abc', 'abcd'), false);
});
