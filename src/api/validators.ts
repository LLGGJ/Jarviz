import { LIMITS } from '../constants';
import { makeApiError } from './errors';

/** Regras extraídas de api-docs.html (v1.4). */
export const RUNTIMES = ['nodejs', 'python', 'bun', 'go', 'java', 'ruby', 'static', 'php'] as const;
export type Runtime = (typeof RUNTIMES)[number];
export const REGIONS = ['BR', 'CA'] as const;
export type Region = (typeof REGIONS)[number];
export const DB_ENGINES = ['PostgreSQL', 'MySQL', 'mariadb', 'MongoDB', 'Redis'] as const;
export type DbEngine = (typeof DB_ENGINES)[number];
/** Eggs citados na documentação; a lista real vem de GET /instances/games/eggs. */
export const DOCUMENTED_EGGS = ['minecraft_paper', 'minecraft_forge', 'samp', 'mta', 'csgo', 'rust'] as const;

export const APP_NAME_RE = /^[a-zA-Z0-9_-]{3,50}$/;
export const DB_NAME_RE = /^[a-z0-9-]{3,}$/;
export const OBJECT_NAME_RE = /^[a-zA-Z0-9_]{3,32}$/;
/** Rótulo DNS (necessário para *.venixcloud.com); a regra exata do provedor não é documentada. */
export const SUBDOMAIN_RE = /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/;
const SAFE_ID_RE = /^[A-Za-z0-9._-]{1,128}$/;
const PREFIX_RE = /^[A-Za-z0-9_\-/.]{1,100}$/;

export const APP_RAM = { min: 128, max: 32768 } as const;
export const GAME_RAM = { min: 256, max: 65536 } as const;
export const DB_RAM = { min: 512 } as const;

type Check = string | undefined;

export function checkAppName(v: string): Check {
  return APP_NAME_RE.test(v) ? undefined : 'Use 3 a 50 caracteres: letras, números, "_" ou "-".';
}
export function checkAppRam(v: number): Check {
  return Number.isInteger(v) && v >= APP_RAM.min && v <= APP_RAM.max
    ? undefined
    : `A RAM deve ser um inteiro entre ${APP_RAM.min} e ${APP_RAM.max} MB.`;
}
export function checkSubdomain(v: string): Check {
  return SUBDOMAIN_RE.test(v) ? undefined : 'Use letras minúsculas, números e "-" (1 a 63 caracteres).';
}
export function checkStartCommand(v: string): Check {
  // eslint-disable-next-line no-control-regex
  return v.length <= 500 && !/[\u0000-\u001f]/.test(v) ? undefined : 'Comando inválido (máx. 500 caracteres, uma linha).';
}
export function checkGameName(v: string): Check {
  return v.trim().length >= 1 && v.length <= 60 ? undefined : 'O nome deve ter de 1 a 60 caracteres.';
}
export function checkGameRam(v: number): Check {
  return Number.isInteger(v) && v >= GAME_RAM.min && v <= GAME_RAM.max
    ? undefined
    : `A RAM deve ser um inteiro entre ${GAME_RAM.min} e ${GAME_RAM.max} MB.`;
}
export function checkDbName(v: string): Check {
  return DB_NAME_RE.test(v) ? undefined : 'Mínimo 3 caracteres: letras minúsculas, números e hifens.';
}
export function checkDbRam(v: number): Check {
  return Number.isInteger(v) && v >= DB_RAM.min ? undefined : `A RAM deve ser um inteiro de, no mínimo, ${DB_RAM.min} MB.`;
}
export function checkObjectName(v: string): Check {
  return OBJECT_NAME_RE.test(v) ? undefined : 'Use 3 a 32 caracteres: letras, números e "_".';
}
export function checkObjectPrefix(v: string): Check {
  return PREFIX_RE.test(v) && !v.split('/').includes('..') ? undefined : 'Prefixo inválido (letras, números, "_", "-", "/", ".").';
}
export function checkExpireDays(v: number): Check {
  return Number.isInteger(v) && v >= 1 && v <= 365 ? undefined : 'A expiração deve ser de 1 a 365 dias.';
}
export function checkCdnSize(bytes: number): Check {
  if (bytes < LIMITS.cdnMinBytes) return 'O arquivo precisa ter no mínimo 1 KB.';
  if (bytes > LIMITS.cdnMaxBytes) return 'O arquivo excede o limite de 100 MB do CDN.';
  return undefined;
}
export function checkConsoleCommand(v: string): Check {
  // eslint-disable-next-line no-control-regex
  return v.trim().length > 0 && v.length <= 1000 && !/[\u0000-\u001f]/.test(v) ? undefined : 'Comando vazio ou inválido (uma linha, máx. 1000 caracteres).';
}
export function checkRemotePath(v: string): Check {
  // eslint-disable-next-line no-control-regex
  if (!v || v.length > 500 || /[\u0000-\u001f]/.test(v)) return 'Caminho remoto inválido.';
  if (v.split(/[\\/]/).includes('..')) return 'Caminhos com ".." não são permitidos.';
  return undefined;
}

export function assertValid(message: Check): void {
  if (message) {
    throw makeApiError('invalid-input', { message });
  }
}

/** IDs vão direto para o caminho da URL: aceitar apenas caracteres seguros evita injeção de caminho. */
export function safeId(id: string): string {
  if (!SAFE_ID_RE.test(id) || id === '.' || id === '..') {
    throw makeApiError('invalid-input', { message: 'Identificador de recurso inválido.' });
  }
  return encodeURIComponent(id);
}
