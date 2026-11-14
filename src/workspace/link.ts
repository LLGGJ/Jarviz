import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { LINK_FILE } from '../constants';

export interface AppLink {
  id: string;
  name?: string;
}

const ID_RE = /^[A-Za-z0-9_-]{8,64}$/;

/** Lê `.venix.json` (formato da CLI oficial: `{id, name}`). Devolve undefined se ausente ou inválido. */
export async function readLink(root: string): Promise<AppLink | undefined> {
  try {
    const parsed = JSON.parse(await fs.readFile(path.join(root, LINK_FILE), 'utf8')) as Record<string, unknown>;
    const id = parsed['id'];
    if (typeof id !== 'string' || !ID_RE.test(id)) return undefined;
    const name = typeof parsed['name'] === 'string' ? parsed['name'].slice(0, 100) : undefined;
    return { id, name };
  } catch {
    return undefined;
  }
}

export async function writeLink(root: string, link: AppLink): Promise<void> {
  const body = JSON.stringify({ id: link.id, name: link.name }, null, 2) + '\n';
  await fs.writeFile(path.join(root, LINK_FILE), body, { mode: 0o600 });
}
