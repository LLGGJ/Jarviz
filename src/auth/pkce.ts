import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';

/** Verifier PKCE (RFC 7636): 32 bytes aleatórios em base64url = 43 caracteres. */
export function generateCodeVerifier(bytes = 32): string {
  return randomBytes(bytes).toString('base64url');
}

/** code_challenge com método S256. */
export function codeChallengeS256(verifier: string): string {
  return createHash('sha256').update(verifier, 'ascii').digest().toString('base64url');
}

/** Valor de `state` imprevisível, para proteção contra CSRF. */
export function generateState(): string {
  return randomBytes(24).toString('base64url');
}

/** Comparação em tempo constante. */
export function safeEqual(a: string, b: string): boolean {
  const ba = Buffer.from(a);
  const bb = Buffer.from(b);
  return ba.length === bb.length && timingSafeEqual(ba, bb);
}
