import * as vscode from 'vscode';
import { describeError } from '../api/errors';
import { OAuthError, type CallbackReceiver } from '../auth/OAuthProvider';
import { LoopbackReceiver } from '../auth/CallbackServer';
import { ManualReceiver } from '../auth/vscodeReceivers';
import { validateApiToken } from '../auth/AuthenticationManager';
import { OAUTH_CALLBACK_PATH, WEB_PANEL_URL } from '../constants';
import type { Services } from '../services';
import { formatTime } from '../utils/format';
import { ensureSignedIn, reportError } from './shared';

type Mode = 'auto' | 'manual';

function redirectUriFor(s: Services, mode: Mode): { uri: string; kind: 'loopback' | 'vscode' } {
  const settings = s.settings();
  if (mode === 'auto' && settings.redirectMode === 'vscode') {
    return { uri: `${vscode.env.uriScheme}://${s.context.extension.id}${OAUTH_CALLBACK_PATH}`, kind: 'vscode' };
  }
  return { uri: `http://127.0.0.1:${settings.callbackPort}${OAUTH_CALLBACK_PATH}`, kind: 'loopback' };
}

async function openBrowser(url: string): Promise<void> {
  const opened = await vscode.env.openExternal(vscode.Uri.parse(url, true));
  if (!opened) {
    const choice = await vscode.window.showWarningMessage('Venix Cloud: não consegui abrir o navegador.', 'Copiar link');
    if (choice === 'Copiar link') await vscode.env.clipboard.writeText(url);
  }
}

export async function signInOAuth(s: Services, mode: Mode = 'auto'): Promise<void> {
  if (s.auth.isSignedIn()) {
    const choice = await vscode.window.showInformationMessage('Você já está conectado à Venix Cloud.', 'Trocar de conta');
    if (choice !== 'Trocar de conta') return;
  }
  const { uri, kind } = redirectUriFor(s, mode);
  let receiver: CallbackReceiver;
  if (mode === 'manual') receiver = new ManualReceiver(uri);
  else if (kind === 'vscode') receiver = s.uriRouter.createReceiver(uri);
  else receiver = new LoopbackReceiver(s.settings().callbackPort);

  try {
    const info = await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: mode === 'manual' ? 'Venix Cloud: cole a URL final no campo exibido…' : 'Venix Cloud: aguardando autorização no navegador…',
        cancellable: true,
      },
      (_p, token) => {
        const controller = new AbortController();
        token.onCancellationRequested(() => controller.abort());
        return s.auth.signInWithOAuth({ receiver, openUrl: openBrowser, signal: controller.signal });
      },
    );
    void vscode.window.showInformationMessage(`Venix Cloud: conectado como ${info.account.label}.`);
    void s.store.refreshAll();
  } catch (err) {
    if (err instanceof OAuthError && err.kind === 'cancelled') return;
    if (err instanceof OAuthError && err.kind === 'listen-failed' && mode === 'auto' && kind === 'loopback') {
      s.logger.warn(`Loopback indisponível: ${err.message}`);
      const choice = await vscode.window.showWarningMessage(
        `Venix Cloud: ${err.message} Você pode concluir o login colando a URL final manualmente.`,
        'Entrar manualmente',
      );
      if (choice === 'Entrar manualmente') await signInOAuth(s, 'manual');
      return;
    }
    reportError(s, 'Login OAuth', err);
  }
}

/** Alternativa documentada: token de API gerado no painel. NÃO é OAuth. */
export async function signInApiToken(s: Services): Promise<void> {
  const token = await vscode.window.showInputBox({
    title: 'Venix Cloud — Token de API',
    prompt: 'Cole o token gerado no painel da Venix Cloud. Ele será guardado no armazenamento seguro do VS Code.',
    password: true,
    ignoreFocusOut: true,
    validateInput: (v) => (v.trim() === '' ? undefined : validateApiToken(v.trim())),
  });
  if (!token?.trim()) return;
  try {
    const info = await s.auth.signInWithApiToken(token);
    void vscode.window.showInformationMessage(`Venix Cloud: conectado com token de API (${info.account.label}).`);
    void s.store.refreshAll();
  } catch (err) {
    void vscode.window.showErrorMessage(`Venix Cloud: ${describeError(err)}`);
  }
}

export async function signOut(s: Services): Promise<void> {
  if (!s.auth.isSignedIn()) {
    void vscode.window.showInformationMessage('Venix Cloud: você não está conectado.');
    return;
  }
  await s.auth.signOut();
  s.store.clear();
  void vscode.window.showInformationMessage('Venix Cloud: sessão encerrada e dados locais removidos.');
}

export async function showAccount(s: Services): Promise<void> {
  const info = s.auth.getSessionInfo();
  if (!info) {
    const choice = await vscode.window.showInformationMessage('Venix Cloud: você não está conectado.', 'Entrar');
    if (choice === 'Entrar') await signInOAuth(s);
    return;
  }
  const lines = [
    `Conta: ${info.account.label}`,
    info.account.email ? `E-mail: ${info.account.email}` : undefined,
    info.account.plan ? `Plano: ${info.account.plan}` : undefined,
    `Autenticação: ${info.method === 'oauth' ? 'OAuth 2.0 com PKCE' : 'Token de API (não é OAuth)'}`,
    info.expiresAt ? `Sessão expira em: ${formatTime(info.expiresAt)}` : 'Expiração: não informada pelo servidor',
  ].filter(Boolean);
  const choice = await vscode.window.showInformationMessage(lines.join('\n'), { modal: true }, 'Sair');
  if (choice === 'Sair') await signOut(s);
}

export async function checkConnection(s: Services): Promise<void> {
  if (!(await ensureSignedIn(s))) return;
  const started = Date.now();
  try {
    await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: 'Venix Cloud: verificando conexão…' }, () =>
      s.api().account.getMe(),
    );
    void vscode.window.showInformationMessage(`Venix Cloud: conexão OK (${Date.now() - started} ms) • ${s.settings().apiBaseUrl}`);
  } catch (err) {
    reportError(s, 'Verificação de conexão', err);
  }
}

export async function openWebPanel(): Promise<void> {
  await vscode.env.openExternal(vscode.Uri.parse(WEB_PANEL_URL));
}

export async function openSettings(): Promise<void> {
  await vscode.commands.executeCommand('workbench.action.openSettings', '@ext:venixcloud.venix-cloud');
}
