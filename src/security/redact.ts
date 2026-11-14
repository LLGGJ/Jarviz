/** Chaves cujo valor deve ficar oculto por padrão. */
const SENSITIVE_KEY =
  /(pass(word|wd)?|pwd|secret|token|api[-_]?key|private|credential|cert|key$|auth|connection|conn(ection)?[-_]?str|dsn|uri)/i;

export const MASK = '••••••••';

export function isSensitiveKey(key: string): boolean {
  return SENSITIVE_KEY.test(key);
}

const PATTERNS: Array<[RegExp, string]> = [
  [/Bearer\s+[A-Za-z0-9._~+/=-]+/gi, 'Bearer [REDACTED]'],
  [
    /("?(?:access_token|refresh_token|code_verifier|client_secret|password|authorization)"?\s*[:=]\s*)"?[^"\s,&}]+"?/gi,
    '$1"[REDACTED]"',
  ],
  [/([?&](?:code|state|access_token|token|code_verifier)=)[^&\s"']+/gi, '$1[REDACTED]'],
];

/** Remove tokens e parâmetros sensíveis de textos antes de registrá-los. */
export function redactSecrets(text: string, knownSecrets: readonly string[] = []): string {
  let out = text;
  for (const secret of knownSecrets) {
    if (secret && secret.length >= 8) {
      out = out.split(secret).join('[REDACTED]');
    }
  }
  for (const [pattern, replacement] of PATTERNS) {
    out = out.replace(pattern, replacement);
  }
  return out;
}

/** Normaliza mensagens vindas da rede: sem caracteres de controle e com tamanho limitado. */
export function sanitizeMessage(text: string, max = 300): string {
  // eslint-disable-next-line no-control-regex
  const clean = text.replace(/[\u0000-\u001f\u007f]+/g, ' ').replace(/\s+/g, ' ').trim();
  return clean.length > max ? `${clean.slice(0, max)}…` : clean;
}
