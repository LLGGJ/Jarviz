import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { checkCdnSize, checkExpireDays, checkObjectName, checkObjectPrefix } from '../api/validators';
import { isSafeExternalUrl } from '../security/trustedHosts';
import { isSecretFile } from '../security/secretFiles';
import type { Services } from '../services';
import { suggestObjectName } from '../workspace/projectDetector';
import { ensureSignedIn, nodeOf, run } from './shared';

export async function pickLocalFile(arg: unknown, title: string): Promise<vscode.Uri | undefined> {
  if (arg instanceof vscode.Uri) return arg;
  const picked = await vscode.window.showOpenDialog({ canSelectMany: false, canSelectFolders: false, title, defaultUri: vscode.window.activeTextEditor?.document.uri });
  return picked?.[0];
}

/** Arquivos que parecem conter segredos exigem confirmação explícita (nunca são enviados automaticamente). */
export async function confirmSecretFile(fileName: string): Promise<boolean> {
  if (!isSecretFile(fileName)) return true;
  const choice = await vscode.window.showWarningMessage(
    `"${fileName}" parece conter segredos (chaves, credenciais ou variáveis de ambiente).`,
    { modal: true, detail: 'Enviar para um destino público ou compartilhado pode expor credenciais.' },
    'Enviar mesmo assim',
  );
  return choice === 'Enviar mesmo assim';
}

export async function storageUpload(s: Services, arg?: unknown): Promise<void> {
  if (!(await ensureSignedIn(s))) return;
  const uri = await pickLocalFile(arg, 'Arquivo para o CDN (1 KB a 100 MB)');
  if (!uri) return;
  const stat = await fs.stat(uri.fsPath);
  const sizeProblem = checkCdnSize(stat.size);
  if (sizeProblem) {
    void vscode.window.showErrorMessage(`Venix Cloud: ${sizeProblem}`);
    return;
  }
  const base = path.basename(uri.fsPath);
  if (!(await confirmSecretFile(base))) return;

  const name = await vscode.window.showInputBox({
    title: 'Upload para o CDN (1/3): nome do objeto',
    prompt: '3 a 32 caracteres: letras, números e "_".',
    value: suggestObjectName(base),
    ignoreFocusOut: true,
    validateInput: checkObjectName,
  });
  if (!name) return;
  const prefix = await vscode.window.showInputBox({
    title: 'Upload para o CDN (2/3): pasta/prefixo (opcional)',
    ignoreFocusOut: true,
    validateInput: (v) => (v ? checkObjectPrefix(v) : undefined),
  });
  if (prefix === undefined) return;
  const expireText = await vscode.window.showInputBox({
    title: 'Upload para o CDN (3/3): expiração em dias (opcional, 1 a 365)',
    ignoreFocusOut: true,
    validateInput: (v) => (v ? (Number.isFinite(Number(v)) ? checkExpireDays(Number(v)) : 'Informe um número.') : undefined),
  });
  if (expireText === undefined) return;

  const data = new Uint8Array(await fs.readFile(uri.fsPath));
  const res = await run(
    s,
    `Venix Cloud: enviando ${base}…`,
    (signal) => s.api().storage.upload({ name, prefix: prefix || undefined, expireDays: expireText ? Number(expireText) : undefined, file: { filename: base, data } }, signal),
    { cancellable: true, refresh: ['storage'] },
  );
  if (!res.ok) return;
  const choice = await vscode.window.showInformationMessage(`Venix Cloud: upload concluído.\n${res.value.url}`, 'Copiar URL', 'Abrir');
  if (choice === 'Copiar URL') await vscode.env.clipboard.writeText(res.value.url);
  if (choice === 'Abrir' && isSafeExternalUrl(res.value.url)) await vscode.env.openExternal(vscode.Uri.parse(res.value.url));
}

export async function storageCopyUrl(_s: Services, arg?: unknown): Promise<void> {
  const node = nodeOf(arg, 'object');
  if (!node?.item.url) {
    void vscode.window.showWarningMessage('Venix Cloud: este objeto não informou uma URL.');
    return;
  }
  await vscode.env.clipboard.writeText(node.item.url);
  void vscode.window.showInformationMessage('Venix Cloud: URL copiada para a área de transferência.');
}

export async function storageOpenUrl(_s: Services, arg?: unknown): Promise<void> {
  const url = nodeOf(arg, 'object')?.item.url;
  if (!url || !isSafeExternalUrl(url)) {
    void vscode.window.showWarningMessage('Venix Cloud: URL indisponível ou insegura.');
    return;
  }
  await vscode.env.openExternal(vscode.Uri.parse(url));
}
