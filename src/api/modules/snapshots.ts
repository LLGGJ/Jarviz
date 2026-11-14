import type { VenixHttp } from '../VenixApiClient';
import { extractRecords, isRecord, pickNumber, pickString, readId, readName } from '../normalize';
import { makeApiError } from '../errors';
import type { SnapshotInfo } from '../types';
import { assertValid, safeId } from '../validators';

export class SnapshotsApi {
  constructor(private readonly http: VenixHttp) {}

  /** GET /snapshots?page=&limit= */
  async list(page?: number, limit?: number, signal?: AbortSignal): Promise<SnapshotInfo[]> {
    const payload = await this.http.json<unknown>('GET', '/snapshots', { signal, query: { page, limit } });
    return parseSnapshots(payload);
  }

  /** POST /snapshots  {resourceId, name} */
  async create(resourceId: string, name: string): Promise<void> {
    safeId(resourceId);
    assertValid(name.trim() && name.length <= 100 ? undefined : 'Informe um nome de até 100 caracteres.');
    await this.http.json('POST', '/snapshots', { json: { resourceId, name } });
  }

  /** GET /snapshots/:id/download — a API devolve uma URL pré-assinada. */
  async downloadUrl(id: string): Promise<string> {
    const res = await this.http.json<unknown>('GET', `/snapshots/${safeId(id)}/download`);
    const url = typeof res === 'string' ? res : isRecord(res) ? pickString(res, ['url', 'downloadUrl', 'download_url', 'signedUrl', 'signed_url']) : undefined;
    if (!url) throw makeApiError('invalid-response');
    return url;
  }

  /** DELETE /snapshots/:id */
  async remove(id: string): Promise<void> {
    await this.http.json('DELETE', `/snapshots/${safeId(id)}`);
  }
}

export function parseSnapshots(payload: unknown): SnapshotInfo[] {
  const out: SnapshotInfo[] = [];
  for (const rec of extractRecords(payload, ['snapshots'])) {
    const id = readId(rec);
    if (!id) continue;
    out.push({
      id,
      name: readName(rec) ?? id,
      resourceId: pickString(rec, ['resourceId', 'resource_id', 'instanceId']),
      status: pickString(rec, ['status', 'state']),
      size: pickNumber(rec, ['size', 'bytes']),
      createdAt: pickString(rec, ['createdAt', 'created_at', 'created']),
      raw: rec,
    });
  }
  return out;
}
