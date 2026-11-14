import type { VenixHttp } from '../VenixApiClient';
import {
  extractList,
  extractRecords,
  isRecord,
  mapStatus,
  pickString,
  readId,
  readName,
  readRamMb,
  readStatusRaw,
} from '../normalize';
import type { GameServer, RawRecord } from '../types';
import {
  assertValid,
  checkConsoleCommand,
  checkGameName,
  checkGameRam,
  DOCUMENTED_EGGS,
  REGIONS,
  safeId,
  type Region,
} from '../validators';

export interface CreateGameInput {
  name: string;
  game: string;
  ram: number;
  region: Region;
}

export interface EggInfo {
  id: string;
  label: string;
}

export class GamesApi {
  constructor(private readonly http: VenixHttp) {}

  /** GET /instances/games */
  async list(signal?: AbortSignal): Promise<GameServer[]> {
    const payload = await this.http.json<unknown>('GET', '/instances/games', { signal });
    return parseGameServers(payload);
  }

  /** GET /instances/games/eggs */
  async eggs(signal?: AbortSignal): Promise<EggInfo[]> {
    const payload = await this.http.json<unknown>('GET', '/instances/games/eggs', { signal });
    return parseEggs(payload);
  }

  /** POST /instances/games/create */
  async create(input: CreateGameInput): Promise<RawRecord> {
    assertValid(checkGameName(input.name));
    if (!input.game || /\s/.test(input.game) || input.game.length > 64) assertValid('ID do egg inválido.');
    assertValid(checkGameRam(input.ram));
    if (!REGIONS.includes(input.region)) assertValid('Região inválida (use BR ou CA).');
    const res = await this.http.json<unknown>('POST', '/instances/games/create', { json: input });
    return isRecord(res) ? res : {};
  }

  /** POST /instances/games/:id/command  {cmd} */
  async sendCommand(id: string, cmd: string): Promise<void> {
    assertValid(checkConsoleCommand(cmd));
    await this.http.json('POST', `/instances/games/${safeId(id)}/command`, { json: { cmd } });
  }

  /** DELETE /instances/games/:id */
  async remove(id: string): Promise<void> {
    await this.http.json('DELETE', `/instances/games/${safeId(id)}`);
  }
}

export function parseGameServers(payload: unknown): GameServer[] {
  const out: GameServer[] = [];
  for (const rec of extractRecords(payload, ['games', 'servers'])) {
    const id = readId(rec);
    if (!id) continue;
    const statusRaw = readStatusRaw(rec);
    const ip = pickString(rec, ['ip', 'host', 'address', 'public_ip', 'publicIp']);
    const port = pickString(rec, ['port', 'public_port', 'publicPort']);
    out.push({
      id,
      name: readName(rec) ?? id,
      game: pickString(rec, ['game', 'egg', 'type']),
      region: pickString(rec, ['region']),
      status: mapStatus(statusRaw),
      statusRaw,
      ramMb: readRamMb(rec, ['ram', 'max_ram', 'memory']),
      address: ip ? (port ? `${ip}:${port}` : ip) : undefined,
      raw: rec,
    });
  }
  return out;
}

/** Aceita lista de strings ou de objetos; sem resposta utilizável, devolve vazio (o chamador usa os eggs documentados). */
export function parseEggs(payload: unknown): EggInfo[] {
  const out: EggInfo[] = [];
  for (const item of extractList(payload, ['eggs'])) {
    if (typeof item === 'string' && item) out.push({ id: item, label: item });
    else if (isRecord(item)) {
      const id = pickString(item, ['id', 'egg', 'slug', 'key', 'name']);
      if (id) out.push({ id, label: pickString(item, ['name', 'title', 'label', 'id']) ?? id });
    }
  }
  if (out.length === 0 && isRecord(payload)) {
    // Formato {minecraft_paper: {...}, rust: {...}}
    const inner = isRecord(payload['response']) ? payload['response'] : payload;
    for (const [key, value] of Object.entries(inner)) {
      if (isRecord(value) && /^[a-z0-9_]+$/.test(key)) {
        out.push({ id: key, label: pickString(value, ['name', 'title', 'label']) ?? key });
      }
    }
  }
  return out;
}

export function documentedEggs(): EggInfo[] {
  return DOCUMENTED_EGGS.map((id) => ({ id, label: id }));
}
