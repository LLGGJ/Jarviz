import * as vscode from 'vscode';
import { isSafeExternalUrl } from '../security/trustedHosts';
import { buildDetailEntries, toPublicFields, type DetailEntry } from '../utils/details';
import type { RawRecord } from '../api/types';
import { buildWebviewHtml } from './webviewHtml';
import { parseDetailsMessage } from './messages';

export interface DetailsInput {
  panelKey: string;
  title: string;
  subtitle?: string;
  raw: RawRecord;
  preferredOrder?: readonly string[];
}

const CLIPBOARD_CLEAR_MS = 30_000;
const panels = new Map<string, vscode.WebviewPanel>();

/**
 * Painel de detalhes. Valores sensíveis chegam MASCARADOS ao webview; o valor real só
 * é enviado quando o usuário clica em "Revelar", e a cópia é feita pela extensão
 * (o valor nunca passa pelo DOM). Chaves recebidas do webview são validadas.
 */
export function showDetails(extensionUri: vscode.Uri, input: DetailsInput): void {
  const existing = panels.get(input.panelKey);
  if (existing) {
    existing.reveal();
    return;
  }
  const entries = buildDetailEntries(input.raw, input.preferredOrder);
  const byKey = new Map<string, DetailEntry>(entries.map((e) => [e.key, e]));
  const validKeys = new Set(byKey.keys());

  const panel = vscode.window.createWebviewPanel('venixCloud.details', input.title, vscode.ViewColumn.Active, {
    enableScripts: true,
    localResourceRoots: [vscode.Uri.joinPath(extensionUri, 'media')],
  });
  panels.set(input.panelKey, panel);
  panel.webview.html = buildWebviewHtml(panel.webview, extensionUri, {
    title: input.title,
    script: 'details.js',
    bodyHtml: '<main id="root"><p class="muted">Carregando…</p></main>',
  });

  const send = (): void => {
    void panel.webview.postMessage({ type: 'init', title: input.title, subtitle: input.subtitle ?? '', fields: toPublicFields(entries) });
  };

  const sub = panel.webview.onDidReceiveMessage(async (raw: unknown) => {
    const msg = parseDetailsMessage(raw, validKeys);
    if (!msg) return;
    if (msg.type === 'ready') {
      send();
      return;
    }
    const entry = byKey.get(msg.key);
    if (!entry) return;
    switch (msg.type) {
      case 'reveal':
        if (entry.sensitive) void panel.webview.postMessage({ type: 'value', key: entry.key, value: entry.value });
        break;
      case 'hide':
        void panel.webview.postMessage({ type: 'value', key: entry.key, value: entry.display });
        break;
      case 'copy':
        await vscode.env.clipboard.writeText(entry.value);
        void vscode.window.showInformationMessage(
          entry.sensitive ? `"${entry.label}" copiado. A área de transferência será limpa em 30 s.` : `"${entry.label}" copiado.`,
        );
        if (entry.sensitive) {
          setTimeout(() => {
            void vscode.env.clipboard.readText().then((current) => {
              if (current === entry.value) void vscode.env.clipboard.writeText('');
            });
          }, CLIPBOARD_CLEAR_MS);
        }
        break;
      case 'open':
        if (!entry.sensitive && isSafeExternalUrl(entry.value)) await vscode.env.openExternal(vscode.Uri.parse(entry.value));
        break;
    }
  });
  panel.onDidDispose(() => {
    sub.dispose();
    panels.delete(input.panelKey);
  });
}
