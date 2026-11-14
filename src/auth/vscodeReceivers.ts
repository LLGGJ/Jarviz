import * as vscode from 'vscode';
import { OAUTH_CALLBACK_PATH } from '../constants';
import type { CallbackParams, CallbackReceiver, CallbackResult } from './OAuthProvider';

type Handler = (params: CallbackParams) => CallbackResult;

/** Roteia `vscode://<publisher>.<extensão>/callback` para o login em andamento (modo "vscode"). */
export class UriCallbackRouter implements vscode.UriHandler {
  private pending?: Handler;

  handleUri(uri: vscode.Uri): void {
    if (uri.path !== OAUTH_CALLBACK_PATH) return;
    if (!this.pending) {
      void vscode.window.showWarningMessage('Venix Cloud: nenhum login em andamento. Inicie com "Venix Cloud: Entrar".');
      return;
    }
    const params: CallbackParams = {};
    for (const [k, v] of new URLSearchParams(uri.query)) {
      if (!(k in params)) params[k] = v;
    }
    const result = this.pending(params);
    if (!result.ok) void vscode.window.showErrorMessage(`Venix Cloud: ${result.message}`);
  }

  createReceiver(redirectUri: string): CallbackReceiver {
    return {
      redirectUri,
      listen: (handler) => {
        this.pending = handler;
        return Promise.resolve();
      },
      dispose: () => {
        this.pending = undefined;
      },
    };
  }
}

/**
 * Fallback para ambientes onde o navegador não alcança 127.0.0.1 (SSH, WSL, Codespaces)
 * ou a porta está ocupada: o usuário cola a URL final. O `state` é validado igualmente.
 */
export class ManualReceiver implements CallbackReceiver {
  private box?: vscode.InputBox;

  constructor(readonly redirectUri: string) {}

  listen(handler: Handler, context: { authUrl: string }): Promise<void> {
    const box = vscode.window.createInputBox();
    this.box = box;
    box.title = 'Venix Cloud — Entrar (modo manual)';
    box.prompt = 'Autorize no navegador e cole aqui a URL final da página (mesmo que ela mostre erro de conexão).';
    box.placeholder = `${this.redirectUri}?code=…&state=…`;
    box.ignoreFocusOut = true;
    box.buttons = [{ iconPath: new vscode.ThemeIcon('copy'), tooltip: 'Copiar link de autorização' }];
    box.onDidTriggerButton(() => {
      void vscode.env.clipboard.writeText(context.authUrl);
      void vscode.window.showInformationMessage('Link de autorização copiado.');
    });
    box.onDidAccept(() => {
      const text = box.value.trim();
      let url: URL;
      try {
        url = new URL(text);
      } catch {
        box.validationMessage = 'Cole a URL completa, começando com http://…';
        return;
      }
      const expected = new URL(this.redirectUri);
      if (url.origin !== expected.origin || url.pathname !== expected.pathname) {
        box.validationMessage = `A URL deve começar com ${this.redirectUri}`;
        return;
      }
      const params: CallbackParams = {};
      for (const [k, v] of url.searchParams) {
        if (!(k in params)) params[k] = v;
      }
      const result = handler(params);
      if (result.ok) box.hide();
      else box.validationMessage = result.message;
    });
    box.onDidHide(() => box.dispose());
    box.show();
    return Promise.resolve();
  }

  dispose(): void {
    this.box?.dispose();
    this.box = undefined;
  }
}
