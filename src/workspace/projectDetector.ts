import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import type { Runtime } from '../api/validators';

export interface ProjectInfo {
  runtime?: Runtime;
  /** Arquivos que levaram à detecção (exibidos ao usuário). */
  evidence: string[];
  suggestedName?: string;
  startCommandHint?: string;
}

const SIGNATURES: Array<{ file: string; runtime: Runtime }> = [
  { file: 'bun.lockb', runtime: 'bun' },
  { file: 'bun.lock', runtime: 'bun' },
  { file: 'package.json', runtime: 'nodejs' },
  { file: 'requirements.txt', runtime: 'python' },
  { file: 'pyproject.toml', runtime: 'python' },
  { file: 'go.mod', runtime: 'go' },
  { file: 'pom.xml', runtime: 'java' },
  { file: 'build.gradle', runtime: 'java' },
  { file: 'composer.json', runtime: 'php' },
  { file: 'Gemfile', runtime: 'ruby' },
  { file: 'index.html', runtime: 'static' },
];

/** Heurística simples e transparente: a primeira assinatura encontrada define o runtime sugerido. */
export async function detectProject(root: string): Promise<ProjectInfo> {
  let names: string[] = [];
  try {
    names = await fs.readdir(root);
  } catch {
    return { evidence: [] };
  }
  const present = new Set(names);
  const evidence: string[] = [];
  let runtime: Runtime | undefined;
  for (const sig of SIGNATURES) {
    if (present.has(sig.file)) {
      evidence.push(sig.file);
      runtime ??= sig.runtime;
    }
  }
  const info: ProjectInfo = { runtime, evidence, suggestedName: sanitizeAppName(path.basename(root)) };
  if (present.has('package.json')) {
    try {
      const pkg = JSON.parse(await fs.readFile(path.join(root, 'package.json'), 'utf8')) as {
        name?: unknown;
        scripts?: Record<string, unknown>;
      };
      if (typeof pkg.name === 'string') info.suggestedName = sanitizeAppName(pkg.name) ?? info.suggestedName;
      if (typeof pkg.scripts?.['start'] === 'string') info.startCommandHint = 'npm start';
    } catch {
      // package.json inválido: mantém a detecção básica
    }
  }
  return info;
}

/** Ajusta um nome candidato às regras documentadas (3–50, [a-zA-Z0-9_-]). */
export function sanitizeAppName(raw: string): string | undefined {
  const cleaned = raw.replace(/^@[^/]+\//, '').replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 50);
  return cleaned.length >= 3 ? cleaned : undefined;
}

/** Sugere um nome de objeto de CDN válido (3–32, [a-zA-Z0-9_]) a partir do nome do arquivo. */
export function suggestObjectName(fileName: string): string {
  const base = fileName.replace(/\.[^.]*$/, '');
  let name = base.replace(/[^a-zA-Z0-9_]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 32);
  if (name.length < 3) name = (name + '_file').slice(0, 32);
  return name;
}
