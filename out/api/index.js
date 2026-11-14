"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.VenixApi = void 0;
const account_1 = require("./modules/account");
const applications_1 = require("./modules/applications");
const databases_1 = require("./modules/databases");
const games_1 = require("./modules/games");
const snapshots_1 = require("./modules/snapshots");
const storage_1 = require("./modules/storage");
/** Fachada com todos os módulos de domínio sobre um mesmo cliente HTTP. */
class VenixApi {
    http;
    account;
    apps;
    games;
    databases;
    storage;
    snapshots;
    constructor(http) {
        this.http = http;
        this.account = new account_1.AccountApi(http);
        this.apps = new applications_1.ApplicationsApi(http);
        this.games = new games_1.GamesApi(http);
        this.databases = new databases_1.DatabasesApi(http);
        this.storage = new storage_1.StorageApi(http);
        this.snapshots = new snapshots_1.SnapshotsApi(http);
    }
}
exports.VenixApi = VenixApi;
//# sourceMappingURL=index.js.map