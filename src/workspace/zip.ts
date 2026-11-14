import * as fs from 'node:fs/promises';
import { promisify } from 'node:util';
import { deflateRaw } from 'node:zlib';
import type { ScannedFile } from './scanner';

const deflateRawAsync = promisify(deflateRaw);

let crcTable: Uint32Array | undefined;
function table(): Uint32Array {
  if (crcTable) return crcTable;
  crcTable = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crcTable[n] = c >>> 0;
  }
  return crcTable;
}

export function crc32(data: Uint8Array): number {
  const t = table();
  let c = 0xffffffff;
  for (let i = 0; i < data.length; i++) c = (t[(c ^ (data[i] as number)) & 0xff] as number) ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function dosDateTime(ms: number): { time: number; date: number } {
  const d = new Date(Math.max(ms, Date.UTC(1980, 0, 1)));
  return {
    time: (d.getHours() << 11) | (d.getMinutes() << 5) | (d.getSeconds() >> 1),
    date: ((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate(),
  };
}

export class ZipLimitError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ZipLimitError';
  }
}

export interface ZipOptions {
  maxBytes: number;
  signal?: AbortSignal;
  onProgress?: (done: number, total: number) => void;
}

/**
 * Gera um .zip (sem Zip64, nomes UTF-8, deflate) em memória, sem dependências externas.
 * Limites: 65.535 arquivos e `maxBytes` no resultado final.
 */
export async function buildZip(files: readonly ScannedFile[], options: ZipOptions): Promise<Buffer> {
  if (files.length > 0xffff) {
    throw new ZipLimitError('Projeto com arquivos demais (limite de 65.535). Ajuste as exclusões.');
  }
  const chunks: Buffer[] = [];
  const central: Buffer[] = [];
  let offset = 0;

  for (let i = 0; i < files.length; i++) {
    if (options.signal?.aborted) throw new Error('cancelado');
    const file = files[i] as ScannedFile;
    const data = await fs.readFile(file.abs);
    const name = Buffer.from(file.rel, 'utf8');
    const crc = crc32(data);
    let method = 8;
    let payload: Buffer = data.length > 0 ? await deflateRawAsync(data) : Buffer.alloc(0);
    if (data.length === 0 || payload.length >= data.length) {
      method = 0;
      payload = data;
    }
    const { time, date } = dosDateTime(file.mtimeMs);

    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6);
    local.writeUInt16LE(method, 8);
    local.writeUInt16LE(time, 10);
    local.writeUInt16LE(date, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(payload.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(name.length, 26);
    local.writeUInt16LE(0, 28);

    const entry = Buffer.alloc(46);
    entry.writeUInt32LE(0x02014b50, 0);
    entry.writeUInt16LE((3 << 8) | 20, 4);
    entry.writeUInt16LE(20, 6);
    entry.writeUInt16LE(0x0800, 8);
    entry.writeUInt16LE(method, 10);
    entry.writeUInt16LE(time, 12);
    entry.writeUInt16LE(date, 14);
    entry.writeUInt32LE(crc, 16);
    entry.writeUInt32LE(payload.length, 20);
    entry.writeUInt32LE(data.length, 24);
    entry.writeUInt16LE(name.length, 28);
    entry.writeUInt32LE(((0o100644 << 16) >>> 0), 38);
    entry.writeUInt32LE(offset, 42);

    chunks.push(local, name, payload);
    central.push(entry, name);
    offset += local.length + name.length + payload.length;
    if (offset > options.maxBytes) {
      throw new ZipLimitError(`O pacote excede o limite de ${Math.round(options.maxBytes / 1024 / 1024)} MB.`);
    }
    options.onProgress?.(i + 1, files.length);
  }

  const centralBuf = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(files.length, 8);
  end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(centralBuf.length, 12);
  end.writeUInt32LE(offset, 16);
  const out = Buffer.concat([...chunks, centralBuf, end]);
  if (out.length > options.maxBytes) {
    throw new ZipLimitError(`O pacote excede o limite de ${Math.round(options.maxBytes / 1024 / 1024)} MB.`);
  }
  return out;
}
