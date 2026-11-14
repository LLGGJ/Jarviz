import * as vscode from 'vscode';
import { describeError, VenixApiError } from '../api/errors';
import type { AppSummary } from '../api/types';
import type { Category } from '../providers/ResourceStore';
import type { TreeNode } from '../providers/VenixTreeProvider';
import type { Services } from '../services';

export type NodeOf<K extends TreeNode['kind']> = Extract<TreeNode, { kind: K }>;

export function nodeOf<K extends TreeNode['kind']>(arg: unknown, kind: K): NodeOf<K> | undefined {
  if (typeof arg === 'object' && arg !== null && (arg as { kind?: unknown }).kind === kind && 'item' in arg) {
    return arg as NodeOf<K>;
  }
  return undefined;
}

/** Mostra o erro ao usuário (sem stack) e registra apenas dados não sensíveis. */
export function reportError(s: Services, context: string, err: unknown): void {
  s.logger.error(context, err instanceof VenixApiError ? `${err.kind}${err.status ? ` HTTP ${err.status}` : ''}` : err);
  if (err instanceof VenixApiError && err.kind === 'cancelled') return;
  const message = `Venix Cloud: ${describeError(err)}`;
  if (err instanceof VenixApiError && (err.kind === 'unauthorized' || err.kind === 'not-authenticated')) {
    void vscode.window.showErrorMessage(message, 'Entrar').then((choice) => {
      if (choice === 'Entrar') void vscode.commands.executeCommand('venixCloud.signIn');
    });
    return;
  }
  void vscode.window.showErrorMessage(message);
}

export interface RunOptions<T> {
  cancellable?: boolean;
  success?: string | ((result: T) => string);
  refresh?: Category[];
}

/** Executa uma operação com notificação de progresso, tratamento de erro e atualização da árvore. */
export async function run<T>(
  s: Services,
  title: string,
  fn: (signal: AbortSignal) => Promise<T>,
  options: RunOptions<T> = {},
): Promise<{ ok: true; value: T } | { ok: false }> {
  try {
    const value = await vscode.window.withProgress(
      { location: vscode.ProgressLocation.Notification, title, cancellable: options.cancellable ?? false },
      (_progress, token) => {
        const controller = new AbortController();
        token.onCancellationRequested(() => controller.abort());
        return fn(controller.signal);
      },
    );
    if (options.success) {
      const text = typeof options.success === 'function' ? options.success(value) : options.success;
      void vscode.window.showInformationMessage(`Venix Cloud: ${text}`);
    }
    if (options.refresh?.length) void s.store.refresh(...options.refresh);
    return { ok: true, value };
  } catch (err) {
    reportError(s, title, err);
    return { ok: false };
  }
}

/**
 * Confirmação explícita para operações destrutivas.
 * Com `expectedName`, o usuário precisa digitar o nome do recurso.
 */
export async function confirmDestructive(
  s: Services,
  opts: { action: string; name: string; detail: string; typeName?: boolean },
): Promise<boolean> {
  if (!s.settings().confirmDestructive) return true;
  if (opts.typeName) {
    const typed = await vscode.window.showInputBox({
      title: `${opts.action}: ${opts.name}`,
      prompt: `${opts.detail} Digite "${opts.name}" para confirmar.`,
      ignoreFocusOut: true,
      validateInput: (v) => (v === opts.name ? undefined : `Digite exatamente: ${opts.name}`),
    });
    return typed === opts.name;
  }
  const choice = await vscode.window.showWarningMessage(
    `${opts.action}: ${opts.name}?`,
    { modal: true, detail: opts.detail },
    opts.action,
  );
  return choice === opts.action;
}

export async function ensureSignedIn(s: Services): Promise<boolean> {
  if (s.auth.isSignedIn()) return true;
  const choice = await vscode.window.showWarningMessage('Venix Cloud: você não está conectado.', 'Entrar');
  if (choice === 'Entrar') await vscode.commands.executeCommand('venixCloud.signIn');
  return s.auth.isSignedIn();
}

/** Garante que os dados de uma categoria estejam carregados antes de abrir um seletor. */
export async function ensureLoaded(s: Services, category: Category): Promise<void> {
  if (s.store.snapshot[category].state === 'idle') await s.store.refresh(category);
}

export async function pickApp(s: Services, arg?: unknown, placeHolder = 'Selecione a aplicação'): Promise<AppSummary | undefined> {
  const fromNode = nodeOf(arg, 'app');
  if (fromNode) return fromNode.item;
  if (!(await ensureSignedIn(s))) return undefined;
  await ensureLoaded(s, 'apps');
  const apps = s.store.snapshot.apps.items;
  if (apps.length === 0) {
    void vscode.window.showInformationMessage('Venix Cloud: nenhuma aplicação encontrada.');
    return undefined;
  }
  const linked = s.linkedApp()?.id;
  const picked = await vscode.window.showQuickPick(
    apps.map((a) => ({ label: a.name, description: `${a.status}${a.id === linked ? ' · projeto atual' : ''}`, detail: a.id, app: a })),
    { placeHolder, matchOnDetail: true },
  );
  return picked?.app;
}

export async function askNumber(prompt: string, value: string, check: (n: number) => string | undefined): Promise<number | undefined> {
  const text = await vscode.window.showInputBox({
    prompt,
    value,
    ignoreFocusOut: true,
    validateInput: (v) => {
      const n = Number(v);
      if (v.trim() === '' || !Number.isFinite(n)) return 'Informe um número.';
      return check(n);
    },
  });
  return text === undefined ? undefined : Number(text);
}

export async function pickFolder(): Promise<vscode.WorkspaceFolder | undefined> {
  const folders = vscode.workspace.workspaceFolders;
  if (!folders || folders.length === 0) return undefined;
  if (folders.length === 1) return folders[0];
  return vscode.window.showWorkspaceFolderPick({ placeHolder: 'Selecione a pasta do projeto' });
}

