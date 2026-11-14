import { randomBytes } from 'node:crypto';
import * as vscode from 'vscode';
import { escapeHtml } from '../auth/callbackPage';

/** HTML base com CSP restritiva: sem scripts inline, sem recursos externos. */
export function buildWebviewHtml(
  webview: vscode.Webview,
  extensionUri: vscode.Uri,
  options: { title: string; script: string; bodyHtml: string },
): string {
  const nonce = randomBytes(16).toString('base64');
  const asUri = (file: string): vscode.Uri => webview.asWebviewUri(vscode.Uri.joinPath(extensionUri, 'media', 'webview', file));
  const csp = [
    "default-src 'none'",
    `style-src ${webview.cspSource}`,
    `script-src 'nonce-${nonce}'`,
    `img-src ${webview.cspSource} data:`,
    `font-src ${webview.cspSource}`,
  ].join('; ');
  return `<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="${csp}">
<meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="${asUri('styles.css')}">
<title>${escapeHtml(options.title)}</title></head>
<body>${options.bodyHtml}
<script nonce="${nonce}" src="${asUri(options.script)}"></script></body></html>`;
}
