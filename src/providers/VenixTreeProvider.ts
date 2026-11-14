import * as vscode from 'vscode';
import type { AuthenticationManager } from '../auth/AuthenticationManager';
import type { AppSummary, DatabaseInstance, GameServer, ResourceStatus, SnapshotInfo, StorageObject } from '../api/types';
import { formatBytes, formatMb, formatTime } from '../utils/format';
import type { ResourceStore, Slice } from './ResourceStore';

export type CategoryId = 'account' | 'apps' | 'games' | 'databases' | 'storage' | 'snapshots' | 'settings';

export type TreeNode =
  | { kind: 'category'; id: CategoryId }
  | { kind: 'app'; item: AppSummary }
  | { kind: 'game'; item: GameServer }
  | { kind: 'database'; item: DatabaseInstance }
  | { kind: 'object'; item: StorageObject }
  | { kind: 'snapshot'; item: SnapshotInfo }
  | { kind: 'info'; id: string; label: string; description?: string; icon?: string }
  | { kind: 'message'; id: string; label: string; icon: string; command?: vscode.Command; tooltip?: string };

const CATEGORY_META: Record<CategoryId, { label: string; icon: string }> = {
  account: { label: 'Conta', icon: 'account' },
  apps: { label: 'Aplicações', icon: 'rocket' },
  games: { label: 'Servidores de jogos', icon: 'play-circle' },
  databases: { label: 'Bancos de dados', icon: 'database' },
  storage: { label: 'Armazenamento', icon: 'cloud' },
  snapshots: { label: 'Snapshots', icon: 'history' },
  settings: { label: 'Configurações', icon: 'gear' },
};

const STATUS_LABEL: Record<ResourceStatus, string> = {
  online: 'online',
  offline: 'offline',
  starting: 'iniciando',
  stopping: 'parando',
  error: 'erro',
  unknown: 'n/d',
};

export function statusIcon(status: ResourceStatus): vscode.ThemeIcon {
  switch (status) {
    case 'online':
      return new vscode.ThemeIcon('circle-filled', new vscode.ThemeColor('testing.iconPassed'));
    case 'offline':
      return new vscode.ThemeIcon('circle-filled', new vscode.ThemeColor('testing.iconFailed'));
    case 'error':
      return new vscode.ThemeIcon('error', new vscode.ThemeColor('testing.iconFailed'));
    case 'starting':
    case 'stopping':
      return new vscode.ThemeIcon('sync~spin', new vscode.ThemeColor('testing.iconQueued'));
    default:
      return new vscode.ThemeIcon('circle-outline');
  }
}

/** Sufixo usado nos `when` dos menus: online / offline / unknown. */
export function appContextSuffix(status: ResourceStatus): 'online' | 'offline' | 'unknown' {
  if (status === 'online') return 'online';
  if (status === 'offline' || status === 'error') return 'offline';
  return 'unknown';
}

export class VenixTreeProvider implements vscode.TreeDataProvider<TreeNode>, vscode.Disposable {
  private readonly emitter = new vscode.EventEmitter<TreeNode | undefined>();
  readonly onDidChangeTreeData = this.emitter.event;
  private readonly subs: Array<{ dispose(): void }>;

  constructor(
    private readonly store: ResourceStore,
    private readonly auth: AuthenticationManager,
    private readonly linkedAppId: () => string | undefined = () => undefined,
  ) {
    this.subs = [store.onDidChange(() => this.refresh()), auth.onDidChange(() => this.refresh())];
  }

  refresh(): void {
    this.emitter.fire(undefined);
  }

  dispose(): void {
    for (const s of this.subs) s.dispose();
    this.emitter.dispose();
  }

  getChildren(node?: TreeNode): TreeNode[] {
    if (!this.auth.isSignedIn()) return [];
    if (!node) {
      return (['account', 'apps', 'games', 'databases', 'storage', 'snapshots', 'settings'] as CategoryId[]).map((id) => ({ kind: 'category', id }));
    }
    if (node.kind !== 'category') return [];
    const snap = this.store.snapshot;
    switch (node.id) {
      case 'account':
        return this.accountChildren();
      case 'apps':
        return this.sliceChildren('apps', snap.apps, (item) => ({ kind: 'app', item }), 'Nenhuma aplicação encontrada.');
      case 'games':
        return this.sliceChildren('games', snap.games, (item) => ({ kind: 'game', item }), 'Nenhum servidor de jogo encontrado.');
      case 'databases':
        return this.sliceChildren('databases', snap.databases, (item) => ({ kind: 'database', item }), 'Nenhum banco de dados encontrado.');
      case 'storage':
        return this.sliceChildren('storage', snap.storage, (item) => ({ kind: 'object', item }), 'Nenhum objeto no armazenamento.');
      case 'snapshots':
        return this.sliceChildren('snapshots', snap.snapshots, (item) => ({ kind: 'snapshot', item }), 'Nenhum snapshot encontrado.');
      case 'settings':
        return [];
    }
  }

  getTreeItem(node: TreeNode): vscode.TreeItem {
    switch (node.kind) {
      case 'category':
        return this.categoryItem(node.id);
      case 'app': {
        const a = node.item;
        const linked = this.linkedAppId() === a.id;
        const item = new vscode.TreeItem(a.name, vscode.TreeItemCollapsibleState.None);
        item.id = `app:${a.id}`;
        item.iconPath = statusIcon(a.status);
        item.description = [STATUS_LABEL[a.status], formatMb(a.ramMb), linked ? '↔ projeto atual' : undefined].filter(Boolean).join(' · ');
        item.contextValue = `venix.app.${appContextSuffix(a.status)}`;
        item.tooltip = this.tooltip(a.name, [['ID', a.id], ['Status', a.statusRaw ?? 'não informado'], ['Runtime', a.runtime], ['URL', a.url]]);
        item.command = { command: 'venixCloud.showDetails', title: 'Detalhes', arguments: [node] };
        return item;
      }
      case 'game': {
        const g = node.item;
        const item = new vscode.TreeItem(g.name, vscode.TreeItemCollapsibleState.None);
        item.id = `game:${g.id}`;
        item.iconPath = statusIcon(g.status);
        item.description = [g.game, g.region, STATUS_LABEL[g.status]].filter(Boolean).join(' · ');
        item.contextValue = 'venix.game';
        item.tooltip = this.tooltip(g.name, [['ID', g.id], ['Jogo', g.game], ['Região', g.region], ['Endereço', g.address], ['RAM', formatMb(g.ramMb)]]);
        item.command = { command: 'venixCloud.showDetails', title: 'Detalhes', arguments: [node] };
        return item;
      }
      case 'database': {
        const d = node.item;
        const item = new vscode.TreeItem(d.name, vscode.TreeItemCollapsibleState.None);
        item.id = `db:${d.id}`;
        item.iconPath = statusIcon(d.status);
        item.description = [d.engine, d.region, STATUS_LABEL[d.status]].filter(Boolean).join(' · ');
        item.contextValue = 'venix.database';
        item.tooltip = this.tooltip(d.name, [['ID', d.id], ['Motor', d.engine], ['Região', d.region], ['Host', d.host], ['RAM', formatMb(d.ramMb)]]);
        item.command = { command: 'venixCloud.showDetails', title: 'Detalhes', arguments: [node] };
        return item;
      }
      case 'object': {
        const o = node.item;
        const item = new vscode.TreeItem(o.name, vscode.TreeItemCollapsibleState.None);
        item.id = `obj:${o.id}`;
        item.iconPath = new vscode.ThemeIcon('file-media');
        item.description = o.size !== undefined ? formatBytes(o.size) : undefined;
        item.contextValue = o.url ? 'venix.object.url' : 'venix.object';
        item.tooltip = this.tooltip(o.name, [['URL', o.url], ['Tamanho', o.size !== undefined ? formatBytes(o.size) : undefined]]);
        item.command = { command: 'venixCloud.showDetails', title: 'Detalhes', arguments: [node] };
        return item;
      }
      case 'snapshot': {
        const s = node.item;
        const item = new vscode.TreeItem(s.name, vscode.TreeItemCollapsibleState.None);
        item.id = `snap:${s.id}`;
        item.iconPath = new vscode.ThemeIcon('archive');
        item.description = [s.status, s.size !== undefined ? formatBytes(s.size) : undefined, s.createdAt].filter(Boolean).join(' · ');
        item.contextValue = 'venix.snapshot';
        item.tooltip = this.tooltip(s.name, [['ID', s.id], ['Recurso', s.resourceId], ['Status', s.status], ['Criado em', s.createdAt]]);
        item.command = { command: 'venixCloud.showDetails', title: 'Detalhes', arguments: [node] };
        return item;
      }
      case 'info': {
        const item = new vscode.TreeItem(node.label, vscode.TreeItemCollapsibleState.None);
        item.id = node.id;
        item.description = node.description;
        item.iconPath = node.icon ? new vscode.ThemeIcon(node.icon) : undefined;
        item.contextValue = 'venix.info';
        return item;
      }
      case 'message': {
        const item = new vscode.TreeItem(node.label, vscode.TreeItemCollapsibleState.None);
        item.id = node.id;
        item.iconPath = new vscode.ThemeIcon(node.icon);
        item.command = node.command;
        item.tooltip = node.tooltip;
        item.contextValue = 'venix.message';
        return item;
      }
    }
  }

  // ---------------------------------------------------------------- internos

  private categoryItem(id: CategoryId): vscode.TreeItem {
    const meta = CATEGORY_META[id];
    const leaf = id === 'settings';
    const item = new vscode.TreeItem(
      meta.label,
      leaf ? vscode.TreeItemCollapsibleState.None : id === 'account' ? vscode.TreeItemCollapsibleState.Expanded : vscode.TreeItemCollapsibleState.Collapsed,
    );
    item.id = `category:${id}`;
    item.iconPath = new vscode.ThemeIcon(meta.icon);
    item.contextValue = `venix.category.${id}`;
    if (leaf) item.command = { command: 'venixCloud.openSettings', title: 'Configurações' };
    const slice = this.sliceOf(id);
    if (slice?.state === 'loaded') item.description = String(slice.items.length);
    else if (slice?.state === 'loading') item.description = 'carregando…';
    else if (slice?.state === 'error') item.description = 'erro';
    return item;
  }

  private sliceOf(id: CategoryId): Slice<unknown> | undefined {
    const snap = this.store.snapshot;
    switch (id) {
      case 'apps':
        return snap.apps;
      case 'games':
        return snap.games;
      case 'databases':
        return snap.databases;
      case 'storage':
        return snap.storage;
      case 'snapshots':
        return snap.snapshots;
      default:
        return undefined;
    }
  }

  private sliceChildren<T>(id: string, slice: Slice<T>, make: (item: T) => TreeNode, emptyText: string): TreeNode[] {
    if (slice.state === 'loading' && slice.items.length === 0) {
      return [{ kind: 'message', id: `msg:${id}:loading`, label: 'Carregando…', icon: 'loading~spin' }];
    }
    if (slice.state === 'error') {
      return [
        {
          kind: 'message',
          id: `msg:${id}:error`,
          label: slice.error ?? 'Falha ao carregar.',
          icon: 'warning',
          tooltip: 'Clique para tentar novamente',
          command: { command: 'venixCloud.refresh', title: 'Atualizar' },
        },
      ];
    }
    if (slice.state === 'idle') {
      return [{ kind: 'message', id: `msg:${id}:idle`, label: 'Use "Atualizar" para carregar.', icon: 'info', command: { command: 'venixCloud.refresh', title: 'Atualizar' } }];
    }
    if (slice.items.length === 0) return [{ kind: 'message', id: `msg:${id}:empty`, label: emptyText, icon: 'info' }];
    return slice.items.map(make);
  }

  private accountChildren(): TreeNode[] {
    const session = this.auth.getSessionInfo();
    if (!session) return [];
    const rows: TreeNode[] = [
      { kind: 'info', id: 'acct:name', label: session.account.label, icon: 'account', description: 'conta' },
      { kind: 'info', id: 'acct:method', label: 'Autenticação', icon: 'key', description: session.method === 'oauth' ? 'OAuth 2.0 (PKCE)' : 'Token de API' },
    ];
    if (session.account.email) rows.push({ kind: 'info', id: 'acct:email', label: 'E-mail', icon: 'mail', description: session.account.email });
    if (session.account.plan) rows.push({ kind: 'info', id: 'acct:plan', label: 'Plano', icon: 'tag', description: session.account.plan });
    if (session.expiresAt) rows.push({ kind: 'info', id: 'acct:exp', label: 'Sessão expira em', icon: 'clock', description: formatTime(session.expiresAt) });
    const conn = this.store.snapshot.connection;
    rows.push({
      kind: 'info',
      id: 'acct:conn',
      label: 'Conexão',
      icon: conn === 'ok' ? 'check' : conn === 'error' ? 'warning' : 'question',
      description: conn === 'ok' ? 'ok' : conn === 'error' ? 'com falhas' : 'não verificada',
    });
    return rows;
  }

  private tooltip(title: string, rows: Array<[string, string | undefined]>): vscode.MarkdownString {
    const md = new vscode.MarkdownString(undefined, true);
    md.appendMarkdown(`**${title.replace(/[*_`\\]/g, '\\$&')}**\n\n`);
    for (const [k, v] of rows) {
      if (v) md.appendMarkdown(`${k}: \`${v.replace(/`/g, "'")}\`  \n`);
    }
    return md;
  }
}
