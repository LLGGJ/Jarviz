import type { VenixHttp } from '../VenixApiClient';
import { toBlob } from '../blob';
import {
  extractRecords,
  isRecord,
  mapStatus,
  pickString,
  readId,
  readName,
  readRamMb,
  readStatusRaw,
  resolveWebUrl,
} from '../normalize';
import type { AppSummary, FileUpload, RawRecord } from '../types';
import {
  assertValid,
  checkAppName,
  checkAppRam,
  checkRemotePath,
  checkStartCommand,
  checkSubdomain,
  RUNTIMES,
  safeId,
  type Runtime,
} from '../validators';
import { makeApiError } from '../errors';

export type AppAction = 'START' | 'STOP' | 'RESTART';

export interface CreateAppInput {
  appName: string;
  runtime: Runtime;
  ram: number;
  isWeb: boolean;
  subdomain?: string;
  startCommand?: string;
  file?: FileUpload;
}

export interface UpdateAppInput {
  max_ram?: number;
  startCommand?: string;
  runtime?: Runtime;
}

export class ApplicationsApi {
  constructor(private readonly http: VenixHttp) {}

  /** GET /apps/status — usado pela CLI oficial; opcional (falhas são toleradas pelo chamador). */
  async status(signal?: AbortSignal): Promise<unknown> {
    return this.http.json('GET', '/apps/status', { signal });
  }

  /** POST /apps/:id/action  {action}  [?replica=] */
  async action(id: string, action: AppAction, replica?: string): Promise<void> {
    await this.http.json('POST', `/apps/${safeId(id)}/action`, { json: { action }, query: { replica } });
  }

  /** POST /apps/create (multipart/form-data) */
  async create(input: CreateAppInput, signal?: AbortSignal): Promise<RawRecord> {
    assertValid(checkAppName(input.appName));
    if (!RUNTIMES.includes(input.runtime)) assertValid('Runtime inválido.');
    assertValid(checkAppRam(input.ram));
    if (input.isWeb) {
      assertValid(input.subdomain ? checkSubdomain(input.subdomain) : 'Informe um subdomínio para aplicações web.');
    }
    if (input.startCommand) assertValid(checkStartCommand(input.startCommand));

    const form = new FormData();
    form.append('appName', input.appName);
    form.append('runtime', input.runtime);
    form.append('ram', String(input.ram));
    form.append('isWeb', input.isWeb ? 'true' : 'false');
    if (input.isWeb && input.subdomain) form.append('subdomain', input.subdomain);
    if (input.startCommand) form.append('startCommand', input.startCommand);
    if (input.file) form.append('file', toBlob(input.file.data), input.file.filename);
    const res = await this.http.json<unknown>('POST', '/apps/create', { form, signal, timeoutMs: 10 * 60_000 });
    return isRecord(res) ? res : {};
  }

  /** DELETE /apps/:id — exclusão permanente. */
  async remove(id: string): Promise<void> {
    await this.http.json('DELETE', `/apps/${safeId(id)}`);
  }

  /** POST /apps/:id/deploy/trigger — deploy a partir do repositório GitHub vinculado. */
  async triggerDeploy(id: string): Promise<void> {
    await this.http.json('POST', `/apps/${safeId(id)}/deploy/trigger`);
  }

  /** PATCH /apps/:id */
  async update(id: string, input: UpdateAppInput): Promise<void> {
    if (input.max_ram === undefined && input.startCommand === undefined && input.runtime === undefined) {
      throw makeApiError('invalid-input', { message: 'Nada para atualizar.' });
    }
    if (input.max_ram !== undefined) assertValid(checkAppRam(input.max_ram));
    if (input.startCommand !== undefined) assertValid(checkStartCommand(input.startCommand));
    if (input.runtime !== undefined && !RUNTIMES.includes(input.runtime)) assertValid('Runtime inválido.');
    await this.http.json('PATCH', `/apps/${safeId(id)}`, { json: input });
  }

  /** POST /apps/:id/files/upload (form: file, path) */
  async uploadFile(id: string, remotePath: string, file: FileUpload, signal?: AbortSignal): Promise<void> {
    assertValid(checkRemotePath(remotePath));
    const form = new FormData();
    form.append('path', remotePath);
    form.append('file', toBlob(file.data), file.filename);
    await this.http.json('POST', `/apps/${safeId(id)}/files/upload`, { form, signal, timeoutMs: 10 * 60_000 });
  }

  /** POST /apps/:id/files/extract  {filePath} */
  async extract(id: string, filePath: string, signal?: AbortSignal): Promise<void> {
    assertValid(checkRemotePath(filePath));
    await this.http.json('POST', `/apps/${safeId(id)}/files/extract`, { json: { filePath }, signal, timeoutMs: 5 * 60_000 });
  }

  /** DELETE /apps/:id/files  {path} */
  async deleteFile(id: string, path: string): Promise<void> {
    assertValid(checkRemotePath(path));
    await this.http.json('DELETE', `/apps/${safeId(id)}/files`, { json: { path } });
  }
}

/** Une `GET /me` (lista `applications`) com o payload de `GET /apps/status`, quando existir. */
export function parseApplications(me: RawRecord, statusPayload?: unknown): AppSummary[] {
  const apps = extractRecords(me['applications'] ?? []).map((rec) => ({ ...rec }));
  if (statusPayload !== undefined) mergeStatus(apps, statusPayload);
  const out: AppSummary[] = [];
  for (const rec of apps) {
    const id = readId(rec);
    if (!id) continue;
    const statusRaw = readStatusRaw(rec);
    out.push({
      id,
      name: readName(rec) ?? id,
      status: mapStatus(statusRaw),
      statusRaw,
      runtime: pickString(rec, ['runtime', 'language']),
      ramMb: readRamMb(rec, ['max_ram', 'ram', 'memory']),
      url: resolveWebUrl(rec),
      raw: rec,
    });
  }
  return out;
}

function collectStatus(value: unknown, byKey: Map<string, RawRecord>, depth: number): void {
  if (Array.isArray(value)) {
    for (const item of value) {
      if (!isRecord(item)) continue;
      const id = readId(item);
      if (id) byKey.set(id, item);
      const name = readName(item);
      if (name) byKey.set(name, item);
    }
    return;
  }
  if (!isRecord(value)) return;
  for (const [key, item] of Object.entries(value)) {
    if (typeof item === 'string') byKey.set(key, { status: item });
    else if (typeof item === 'boolean') byKey.set(key, { running: item });
    else if (isRecord(item)) {
      if (readStatusRaw(item) !== undefined) byKey.set(key, item);
      else if (depth < 2) collectStatus(item, byKey, depth + 1);
    } else if (Array.isArray(item) && depth < 2) collectStatus(item, byKey, depth + 1);
  }
}

export function mergeStatus(apps: RawRecord[], statusPayload: unknown): void {
  const byKey = new Map<string, RawRecord>();
  collectStatus(statusPayload, byKey, 0);
  for (const app of apps) {
    const id = readId(app);
    const name = readName(app);
    const extra = (id ? byKey.get(id) : undefined) ?? (name ? byKey.get(name) : undefined);
    if (!extra) continue;
    for (const [k, v] of Object.entries(extra)) {
      if (v !== null && v !== undefined) app[k] = v;
    }
  }
}

/** Converte uma linha SSE de `/instances/stream/:id` em texto legível (log, status ou métrica). */
export function formatStreamLine(raw: string): { kind: 'log' | 'status' | 'metric'; text: string } | undefined {
  const trimmed = raw.trim();
  if (!trimmed) return undefined;
  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    return { kind: 'log', text: raw };
  }
  if (typeof parsed === 'string') return { kind: 'status', text: `[status] ${parsed}` };
  if (!isRecord(parsed)) return { kind: 'log', text: raw };
  if ('cpu' in parsed) {
    const parts: string[] = [];
    if (typeof parsed['cpu'] === 'number') parts.push(`CPU ${parsed['cpu'].toFixed(1)}%`);
    const mem = parsed['memory'];
    if (isRecord(mem) && typeof mem['usage'] === 'number') {
      const used = Math.round(mem['usage'] / (1 << 20));
      const limit = typeof mem['limit'] === 'number' && mem['limit'] > 0 ? `/${Math.round(mem['limit'] / (1 << 20))}` : '';
      parts.push(`RAM ${used}${limit} MB`);
    }
    return { kind: 'metric', text: `[métricas] ${parts.join(' • ')}` };
  }
  const text = pickString(parsed, ['log', 'message', 'msg', 'line', 'text', 'data']);
  if (text === undefined) return { kind: 'log', text: raw };
  const time = pickString(parsed, ['time', 'timestamp', 'ts', '@timestamp']);
  return { kind: 'log', text: `${time ? `${time} ` : ''}${text.replace(/[\r\n]+$/, '')}` };
}
