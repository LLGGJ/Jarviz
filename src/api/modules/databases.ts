import type { VenixHttp } from '../VenixApiClient';
import { extractRecords, isRecord, mapStatus, pickString, readId, readName, readRamMb, readStatusRaw } from '../normalize';
import type { DatabaseInstance, RawRecord } from '../types';
import { assertValid, checkDbName, checkDbRam, DB_ENGINES, REGIONS, safeId, type DbEngine, type Region } from '../validators';

export interface CreateDatabaseInput {
  name: string;
  engine: DbEngine;
  ram: number;
  region: Region;
  installManager: boolean;
  workspaceId?: string;
}

export class DatabasesApi {
  constructor(private readonly http: VenixHttp) {}

  /** GET /instances/databases */
  async list(signal?: AbortSignal): Promise<DatabaseInstance[]> {
    const payload = await this.http.json<unknown>('GET', '/instances/databases', { signal });
    return parseDatabases(payload);
  }

  /** POST /instances/databases/create */
  async create(input: CreateDatabaseInput): Promise<RawRecord> {
    assertValid(checkDbName(input.name));
    if (!DB_ENGINES.includes(input.engine)) assertValid('Motor de banco inválido.');
    assertValid(checkDbRam(input.ram));
    if (!REGIONS.includes(input.region)) assertValid('Região inválida (use BR ou CA).');
    const body: Record<string, unknown> = {
      name: input.name,
      engine: input.engine,
      ram: input.ram,
      region: input.region,
      installManager: input.installManager,
    };
    if (input.workspaceId) body['workspaceId'] = input.workspaceId;
    const res = await this.http.json<unknown>('POST', '/instances/databases/create', { json: body });
    return isRecord(res) ? res : {};
  }

  /** GET /instances/databases/:id/credentials/zip — ZIP binário com ca.pem, client-cert.pem e client-key.pem. */
  async downloadCertificates(id: string, signal?: AbortSignal): Promise<Uint8Array> {
    return this.http.buffer('GET', `/instances/databases/${safeId(id)}/credentials/zip`, { signal, timeoutMs: 60_000 });
  }

  /** DELETE /instances/databases/:id — exclusão permanente. */
  async remove(id: string): Promise<void> {
    await this.http.json('DELETE', `/instances/databases/${safeId(id)}`);
  }
}

export function parseDatabases(payload: unknown): DatabaseInstance[] {
  const out: DatabaseInstance[] = [];
  for (const rec of extractRecords(payload, ['databases'])) {
    const id = readId(rec);
    if (!id) continue;
    const statusRaw = readStatusRaw(rec);
    out.push({
      id,
      name: readName(rec) ?? id,
      engine: pickString(rec, ['engine', 'type', 'db_type', 'dbType']),
      region: pickString(rec, ['region']),
      status: mapStatus(statusRaw),
      statusRaw,
      ramMb: readRamMb(rec, ['ram', 'max_ram', 'memory']),
      host: pickString(rec, ['host', 'hostname', 'address']),
      raw: rec,
    });
  }
  return out;
}
