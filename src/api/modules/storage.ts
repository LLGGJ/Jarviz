import type { VenixHttp } from '../VenixApiClient';
import { toBlob } from '../blob';
import { extractRecords, isRecord, pickNumber, pickString } from '../normalize';
import { makeApiError } from '../errors';
import type { FileUpload, StorageObject, UploadedObject } from '../types';
import { assertValid, checkCdnSize, checkExpireDays, checkObjectName, checkObjectPrefix } from '../validators';

export interface UploadObjectInput {
  name: string;
  prefix?: string;
  expireDays?: number;
  file: FileUpload;
}

export class StorageApi {
  constructor(private readonly http: VenixHttp) {}

  /** POST /objects?name=&prefix=&expire=  (multipart: file) — devolve {url, size}. */
  async upload(input: UploadObjectInput, signal?: AbortSignal): Promise<UploadedObject> {
    assertValid(checkObjectName(input.name));
    if (input.prefix) assertValid(checkObjectPrefix(input.prefix));
    if (input.expireDays !== undefined) assertValid(checkExpireDays(input.expireDays));
    assertValid(checkCdnSize(input.file.data.byteLength));

    const form = new FormData();
    form.append('file', toBlob(input.file.data), input.file.filename);
    const res = await this.http.json<unknown>('POST', '/objects', {
      form,
      signal,
      timeoutMs: 10 * 60_000,
      query: { name: input.name, prefix: input.prefix || undefined, expire: input.expireDays },
    });
    const url = isRecord(res) ? pickString(res, ['url']) : undefined;
    if (!url) throw makeApiError('invalid-response');
    return { url, size: isRecord(res) ? pickNumber(res, ['size']) : undefined };
  }

  /** GET /objects */
  async list(signal?: AbortSignal): Promise<StorageObject[]> {
    const payload = await this.http.json<unknown>('GET', '/objects', { signal });
    return parseObjects(payload);
  }
}

export function parseObjects(payload: unknown): StorageObject[] {
  const out: StorageObject[] = [];
  for (const rec of extractRecords(payload, ['objects', 'files'])) {
    const url = pickString(rec, ['url', 'cdn_url', 'cdnUrl', 'public_url']);
    const name = pickString(rec, ['name', 'key', 'path', 'filename']) ?? (url ? url.split('/').pop() : undefined);
    if (!name) continue;
    out.push({ id: pickString(rec, ['id', 'key']) ?? name, name, url, size: pickNumber(rec, ['size', 'bytes']), raw: rec });
  }
  return out;
}
