import * as path from 'node:path';

/**
 * Arquivos que NUNCA entram em pacotes de publicação, independentemente das
 * configurações do usuário ou de `.venixignore`.
 */
const SECRET_BASENAME_PATTERNS: RegExp[] = [
  /^\.env(\..*)?$/i,
  /^.*\.(pem|key|p12|pfx|jks|keystore|ppk|crt|cer|der)$/i,
  /^id_(rsa|dsa|ecdsa|ed25519)(\.pub)?$/i,
  /^\.(npmrc|netrc|pypirc|git-credentials|htpasswd)$/i,
  /^credentials(\..*)?$/i,
  /^secrets?(\..*)?$/i,
  /^service-account.*\.json$/i,
  /^\.venix\.json$/i,
];

const BLOCKED_DIRS = new Set(['.git', '.venix', '.ssh', '.aws', '.gnupg', '.kube']);

export function isBlockedDirectory(name: string): boolean {
  return BLOCKED_DIRS.has(name);
}

export function isSecretFile(relPath: string): boolean {
  const base = path.posix.basename(relPath.replace(/\\/g, '/'));
  return SECRET_BASENAME_PATTERNS.some((p) => p.test(base));
}
