import * as vscode from 'vscode';
import type { Services } from '../services';
import type { TreeNode } from '../providers/VenixTreeProvider';
import { showDetails } from '../views/DetailsPanel';

const ORDER = ['id', 'name', 'appName', 'status', 'state', 'runtime', 'engine', 'game', 'region', 'host', 'ip', 'port', 'url'];

/** Abre o painel de detalhes mostrando exatamente os campos devolvidos pela API (sensíveis mascarados). */
export function showNodeDetails(s: Services, node: unknown): void {
  const n = node as TreeNode | undefined;
  if (!n || typeof n !== 'object' || !('kind' in n)) return;
  switch (n.kind) {
    case 'app':
      return showDetails(s.context.extensionUri, { panelKey: `app:${n.item.id}`, title: n.item.name, subtitle: 'Aplicação', raw: n.item.raw, preferredOrder: ORDER });
    case 'game':
      return showDetails(s.context.extensionUri, { panelKey: `game:${n.item.id}`, title: n.item.name, subtitle: 'Servidor de jogo', raw: n.item.raw, preferredOrder: ORDER });
    case 'database':
      return showDetails(s.context.extensionUri, { panelKey: `db:${n.item.id}`, title: n.item.name, subtitle: 'Banco de dados — credenciais ocultas por padrão', raw: n.item.raw, preferredOrder: ORDER });
    case 'object':
      return showDetails(s.context.extensionUri, { panelKey: `obj:${n.item.id}`, title: n.item.name, subtitle: 'Objeto do CDN', raw: n.item.raw, preferredOrder: ['name', 'url', 'size'] });
    case 'snapshot':
      return showDetails(s.context.extensionUri, { panelKey: `snap:${n.item.id}`, title: n.item.name, subtitle: 'Snapshot', raw: n.item.raw, preferredOrder: ORDER });
    default:
      return;
  }
}

export async function copyId(arg: unknown): Promise<void> {
  const item = (arg as { item?: { id?: unknown } } | undefined)?.item;
  if (typeof item?.id !== 'string') return;
  await vscode.env.clipboard.writeText(item.id);
  void vscode.window.showInformationMessage('Venix Cloud: ID copiado.');
}
