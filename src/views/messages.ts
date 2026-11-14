/**
 * Validação estrita das mensagens vindas dos webviews (não confiáveis).
 * Qualquer coisa fora do formato esperado é descartada.
 */
export type OverviewAction = 'refresh' | 'openWeb' | 'checkConnection' | 'signOut';
export const OVERVIEW_ACTIONS: readonly OverviewAction[] = ['refresh', 'openWeb', 'checkConnection', 'signOut'];

export type OverviewMessage = { type: 'action'; action: OverviewAction } | { type: 'ready' };

export function parseOverviewMessage(msg: unknown): OverviewMessage | undefined {
  if (typeof msg !== 'object' || msg === null) return undefined;
  const m = msg as Record<string, unknown>;
  if (m['type'] === 'ready') return { type: 'ready' };
  if (m['type'] === 'action' && typeof m['action'] === 'string' && (OVERVIEW_ACTIONS as readonly string[]).includes(m['action'])) {
    return { type: 'action', action: m['action'] as OverviewAction };
  }
  return undefined;
}

export type DetailsMessage =
  | { type: 'ready' }
  | { type: 'reveal'; key: string }
  | { type: 'hide'; key: string }
  | { type: 'copy'; key: string }
  | { type: 'open'; key: string };

export function parseDetailsMessage(msg: unknown, validKeys: ReadonlySet<string>): DetailsMessage | undefined {
  if (typeof msg !== 'object' || msg === null) return undefined;
  const m = msg as Record<string, unknown>;
  if (m['type'] === 'ready') return { type: 'ready' };
  const key = m['key'];
  if (typeof key !== 'string' || key.length > 300 || !validKeys.has(key)) return undefined;
  if (m['type'] === 'reveal' || m['type'] === 'hide' || m['type'] === 'copy' || m['type'] === 'open') {
    return { type: m['type'], key };
  }
  return undefined;
}
