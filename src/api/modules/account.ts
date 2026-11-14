import type { VenixHttp } from '../VenixApiClient';
import { isRecord, pickString } from '../normalize';
import type { AccountInfo, RawRecord } from '../types';

/**
 * GET /me — NÃO consta em api-docs.html v1.4; é o endpoint usado pela CLI oficial
 * (`venix whoami` / `venix apps`) e devolve a conta e a lista `applications`.
 */
export class AccountApi {
  constructor(private readonly http: VenixHttp) {}

  async getMe(signal?: AbortSignal): Promise<RawRecord> {
    const me = await this.http.json<unknown>('GET', '/me', { signal });
    return isRecord(me) ? me : {};
  }
}

export function parseAccount(me: RawRecord): AccountInfo {
  const user = isRecord(me['user']) ? me['user'] : me;
  const name = pickString(user, ['name', 'username', 'login']);
  const email = pickString(user, ['email']);
  return {
    id: pickString(user, ['id', 'uuid', '_id']),
    label: name ?? email ?? 'Conta Venix Cloud',
    email,
    plan: pickString(user, ['plan', 'planName', 'plan_name']),
  };
}
