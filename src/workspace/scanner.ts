import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { IGNORE_FILE } from '../constants';
import { isBlockedDirectory, isSecretFile } from '../security/secretFiles';
import { createIgnoreMatcher } from './ignore';

export interface ScannedFile {
  rel: string;
  abs: string;
  size: number;
  mtimeMs: number;
}

export interface ScanResult {
  files: ScannedFile[];
  totalBytes: number;
  /** Arquivos secretos que foram bloqueados (nunca enviados). */
  blockedSecrets: string[];
  /** Caminhos excluídos por regras do usuário/.venixignore (amostra limitada). */
  excluded: string[];
  excludedCount: number;
  skippedSymlinks: number;
  truncated: boolean;
}

export interface ScanOptions {
  exclude: readonly string[];
  maxFiles?: number;
  signal?: AbortSignal;
}

const MAX_SAMPLE = 200;

export async function readIgnoreRules(root: string): Promise<string[]> {
  try {
    const text = await fs.readFile(path.join(root, IGNORE_FILE), 'utf8');
    return text.split(/\r?\n/);
  } catch {
    return [];
  }
}

/**
 * Percorre o projeto aplicando, nesta ordem: bloqueios fixos de segurança
 * (segredos, .git, links simbólicos), regras do usuário e `.venixignore`.
 */
export async function scanProject(root: string, options: ScanOptions): Promise<ScanResult> {
  const matcher = createIgnoreMatcher([...options.exclude, ...(await readIgnoreRules(root))]);
  const maxFiles = options.maxFiles ?? 65_000;
  const result: ScanResult = {
    files: [],
    totalBytes: 0,
    blockedSecrets: [],
    excluded: [],
    excludedCount: 0,
    skippedSymlinks: 0,
    truncated: false,
  };

  const note = (list: string[], rel: string): void => {
    if (list.length < MAX_SAMPLE) list.push(rel);
  };

  async function walk(dirAbs: string, dirRel: string): Promise<void> {
    if (options.signal?.aborted) throw new Error('cancelado');
    const entries = await fs.readdir(dirAbs, { withFileTypes: true });
    entries.sort((a, b) => a.name.localeCompare(b.name));
    for (const entry of entries) {
      const rel = dirRel ? `${dirRel}/${entry.name}` : entry.name;
      const abs = path.join(dirAbs, entry.name);
      if (entry.isSymbolicLink()) {
        result.skippedSymlinks++;
        continue;
      }
      if (entry.isDirectory()) {
        if (isBlockedDirectory(entry.name)) {
          if (entry.name !== '.git') note(result.blockedSecrets, `${rel}/`);
          continue;
        }
        if (matcher(rel, true)) {
          result.excludedCount++;
          note(result.excluded, `${rel}/`);
          continue;
        }
        await walk(abs, rel);
        continue;
      }
      if (!entry.isFile()) continue;
      if (isSecretFile(rel)) {
        note(result.blockedSecrets, rel);
        continue;
      }
      if (matcher(rel, false)) {
        result.excludedCount++;
        note(result.excluded, rel);
        continue;
      }
      if (result.files.length >= maxFiles) {
        result.truncated = true;
        return;
      }
      const stat = await fs.stat(abs);
      result.files.push({ rel, abs, size: stat.size, mtimeMs: stat.mtimeMs });
      result.totalBytes += stat.size;
    }
  }

  await walk(root, '');
  return result;
}
