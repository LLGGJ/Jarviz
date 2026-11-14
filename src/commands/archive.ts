import * as vscode from 'vscode';
import { LIMITS } from '../constants';
import { formatBytes } from '../utils/format';
import { scanProject, type ScanResult } from '../workspace/scanner';
import { buildPreviewListing, summarizeBlocked } from '../workspace/preview';
import { buildZip, ZipLimitError } from '../workspace/zip';
import type { Services } from '../services';
import { reportError } from './shared';

/**
 * Varre o projeto, mostra a pré-visualização e só então gera o ZIP.
 * Devolve undefined se o usuário cancelar ou houver erro.
 */
export async function prepareArchive(
  s: Services,
  folder: vscode.WorkspaceFolder,
  target: string,
): Promise<{ zip: Buffer; scan: ScanResult } | undefined> {
  if (!vscode.workspace.isTrusted) {
    void vscode.window.showWarningMessage('Venix Cloud: confie neste workspace para publicar arquivos.');
    return undefined;
  }
  let scan: ScanResult;
  try {
    scan = await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: 'Venix Cloud: analisando o projeto…' }, () =>
      scanProject(folder.uri.fsPath, { exclude: s.settings().publishExclude }),
    );
  } catch (err) {
    reportError(s, 'Análise do projeto', err);
    return undefined;
  }
  if (scan.files.length === 0) {
    void vscode.window.showWarningMessage('Venix Cloud: nenhum arquivo elegível para envio (tudo foi excluído ou bloqueado).');
    return undefined;
  }

  for (;;) {
    const send = `$(cloud-upload) Enviar ${scan.files.length} arquivos (${formatBytes(scan.totalBytes)})`;
    const list = '$(list-flat) Ver lista completa de arquivos';
    const picked = await vscode.window.showQuickPick(
      [
        { label: send, description: target, detail: summarizeBlocked(scan) },
        { label: list },
        { label: '$(close) Cancelar' },
      ],
      { title: 'Pré-visualização da publicação', placeHolder: 'Revise antes de enviar', ignoreFocusOut: true },
    );
    if (!picked || picked.label.includes('Cancelar')) return undefined;
    if (picked.label === list) {
      const doc = await vscode.workspace.openTextDocument({ content: buildPreviewListing(scan, target), language: 'plaintext' });
      await vscode.window.showTextDocument(doc, { preview: true });
      continue;
    }
    break;
  }

  try {
    const zip = await vscode.window.withProgress(
      { location: vscode.ProgressLocation.Notification, title: 'Venix Cloud: compactando…', cancellable: true },
      (_p, token) => {
        const controller = new AbortController();
        token.onCancellationRequested(() => controller.abort());
        return buildZip(scan.files, { maxBytes: LIMITS.appArchiveMaxBytes, signal: controller.signal });
      },
    );
    return { zip, scan };
  } catch (err) {
    if (err instanceof ZipLimitError) void vscode.window.showErrorMessage(`Venix Cloud: ${err.message}`);
    else if (!(err instanceof Error && err.message === 'cancelado')) reportError(s, 'Compactação', err);
    return undefined;
  }
}
