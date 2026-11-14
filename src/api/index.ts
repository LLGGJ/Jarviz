import type { VenixHttp } from './VenixApiClient';
import { AccountApi } from './modules/account';
import { ApplicationsApi } from './modules/applications';
import { DatabasesApi } from './modules/databases';
import { GamesApi } from './modules/games';
import { SnapshotsApi } from './modules/snapshots';
import { StorageApi } from './modules/storage';

/** Fachada com todos os módulos de domínio sobre um mesmo cliente HTTP. */
export class VenixApi {
  readonly account: AccountApi;
  readonly apps: ApplicationsApi;
  readonly games: GamesApi;
  readonly databases: DatabasesApi;
  readonly storage: StorageApi;
  readonly snapshots: SnapshotsApi;

  constructor(readonly http: VenixHttp) {
    this.account = new AccountApi(http);
    this.apps = new ApplicationsApi(http);
    this.games = new GamesApi(http);
    this.databases = new DatabasesApi(http);
    this.storage = new StorageApi(http);
    this.snapshots = new SnapshotsApi(http);
  }
}
