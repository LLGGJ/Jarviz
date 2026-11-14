"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ResourceStore = exports.CATEGORIES = void 0;
const errors_1 = require("../api/errors");
const account_1 = require("../api/modules/account");
const applications_1 = require("../api/modules/applications");
const events_1 = require("../utils/events");
const empty = () => ({ state: 'idle', items: [] });
exports.CATEGORIES = ['apps', 'games', 'databases', 'storage', 'snapshots'];
function initial() {
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
class ResourceStore {
    getApi;
    onAccount;
    now;
    data = initial();
    generation = 0;
    inflight;
    changes = new events_1.Emitter();
    onDidChange = this.changes.event;
    constructor(getApi, onAccount, now = Date.now) {
        this.getApi = getApi;
        this.onAccount = onAccount;
        this.now = now;
    }
    get snapshot() {
        return this.data;
    }
    clear() {
        this.generation++;
        this.inflight = undefined;
        this.data = initial();
        this.changes.fire();
    }
    /** Atualiza tudo; chamadas concorrentes compartilham a mesma execução. */
    refreshAll() {
        this.inflight ??= this.runAll().finally(() => {
            this.inflight = undefined;
        });
        return this.inflight;
    }
    async refresh(...categories) {
        await Promise.all(categories.map((c) => this.load(c)));
        this.changes.fire();
    }
    findApp(id) {
        return this.data.apps.items.find((a) => a.id === id);
    }
    async runAll() {
        const api = this.getApi();
        if (!api)
            return;
        const gen = this.generation;
        for (const c of exports.CATEGORIES)
            this.mark(c, 'loading');
        this.changes.fire();
        await Promise.allSettled(exports.CATEGORIES.map((c) => this.load(c, gen)));
        if (gen !== this.generation)
            return;
        const states = exports.CATEGORIES.map((c) => this.data[c].state);
        this.data.connection = states.some((s) => s === 'loaded') ? 'ok' : 'error';
        this.data.lastRefresh = this.now();
        this.changes.fire();
    }
    mark(category, state) {
        this.data[category].state = state;
    }
    async load(category, gen = this.generation) {
        const api = this.getApi();
        if (!api)
            return;
        this.mark(category, 'loading');
        try {
            let items;
            switch (category) {
                case 'apps': {
                    const me = await api.account.getMe();
                    const status = await api.apps.status().catch(() => undefined); // endpoint opcional
                    if (gen === this.generation) {
                        const account = (0, account_1.parseAccount)(me);
                        this.data.account = account;
                        this.onAccount?.(account);
                    }
                    items = (0, applications_1.parseApplications)(me, status);
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
            if (gen !== this.generation)
                return;
            this.data[category] = { state: 'loaded', items, updatedAt: this.now() };
        }
        catch (err) {
            if (gen !== this.generation)
                return;
            this.data[category] = { state: 'error', items: [], error: (0, errors_1.describeError)(err), updatedAt: this.now() };
        }
        if (gen === this.generation)
            this.changes.fire();
    }
}
exports.ResourceStore = ResourceStore;
//# sourceMappingURL=ResourceStore.js.map