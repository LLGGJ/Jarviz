import { describeError } from '../api/errors';
import type { VenixApi } from '../api';
import { parseAccount } from '../api/modules/account';
import { parseApplications } from '../api/modules/applications';
import type { AccountInfo, AppSummary, DatabaseInstance, GameServer, SnapshotInfo, StorageObject } from '../api/types';
import { Emitter } from '../utils/events';

export type LoadState = 'idle' | 'loading' | 'loaded' | 'error';
export type Category = 'apps' | 'games' | 'databases' | 'storage' | 'snapshots';

export interface Slice<T> {
  state: LoadState;
  items: T[];
  error?: string;
  updatedAt?: number;
}

export interface StoreSnapshot {
  account?: AccountInfo;
  apps: Slice<AppSummary>;
  games: Slice<GameServer>;
  databases: Slice<DatabaseInstance>;
  storage: Slice<StorageObject>;
  snapshots: Slice<SnapshotInfo>;
  lastRefresh?: number;
  connection: 'unknown' | 'ok' | 'error';
}

const empty = <T>(): Slice<T> => ({ state: 'idle', items: [] });
export const CATEGORIES: readonly Category[] = ['apps', 'games', 'databases', 'storage', 'snapshots'];

function initial(): StoreSnapshot {
  return {
    apps: empty(),
    games: empty(),
    databases: empty(),
    storage: empty(),
    snapshots: empty(),
    connection: 'unknown',
  };
}

/** Cache central dos recursos: alimenta a árvore, a visão geral e os comandos. */
export class ResourceStore {
  private data: StoreSnapshot = initial();
  private generation = 0;
  private inflight?: Promise<void>;
  private readonly changes = new Emitter<void>();
  readonly onDidChange = this.changes.event;

  constructor(
    private readonly getApi: () => VenixApi | undefined,
    private readonly onAccount?: (account: AccountInfo) => void,
    private readonly now: () => number = Date.now,
  ) {}

  get snapshot(): Readonly<StoreSnapshot> {
    return this.data;
  }

  clear(): void {
    this.generation++;
    this.inflight = undefined;
    this.data = initial();
    this.changes.fire();
  }

  /** Atualiza tudo; chamadas concorrentes compartilham a mesma execução. */
  refreshAll(): Promise<void> {
    this.inflight ??= this.runAll().finally(() => {
      this.inflight = undefined;
    });
    return this.inflight;
  }

  async refresh(...categories: Category[]): Promise<void> {
    await Promise.all(categories.map((c) => this.load(c)));
    this.changes.fire();
  }

  findApp(id: string): AppSummary | undefined {
    return this.data.apps.items.find((a) => a.id === id);
  }

  private async runAll(): Promise<void> {
    const api = this.getApi();
    if (!api) return;
    const gen = this.generation;
    for (const c of CATEGORIES) this.mark(c, 'loading');
    this.changes.fire();
    await Promise.allSettled(CATEGORIES.map((c) => this.load(c, gen)));
    if (gen !== this.generation) return;
    const states = CATEGORIES.map((c) => this.data[c].state);
    this.data.connection = states.some((s) => s === 'loaded') ? 'ok' : 'error';
    this.data.lastRefresh = this.now();
    this.changes.fire();
  }

  private mark(category: Category, state: LoadState): void {
    (this.data[category] as Slice<unknown>).state = state;
  }

  private async load(category: Category, gen = this.generation): Promise<void> {
    const api = this.getApi();
    if (!api) return;
    this.mark(category, 'loading');
    try {
      let items: unknown[];
      switch (category) {
        case 'apps': {
          const me = await api.account.getMe();
          const status = await api.apps.status().catch(() => undefined); // endpoint opcional
          if (gen === this.generation) {
            const account = parseAccount(me);
            this.data.account = account;
            this.onAccount?.(account);
          }
          items = parseApplications(me, status);
          break;
        }
        case 'games':
          items = await api.games.list();
          break;
        case 'databases':
          items = await api.databases.list();
          break;
        case 'storage':
          items = await api.storage.list();
          break;
        case 'snapshots':
          items = await api.snapshots.list();
          break;
      }
      if (gen !== this.generation) return;
      this.data[category] = { state: 'loaded', items, updatedAt: this.now() } as never;
    } catch (err) {
      if (gen !== this.generation) return;
      this.data[category] = { state: 'error', items: [], error: describeError(err), updatedAt: this.now() } as never;
    }
    if (gen === this.generation) this.changes.fire();
  }
}
