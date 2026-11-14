import { isRecord } from '../api/normalize';
import type { RawRecord } from '../api/types';
import { isSensitiveKey, MASK } from '../security/redact';

export interface DetailField {
  key: string;
  label: string;
  /** Valor para exibição — já mascarado quando `sensitive`. */
  display: string;
  sensitive: boolean;
}

export interface DetailEntry extends DetailField {
  /** Valor real; NUNCA é enviado ao webview até o usuário pedir. */
  value: string;
}

function stringify(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

/**
 * Converte o registro devolvido pela API em campos de exibição. Nada é inventado:
 * só aparece o que a API enviou. Campos sensíveis ficam mascarados por padrão.
 */
export function buildDetailEntries(raw: RawRecord, preferredOrder: readonly string[] = []): DetailEntry[] {
  const flat: Array<[string, unknown]> = [];
  for (const [key, value] of Object.entries(raw)) {
    if (isRecord(value) && !isSensitiveKey(key)) {
      for (const [inner, innerValue] of Object.entries(value)) flat.push([`${key}.${inner}`, innerValue]);
    } else {
      flat.push([key, value]);
    }
  }
  const rank = (key: string): number => {
    const i = preferredOrder.indexOf(key);
    return i === -1 ? preferredOrder.length : i;
  };
  flat.sort((a, b) => rank(a[0]) - rank(b[0]));
  const out: DetailEntry[] = [];
  for (const [key, value] of flat) {
    const text = stringify(value);
    if (text === '') continue;
    const sensitive = isSensitiveKey(key);
    out.push({ key, label: key, value: text, display: sensitive ? MASK : text, sensitive });
  }
  return out;
}

export function toPublicFields(entries: readonly DetailEntry[]): DetailField[] {
  return entries.map(({ key, label, display, sensitive }) => ({ key, label, display, sensitive }));
}
