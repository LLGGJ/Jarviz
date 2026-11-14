export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '-';
  if (bytes >= 1 << 30) return `${(bytes / (1 << 30)).toFixed(1)} GB`;
  if (bytes >= 1 << 20) return `${(bytes / (1 << 20)).toFixed(1)} MB`;
  if (bytes >= 1 << 10) return `${(bytes / (1 << 10)).toFixed(1)} KB`;
  return `${bytes} B`;
}

export function formatMb(mb: number | undefined): string | undefined {
  if (mb === undefined) return undefined;
  return mb >= 1024 && mb % 1024 === 0 ? `${mb / 1024} GB` : `${mb} MB`;
}

export function formatTime(ms: number | undefined, locale = 'pt-BR'): string {
  if (ms === undefined) return '-';
  return new Date(ms).toLocaleString(locale, { dateStyle: 'short', timeStyle: 'medium' });
}

export function pluralize(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}

export function timestampName(prefix = 'backup', date = new Date()): string {
  const p = (n: number): string => String(n).padStart(2, '0');
  return `${prefix}-${date.getFullYear()}${p(date.getMonth() + 1)}${p(date.getDate())}-${p(date.getHours())}${p(date.getMinutes())}${p(date.getSeconds())}`;
}
