import * as vscode from 'vscode';
import type { Services } from '../services';
import { appAction, appCreate, appDelete, appDeploy, appEditConfig, appLogs, appOpenUrl } from './apps';
import { checkConnection, openSettings, openWebPanel, showAccount, signInApiToken, signInOAuth, signOut } from './auth';
import { dbCertificates, dbCreate, dbDelete } from './databases';
import { copyId, showNodeDetails } from './details';
import { gameCommand, gameCreate, gameDelete } from './games';
import { snapshotCreate, snapshotDelete, snapshotDownload } from './snapshots';
import { storageCopyUrl, storageOpenUrl, storageUpload } from './storage';
import { linkWorkspace, publishWorkspace, uploadFile } from './workspace';

type Handler = (...args: unknown[]) => unknown;

/** Tabela única de comandos: o teste de consistência a compara com o package.json. */
export function commandTable(s: Services): Record<string, Handler> {
  return {
    'venixCloud.signIn': () => signInOAuth(s),
    'venixCloud.signInManual': () => signInOAuth(s, 'manual'),
    'venixCloud.signInWithApiToken': () => signInApiToken(s),
    'venixCloud.signOut': () => signOut(s),
    'venixCloud.refresh': async () => {
      if (s.auth.isSignedIn()) await s.store.refreshAll();
    },
    'venixCloud.showAccount': () => showAccount(s),
    'venixCloud.openWebPanel': () => openWebPanel(),
    'venixCloud.checkConnection': () => checkConnection(s),
    'venixCloud.openSettings': () => openSettings(),
    'venixCloud.showDetails': (node) => showNodeDetails(s, node),
    'venixCloud.copyId': (node) => copyId(node),
    'venixCloud.appStart': (node) => appAction(s, 'START', node),
    'venixCloud.appStop': (node) => appAction(s, 'STOP', node),
    'venixCloud.appRestart': (node) => appAction(s, 'RESTART', node),
    'venixCloud.appDeploy': (node) => appDeploy(s, node),
    'venixCloud.appEditConfig': (node) => appEditConfig(s, node),
    'venixCloud.appCreate': () => appCreate(s),
    'venixCloud.appDelete': (node) => appDelete(s, node),
    'venixCloud.appLogs': (node) => appLogs(s, node),
    'venixCloud.appOpenUrl': (node) => appOpenUrl(s, node),
    'venixCloud.gameCreate': () => gameCreate(s),
    'venixCloud.gameCommand': (node) => gameCommand(s, node),
    'venixCloud.gameDelete': (node) => gameDelete(s, node),
    'venixCloud.dbCreate': () => dbCreate(s),
    'venixCloud.dbCertificates': (node) => dbCertificates(s, node),
    'venixCloud.dbDelete': (node) => dbDelete(s, node),
    'venixCloud.storageUpload': (arg) => storageUpload(s, arg),
    'venixCloud.storageCopyUrl': (node) => storageCopyUrl(s, node),
    'venixCloud.storageOpenUrl': (node) => storageOpenUrl(s, node),
    'venixCloud.snapshotCreate': (node) => snapshotCreate(s, node),
    'venixCloud.snapshotDownload': (node) => snapshotDownload(s, node),
    'venixCloud.snapshotDelete': (node) => snapshotDelete(s, node),
    'venixCloud.linkWorkspace': (node) => linkWorkspace(s, node),
    'venixCloud.publishWorkspace': (node) => publishWorkspace(s, node),
    'venixCloud.uploadFile': (arg) => uploadFile(s, arg),
  };
}

export function registerCommands(s: Services): vscode.Disposable[] {
  return Object.entries(commandTable(s)).map(([id, handler]) => vscode.commands.registerCommand(id, handler));
}
