import { formatBytes } from '../utils/format';
import type { ScanResult } from './scanner';

/** Texto da pré-visualização exibida ANTES de qualquer envio. */
export function buildPreviewListing(scan: ScanResult, target: string): string {
  const lines: string[] = [];
  lines.push(`Destino: ${target}`);
  lines.push(`Arquivos a enviar: ${scan.files.length} (${formatBytes(scan.totalBytes)} sem compactar)`);
  if (scan.truncated) lines.push('ATENÇÃO: a lista foi truncada pelo limite de arquivos.');
  lines.push('');
  if (scan.blockedSecrets.length > 0) {
    lines.push(`Bloqueados por segurança (nunca enviados): ${scan.blockedSecrets.length}`);
    for (const f of scan.blockedSecrets) lines.push(`  ✖ ${f}`);
    lines.push('');
  }
  if (scan.excludedCount > 0) {
    lines.push(`Excluídos por regras (.venixignore / configurações): ${scan.excludedCount}`);
    for (const f of scan.excluded) lines.push(`  – ${f}`);
    if (scan.excludedCount > scan.excluded.length) lines.push(`  … e mais ${scan.excludedCount - scan.excluded.length}`);
    lines.push('');
  }
  if (scan.skippedSymlinks > 0) lines.push(`Links simbólicos ignorados: ${scan.skippedSymlinks}\n`);
  lines.push('Arquivos incluídos:');
  for (const f of scan.files) lines.push(`  ✔ ${f.rel}  (${formatBytes(f.size)})`);
  return lines.join('\n');
}

export function summarizeBlocked(scan: ScanResult): string | undefined {
  if (scan.blockedSecrets.length === 0) return undefined;
  const shown = scan.blockedSecrets.slice(0, 3).join(', ');
  const rest = scan.blockedSecrets.length - 3;
  return `Bloqueados por segurança: ${shown}${rest > 0 ? ` (+${rest})` : ''}`;
}
