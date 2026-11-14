import type { RawRecord, ResourceStatus } from './types';

/**
 * A documentação v1.4 não descreve o schema das respostas de listagem.
 * Estes helpers fazem leitura TOLERANTE (várias chaves candidatas, espelhando a
 * CLI oficial) e nunca inventam valores: o que não vier da API fica `undefined`.
 */

export function isRecord(v: unknown): v is RawRecord {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

export function pickString(rec: RawRecord, keys: readonly string[]): string | undefined {
  for (const key of keys) {
    const v = rec[key];
    if (typeof v === 'string' && v.trim() !== '') return v;
    if (typeof v === 'number' && Number.isFinite(v)) return String(v);
  }
  return undefined;
}

export function pickNumber(rec: RawRecord, keys: readonly string[]): number | undefined {
  for (const key of keys) {
    const v = rec[key];
    if (typeof v === 'number' && Number.isFinite(v)) return v;
    if (typeof v === 'string') {
      const n = Number(v.trim().replace(/%$/, ''));
      if (v.trim() !== '' && Number.isFinite(n)) return n;
    }
  }
  return undefined;
}

const LIST_KEYS = ['response', 'data', 'items', 'results', 'games', 'servers', 'instances', 'databases', 'objects', 'files', 'snapshots', 'eggs', 'applications'];

/** Extrai uma lista de registros de payloads como `[...]`, `{response:[...]}` ou `{games:[...]}`. */
export function extractList(payload: unknown, extraKeys: readonly string[] = []): unknown[] {
  if (Array.isArray(payload)) return payload;
  if (!isRecord(payload)) return [];
  for (const key of [...extraKeys, ...LIST_KEYS]) {
    const v = payload[key];
    if (Array.isArray(v)) return v;
    if (isRecord(v)) {
      const inner = extractList(v, extraKeys);
      if (inner.length > 0) return inner;
    }
  }
  return [];
}

export function extractRecords(payload: unknown, extraKeys: readonly string[] = []): RawRecord[] {
  return extractList(payload, extraKeys).filter(isRecord);
}

export const ID_KEYS = ['id', 'uuid', 'appId', 'app_id', 'instanceId', 'instance_id', '_id'] as const;
export const NAME_KEYS = ['name', 'appName', 'displayName', 'display_name', 'title', 'identifier'] as const;
export const STATUS_KEYS = ['status', 'state', 'app_status', 'appStatus', 'container_status', 'instance_status', 'instanceStatus'] as const;
export const BOOL_STATUS_KEYS = ['running', 'is_running', 'isRunning', 'online', 'is_online', 'isOnline', 'active'] as const;

/** Lê o status bruto: texto, booleano ou objeto aninhado (como a CLI oficial). */
export function readStatusRaw(rec: RawRecord): string | undefined {
  for (const key of STATUS_KEYS) {
    const v = rec[key];
    if (typeof v === 'string' && v !== '') return v;
    if (typeof v === 'boolean') return v ? 'online' : 'offline';
    if (isRecord(v)) {
      const inner = pickString(v, ['status', 'state', 'name']);
      if (inner) return inner;
    }
  }
  for (const key of BOOL_STATUS_KEYS) {
    const v = rec[key];
    if (typeof v === 'boolean') return v ? 'online' : 'offline';
  }
  return undefined;
}

export function mapStatus(raw: string | undefined): ResourceStatus {
  switch ((raw ?? '').trim().toUpperCase()) {
    case 'ONLINE':
    case 'RUNNING':
    case 'ACTIVE':
    case 'STARTED':
    case 'UP':
    case 'HEALTHY':
    case 'READY':
      return 'online';
    case 'OFFLINE':
    case 'STOPPED':
    case 'DOWN':
    case 'EXITED':
    case 'PAUSED':
    case 'SUSPENDED':
      return 'offline';
    case 'STARTING':
    case 'DEPLOYING':
    case 'BUILDING':
    case 'RESTARTING':
    case 'CREATING':
    case 'PROVISIONING':
      return 'starting';
    case 'STOPPING':
      return 'stopping';
    case 'ERROR':
    case 'FAILED':
    case 'CRASHED':
      return 'error';
    default:
      return 'unknown';
  }
}

export function readId(rec: RawRecord): string | undefined {
  return pickString(rec, ID_KEYS);
}

export function readName(rec: RawRecord): string | undefined {
  return pickString(rec, NAME_KEYS);
}

/** Devolve a RAM em MB quando o valor parece estar em MB (valores enormes são tratados como bytes). */
export function readRamMb(rec: RawRecord, keys: readonly string[]): number | undefined {
  const v = pickNumber(rec, keys);
  if (v === undefined || v < 0) return undefined;
  return v > 1 << 20 ? Math.round(v / (1 << 20)) : v;
}

/** Monta um endereço web a partir dos campos que a API informar (espelha `webURL` da CLI). */
export function resolveWebUrl(rec: RawRecord): string | undefined {
  let raw = pickString(rec, ['url', 'appUrl', 'app_url', 'public_url', 'publicUrl', 'web_url', 'webUrl', 'dashboard_url', 'domain', 'custom_domain', 'customDomain']);
  if (!raw) return undefined;
  if (!raw.includes('://')) raw = `https://${raw}`;
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    return undefined;
  }
  if (!url.hostname.includes('.') || (url.protocol !== 'https:' && url.protocol !== 'http:')) return undefined;
  const route = pickString(rec, ['route', 'base_path', 'basePath', 'web_path']);
  if (route && (url.pathname === '' || url.pathname === '/')) {
    url.pathname = `/${route.replace(/^\/+/, '')}`;
  }
  return url.toString();
}
