import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { checkRemotePath } from '../api/validators';
import { LIMITS, REMOTE_UPDATE_ZIP, STATE_LAST_PUSH_KEY } from '../constants';
import type { Services } from '../services';
import { formatTime } from '../utils/format';
import { readLink, writeLink } from '../workspace/link';
import { prepareArchive } from './archive';
import { confirmSecretFile, pickLocalFile } from './storage';
import { ensureLoaded, ensureSignedIn, nodeOf, pickApp, pickFolder, run } from './shared';

/** Vincula a pasta a uma aplicação remota (`.venix.json`, compatível com `venix link`). */
export async function linkWorkspace(s: Services, arg?: unknown): Promise<void> {
  const folder = await pickFolder();
  if (!folder) {
    void vscode.window.showWarningMessage('Venix Cloud: abra uma pasta no VS Code primeiro.');
    return;
  }
  const app = await pickApp(s, nodeOf(arg, 'app') ? arg : undefined, 'Aplicação de destino para este projeto');
  if (!app) return;
  await writeLink(folder.uri.fsPath, { id: app.id, name: app.name });
  s.setLinkedApp(app.id, app.name);
  void vscode.window.showInformationMessage(`Venix Cloud: projeto vinculado a ${app.name} (.venix.json). Não versione esse arquivo se o repositório for público.`);
}

/**
 * Publica o projeto: ZIP → upload (/files/upload) → extração (/files/extract) → reinício.
 * Mesmo fluxo do `venix push` da CLI oficial, usando apenas endpoints documentados.
 */
export async function publishWorkspace(s: Services, arg?: unknown): Promise<void> {
  if (!(await ensureSignedIn(s))) return;
  const folder = await pickFolder();
  if (!folder) {
    void vscode.window.showWarningMessage('Venix Cloud: abra uma pasta no VS Code primeiro.');
    return;
  }
  await ensureLoaded(s, 'apps');
  const link = await readLink(folder.uri.fsPath);
  let app = nodeOf(arg, 'app')?.item ?? (link ? s.store.findApp(link.id) : undefined);
  if (!app) app = await pickApp(s, undefined, 'Aplicação de destino da publicação');
  if (!app) return;
  const target = app;

  const prepared = await prepareArchive(s, folder, `${target.name} (${target.id})`);
  if (!prepared) return;
  const restart = s.settings().restartAfterUpload;

  const res = await run(
    s,
    `Venix Cloud: publicando em ${target.name}…`,
    async (signal) => {
      const api = s.api();
      await api.apps.uploadFile(target.id, REMOTE_UPDATE_ZIP, { filename: 'update.zip', data: prepared.zip }, signal);
      await api.apps.extract(target.id, REMOTE_UPDATE_ZIP, signal);
      // Limpeza do pacote temporário: melhor esforço, não afeta o resultado.
      await api.apps.deleteFile(target.id, REMOTE_UPDATE_ZIP).catch(() => undefined);
      if (restart) await api.apps.action(target.id, 'RESTART');
    },
    { cancellable: true, success: `${prepared.scan.files.length} arquivos enviados para ${target.name}${restart ? ' e aplicação reiniciada' : ''}.`, refresh: ['apps'] },
  );
  if (!res.ok) return;
  await s.context.workspaceState.update(STATE_LAST_PUSH_KEY, { appId: target.id, appName: target.name, at: Date.now(), files: prepared.scan.files.length });
  if (!link || link.id !== target.id) {
    const choice = await vscode.window.showInformationMessage(`Vincular esta pasta a ${target.name} para os próximos envios?`, 'Vincular');
    if (choice === 'Vincular') {
      await writeLink(folder.uri.fsPath, { id: target.id, name: target.name });
      s.setLinkedApp(target.id, target.name);
    }
  }
}

/** Envia UM arquivo escolhido pelo usuário para o volume da aplicação. */
export async function uploadFile(s: Services, arg?: unknown): Promise<void> {
  if (!(await ensureSignedIn(s))) return;
  const uri = await pickLocalFile(arg, 'Arquivo para enviar à aplicação');
  if (!uri) return;
  const stat = await fs.stat(uri.fsPath);
  if (stat.size > LIMITS.appArchiveMaxBytes) {
    void vscode.window.showErrorMessage('Venix Cloud: o arquivo excede o limite de 500 MB.');
    return;
  }
  const base = path.basename(uri.fsPath);
  if (!(await confirmSecretFile(base))) return;
  const app = await pickApp(s, undefined, 'Aplicação de destino');
  if (!app) return;
  const remote = await vscode.window.showInputBox({
    title: `Enviar ${base} para ${app.name}`,
    prompt: 'Caminho remoto completo do arquivo no volume da aplicação.',
    value: `/${base}`,
    ignoreFocusOut: true,
    validateInput: checkRemotePath,
  });
  if (!remote) return;
  const data = new Uint8Array(await fs.readFile(uri.fsPath));
  await run(s, `Venix Cloud: enviando ${base}…`, (signal) => s.api().apps.uploadFile(app.id, remote, { filename: base, data }, signal), {
    cancellable: true,
    success: `${base} enviado para ${app.name}:${remote}.`,
  });
}

export function describeLastPush(s: Services): string | undefined {
  const last = s.context.workspaceState.get<{ appName: string; at: number; files: number }>(STATE_LAST_PUSH_KEY);
  return last ? `Último envio (registro local): ${last.files} arquivos para ${last.appName} em ${formatTime(last.at)}` : undefined;
}

