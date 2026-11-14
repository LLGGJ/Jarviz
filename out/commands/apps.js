"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.appAction = appAction;
exports.appDeploy = appDeploy;
exports.appEditConfig = appEditConfig;
exports.appDelete = appDelete;
exports.appOpenUrl = appOpenUrl;
exports.appLogs = appLogs;
exports.appCreate = appCreate;
const vscode = __importStar(require("vscode"));
const errors_1 = require("../api/errors");
const applications_1 = require("../api/modules/applications");
const validators_1 = require("../api/validators");
const constants_1 = require("../constants");
const trustedHosts_1 = require("../security/trustedHosts");
const projectDetector_1 = require("../workspace/projectDetector");
const link_1 = require("../workspace/link");
const archive_1 = require("./archive");
const shared_1 = require("./shared");
const ACTION_TEXT = {
    START: ['Iniciando', 'iniciada'],
    STOP: ['Parando', 'parada'],
    RESTART: ['Reiniciando', 'reiniciada'],
};
async function appAction(s, action, arg) {
    const app = await (0, shared_1.pickApp)(s, arg);
    if (!app)
        return;
    const [ing, done] = ACTION_TEXT[action];
    await (0, shared_1.run)(s, `Venix Cloud: ${ing} ${app.name}…`, () => s.api().apps.action(app.id, action), {
        success: `aplicação ${app.name} ${done}.`,
        refresh: ['apps'],
    });
}
async function appDeploy(s, arg) {
    const app = await (0, shared_1.pickApp)(s, arg);
    if (!app)
        return;
    const choice = await vscode.window.showWarningMessage(`Disparar deploy de ${app.name}?`, { modal: true, detail: 'Dispara um deploy zero-downtime a partir do repositório GitHub vinculado à aplicação no painel.' }, 'Disparar deploy');
    if (choice !== 'Disparar deploy')
        return;
    await (0, shared_1.run)(s, `Venix Cloud: disparando deploy de ${app.name}…`, () => s.api().apps.triggerDeploy(app.id), {
        success: `deploy de ${app.name} disparado.`,
        refresh: ['apps'],
    });
}
async function appEditConfig(s, arg) {
    const app = await (0, shared_1.pickApp)(s, arg);
    if (!app)
        return;
    const what = await vscode.window.showQuickPick([
        { label: 'Memória RAM', id: 'ram', description: app.ramMb ? `${app.ramMb} MB` : undefined },
        { label: 'Comando de início', id: 'start' },
        { label: 'Runtime', id: 'runtime', description: app.runtime },
    ], { placeHolder: `O que alterar em ${app.name}?` });
    if (!what)
        return;
    let body;
    if (what.id === 'ram') {
        const ram = await (0, shared_1.askNumber)('Nova RAM em MB (128 a 32768)', String(app.ramMb ?? 512), validators_1.checkAppRam);
        if (ram !== undefined)
            body = { max_ram: ram };
    }
    else if (what.id === 'start') {
        const cmd = await vscode.window.showInputBox({ prompt: 'Novo comando de início', ignoreFocusOut: true, validateInput: (v) => (v ? (0, validators_1.checkStartCommand)(v) : 'Informe o comando.') });
        if (cmd)
            body = { startCommand: cmd };
    }
    else {
        const rt = await vscode.window.showQuickPick([...validators_1.RUNTIMES], { placeHolder: 'Novo runtime' });
        if (rt)
            body = { runtime: rt };
    }
    if (!body)
        return;
    const payload = body;
    await (0, shared_1.run)(s, `Venix Cloud: atualizando ${app.name}…`, () => s.api().apps.update(app.id, payload), {
        success: `configurações de ${app.name} atualizadas.`,
        refresh: ['apps'],
    });
}
async function appDelete(s, arg) {
    const app = await (0, shared_1.pickApp)(s, arg, 'Aplicação a excluir');
    if (!app)
        return;
    const ok = await (0, shared_1.confirmDestructive)(s, {
        action: 'Excluir aplicação',
        name: app.name,
        typeName: true,
        detail: 'Exclui PERMANENTEMENTE o container, os subdomínios e o volume em disco. Esta ação não pode ser desfeita.',
    });
    if (!ok)
        return;
    const res = await (0, shared_1.run)(s, `Venix Cloud: excluindo ${app.name}…`, () => s.api().apps.remove(app.id), {
        success: `aplicação ${app.name} excluída.`,
        refresh: ['apps'],
    });
    if (res.ok && s.linkedApp()?.id === app.id)
        s.setLinkedApp(undefined, undefined);
}
async function appOpenUrl(s, arg) {
    const app = await (0, shared_1.pickApp)(s, arg);
    if (!app)
        return;
    if (app.url && (0, trustedHosts_1.isSafeExternalUrl)(app.url)) {
        await vscode.env.openExternal(vscode.Uri.parse(app.url));
        return;
    }
    const choice = await vscode.window.showInformationMessage(`A API não informou um endereço web para ${app.name}. Abrir o painel da Venix Cloud?`, 'Abrir painel');
    if (choice === 'Abrir painel')
        await vscode.env.openExternal(vscode.Uri.parse(constants_1.WEB_PANEL_URL));
}
const logChannels = new Map();
/** Transmite logs/console via SSE (/instances/stream/:id). Cancele pela notificação. */
async function appLogs(s, arg) {
    const app = await (0, shared_1.pickApp)(s, arg);
    if (!app)
        return;
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
        await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: `Venix Cloud: transmitindo logs de ${app.name}`, cancellable: true }, async (_p, token) => {
            const controller = new AbortController();
            token.onCancellationRequested(() => controller.abort());
            await s.api().http.stream(`/instances/stream/${encodeURIComponent(app.id)}`, {
                signal: controller.signal,
                onLine: (raw) => {
                    const line = (0, applications_1.formatStreamLine)(raw);
                    if (line && line.kind !== 'metric')
                        out.appendLine(line.text);
                },
            });
            out.appendLine(controller.signal.aborted ? '# transmissão interrompida' : '# conexão encerrada pelo servidor');
        });
    }
    catch (err) {
        out.appendLine('# falha na transmissão');
        if (err instanceof errors_1.VenixApiError)
            s.logger.warn(`logs: ${err.kind}`);
        (0, shared_1.reportError)(s, 'Logs', err);
    }
}
async function appCreate(s) {
    const folder = await (0, shared_1.pickFolder)();
    const detected = folder ? await (0, projectDetector_1.detectProject)(folder.uri.fsPath) : undefined;
    const name = await vscode.window.showInputBox({
        title: 'Nova aplicação (1/6): nome',
        prompt: '3 a 50 caracteres: letras, números, "_" ou "-".',
        value: detected?.suggestedName,
        ignoreFocusOut: true,
        validateInput: validators_1.checkAppName,
    });
    if (!name)
        return;
    const runtimeItems = validators_1.RUNTIMES.map((r) => ({ label: r, description: r === detected?.runtime ? `detectado (${detected.evidence.join(', ')})` : undefined }));
    runtimeItems.sort((a, b) => (a.description ? -1 : 0) - (b.description ? -1 : 0));
    const runtime = await vscode.window.showQuickPick(runtimeItems, { title: 'Nova aplicação (2/6): runtime', ignoreFocusOut: true });
    if (!runtime)
        return;
    const ram = await (0, shared_1.askNumber)('Nova aplicação (3/6): RAM em MB (128 a 32768)', '512', validators_1.checkAppRam);
    if (ram === undefined)
        return;
    const kind = await vscode.window.showQuickPick([
        { label: 'Aplicação web', description: 'HTTPS em *.venixcloud.com', web: true },
        { label: 'Bot / worker', description: 'sem endereço web', web: false },
    ], { title: 'Nova aplicação (4/6): tipo', ignoreFocusOut: true });
    if (!kind)
        return;
    let subdomain;
    if (kind.web) {
        subdomain = await vscode.window.showInputBox({
            title: 'Nova aplicação (5/6): subdomínio',
            prompt: 'Subdomínio único em *.venixcloud.com',
            value: name.toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 63),
            ignoreFocusOut: true,
            validateInput: validators_1.checkSubdomain,
        });
        if (!subdomain)
            return;
    }
    const startCommand = await vscode.window.showInputBox({
        title: 'Nova aplicação (6/6): comando de início (opcional)',
        prompt: 'Deixe em branco para usar o padrão do runtime.',
        value: detected?.startCommandHint,
        ignoreFocusOut: true,
        validateInput: (v) => (v ? (0, validators_1.checkStartCommand)(v) : undefined),
    });
    if (startCommand === undefined)
        return;
    let file;
    if (folder) {
        const include = await vscode.window.showQuickPick([
            { label: `Enviar o projeto "${folder.name}"`, send: true },
            { label: 'Criar aplicação vazia', send: false },
        ], { title: 'Enviar código junto com a criação?', ignoreFocusOut: true });
        if (!include)
            return;
        if (include.send) {
            const prepared = await (0, archive_1.prepareArchive)(s, folder, `nova aplicação "${name}"`);
            if (!prepared)
                return;
            file = { filename: 'projeto.zip', data: prepared.zip };
        }
    }
    const res = await (0, shared_1.run)(s, `Venix Cloud: criando ${name}…`, (signal) => s.api().apps.create({ appName: name, runtime: runtime.label, ram, isWeb: kind.web, subdomain: subdomain?.toLowerCase(), startCommand: startCommand || undefined, file }, signal), { success: `aplicação ${name} criada.`, refresh: ['apps'], cancellable: true });
    if (!res.ok)
        return;
    const id = (res.value['id'] ?? res.value['appId'] ?? res.value['_id']);
    if (folder && typeof id === 'string') {
        const link = await vscode.window.showInformationMessage(`Vincular esta pasta à aplicação ${name}? (cria .venix.json)`, 'Vincular');
        if (link === 'Vincular') {
            await (0, link_1.writeLink)(folder.uri.fsPath, { id, name });
            s.setLinkedApp(id, name);
        }
    }
}
//# sourceMappingURL=apps.js.map