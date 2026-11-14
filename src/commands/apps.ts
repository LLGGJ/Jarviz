import * as vscode from 'vscode';
import { VenixApiError } from '../api/errors';
import { formatStreamLine, type AppAction } from '../api/modules/applications';
import {
  checkAppName,
  checkAppRam,
  checkStartCommand,
  checkSubdomain,
  RUNTIMES,
  type Runtime,
} from '../api/validators';
import { WEB_PANEL_URL } from '../constants';
import type { Services } from '../services';
import { isSafeExternalUrl } from '../security/trustedHosts';
import { detectProject } from '../workspace/projectDetector';
import { writeLink } from '../workspace/link';
import { prepareArchive } from './archive';
import { askNumber, confirmDestructive, pickApp, pickFolder, reportError, run } from './shared';

const ACTION_TEXT: Record<AppAction, [string, string]> = {
  START: ['Iniciando', 'iniciada'],
  STOP: ['Parando', 'parada'],
  RESTART: ['Reiniciando', 'reiniciada'],
};

export async function appAction(s: Services, action: AppAction, arg?: unknown): Promise<void> {
  const app = await pickApp(s, arg);
  if (!app) return;
  const [ing, done] = ACTION_TEXT[action];
  await run(s, `Venix Cloud: ${ing} ${app.name}…`, () => s.api().apps.action(app.id, action), {
    success: `aplicação ${app.name} ${done}.`,
    refresh: ['apps'],
  });
}

export async function appDeploy(s: Services, arg?: unknown): Promise<void> {
  const app = await pickApp(s, arg);
  if (!app) return;
  const choice = await vscode.window.showWarningMessage(
    `Disparar deploy de ${app.name}?`,
    { modal: true, detail: 'Dispara um deploy zero-downtime a partir do repositório GitHub vinculado à aplicação no painel.' },
    'Disparar deploy',
  );
  if (choice !== 'Disparar deploy') return;
  await run(s, `Venix Cloud: disparando deploy de ${app.name}…`, () => s.api().apps.triggerDeploy(app.id), {
    success: `deploy de ${app.name} disparado.`,
    refresh: ['apps'],
  });
}

export async function appEditConfig(s: Services, arg?: unknown): Promise<void> {
  const app = await pickApp(s, arg);
  if (!app) return;
  const what = await vscode.window.showQuickPick(
    [
      { label: 'Memória RAM', id: 'ram', description: app.ramMb ? `${app.ramMb} MB` : undefined },
      { label: 'Comando de início', id: 'start' },
      { label: 'Runtime', id: 'runtime', description: app.runtime },
    ],
    { placeHolder: `O que alterar em ${app.name}?` },
  );
  if (!what) return;
  let body: { max_ram?: number; startCommand?: string; runtime?: Runtime } | undefined;
  if (what.id === 'ram') {
    const ram = await askNumber('Nova RAM em MB (128 a 32768)', String(app.ramMb ?? 512), checkAppRam);
    if (ram !== undefined) body = { max_ram: ram };
  } else if (what.id === 'start') {
    const cmd = await vscode.window.showInputBox({ prompt: 'Novo comando de início', ignoreFocusOut: true, validateInput: (v) => (v ? checkStartCommand(v) : 'Informe o comando.') });
    if (cmd) body = { startCommand: cmd };
  } else {
    const rt = await vscode.window.showQuickPick([...RUNTIMES], { placeHolder: 'Novo runtime' });
    if (rt) body = { runtime: rt as Runtime };
  }
  if (!body) return;
  const payload = body;
  await run(s, `Venix Cloud: atualizando ${app.name}…`, () => s.api().apps.update(app.id, payload), {
    success: `configurações de ${app.name} atualizadas.`,
    refresh: ['apps'],
  });
}

export async function appDelete(s: Services, arg?: unknown): Promise<void> {
  const app = await pickApp(s, arg, 'Aplicação a excluir');
  if (!app) return;
  const ok = await confirmDestructive(s, {
    action: 'Excluir aplicação',
    name: app.name,
    typeName: true,
    detail: 'Exclui PERMANENTEMENTE o container, os subdomínios e o volume em disco. Esta ação não pode ser desfeita.',
  });
  if (!ok) return;
  const res = await run(s, `Venix Cloud: excluindo ${app.name}…`, () => s.api().apps.remove(app.id), {
    success: `aplicação ${app.name} excluída.`,
    refresh: ['apps'],
  });
  if (res.ok && s.linkedApp()?.id === app.id) s.setLinkedApp(undefined, undefined);
}

export async function appOpenUrl(s: Services, arg?: unknown): Promise<void> {
  const app = await pickApp(s, arg);
  if (!app) return;
  if (app.url && isSafeExternalUrl(app.url)) {
    await vscode.env.openExternal(vscode.Uri.parse(app.url));
    return;
  }
  const choice = await vscode.window.showInformationMessage(
    `A API não informou um endereço web para ${app.name}. Abrir o painel da Venix Cloud?`,
    'Abrir painel',
  );
  if (choice === 'Abrir painel') await vscode.env.openExternal(vscode.Uri.parse(WEB_PANEL_URL));
}

const logChannels = new Map<string, vscode.OutputChannel>();

/** Transmite logs/console via SSE (/instances/stream/:id). Cancele pela notificação. */
export async function appLogs(s: Services, arg?: unknown): Promise<void> {
  const app = await pickApp(s, arg);
  if (!app) return;
  let channel = logChannels.get(app.id);
  if (!channel) {
    channel = vscode.window.createOutputChannel(`Venix Cloud • ${app.name}`);
    logChannels.set(app.id, channel);
    s.context.subscriptions.push(channel);
  }
  channel.clear();
  channel.show(true);
  const out = channel;
  out.appendLine(`# Logs ao vivo de ${app.name} — cancele a notificação para parar.`);
  try {
    await vscode.window.withProgress(
      { location: vscode.ProgressLocation.Notification, title: `Venix Cloud: transmitindo logs de ${app.name}`, cancellable: true },
      async (_p, token) => {
        const controller = new AbortController();
        token.onCancellationRequested(() => controller.abort());
        await s.api().http.stream(`/instances/stream/${encodeURIComponent(app.id)}`, {
          signal: controller.signal,
          onLine: (raw) => {
            const line = formatStreamLine(raw);
            if (line && line.kind !== 'metric') out.appendLine(line.text);
          },
        });
        out.appendLine(controller.signal.aborted ? '# transmissão interrompida' : '# conexão encerrada pelo servidor');
      },
    );
  } catch (err) {
    out.appendLine('# falha na transmissão');
    if (err instanceof VenixApiError) s.logger.warn(`logs: ${err.kind}`);
    reportError(s, 'Logs', err);
  }
}

export async function appCreate(s: Services): Promise<void> {
  const folder = await pickFolder();
  const detected = folder ? await detectProject(folder.uri.fsPath) : undefined;

  const name = await vscode.window.showInputBox({
    title: 'Nova aplicação (1/6): nome',
    prompt: '3 a 50 caracteres: letras, números, "_" ou "-".',
    value: detected?.suggestedName,
    ignoreFocusOut: true,
    validateInput: checkAppName,
  });
  if (!name) return;

  const runtimeItems = RUNTIMES.map((r) => ({ label: r, description: r === detected?.runtime ? `detectado (${detected.evidence.join(', ')})` : undefined }));
  runtimeItems.sort((a, b) => (a.description ? -1 : 0) - (b.description ? -1 : 0));
  const runtime = await vscode.window.showQuickPick(runtimeItems, { title: 'Nova aplicação (2/6): runtime', ignoreFocusOut: true });
  if (!runtime) return;

  const ram = await askNumber('Nova aplicação (3/6): RAM em MB (128 a 32768)', '512', checkAppRam);
  if (ram === undefined) return;

  const kind = await vscode.window.showQuickPick(
    [
      { label: 'Aplicação web', description: 'HTTPS em *.venixcloud.com', web: true },
      { label: 'Bot / worker', description: 'sem endereço web', web: false },
    ],
    { title: 'Nova aplicação (4/6): tipo', ignoreFocusOut: true },
  );
  if (!kind) return;

  let subdomain: string | undefined;
  if (kind.web) {
    subdomain = await vscode.window.showInputBox({
      title: 'Nova aplicação (5/6): subdomínio',
      prompt: 'Subdomínio único em *.venixcloud.com',
      value: name.toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 63),
      ignoreFocusOut: true,
      validateInput: checkSubdomain,
    });
    if (!subdomain) return;
  }

  const startCommand = await vscode.window.showInputBox({
    title: 'Nova aplicação (6/6): comando de início (opcional)',
    prompt: 'Deixe em branco para usar o padrão do runtime.',
    value: detected?.startCommandHint,
    ignoreFocusOut: true,
    validateInput: (v) => (v ? checkStartCommand(v) : undefined),
  });
  if (startCommand === undefined) return;

  let file: { filename: string; data: Uint8Array } | undefined;
  if (folder) {
    const include = await vscode.window.showQuickPick(
      [
        { label: `Enviar o projeto "${folder.name}"`, send: true },
        { label: 'Criar aplicação vazia', send: false },
      ],
      { title: 'Enviar código junto com a criação?', ignoreFocusOut: true },
    );
    if (!include) return;
    if (include.send) {
      const prepared = await prepareArchive(s, folder, `nova aplicação "${name}"`);
      if (!prepared) return;
      file = { filename: 'projeto.zip', data: prepared.zip };
    }
  }

  const res = await run(
    s,
    `Venix Cloud: criando ${name}…`,
    (signal) =>
      s.api().apps.create({ appName: name, runtime: runtime.label as Runtime, ram, isWeb: kind.web, subdomain: subdomain?.toLowerCase(), startCommand: startCommand || undefined, file }, signal),
    { success: `aplicação ${name} criada.`, refresh: ['apps'], cancellable: true },
  );
  if (!res.ok) return;

  const id = (res.value['id'] ?? res.value['appId'] ?? res.value['_id']) as unknown;
  if (folder && typeof id === 'string') {
    const link = await vscode.window.showInformationMessage(`Vincular esta pasta à aplicação ${name}? (cria .venix.json)`, 'Vincular');
    if (link === 'Vincular') {
      await writeLink(folder.uri.fsPath, { id, name });
      s.setLinkedApp(id, name);
    }
  }
}
