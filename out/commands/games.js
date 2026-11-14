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
exports.gameCreate = gameCreate;
exports.gameCommand = gameCommand;
exports.gameDelete = gameDelete;
const vscode = __importStar(require("vscode"));
const games_1 = require("../api/modules/games");
const validators_1 = require("../api/validators");
const shared_1 = require("./shared");
async function pickGame(s, arg) {
    const node = (0, shared_1.nodeOf)(arg, 'game');
    if (node)
        return node.item;
    if (!(await (0, shared_1.ensureSignedIn)(s)))
        return undefined;
    if (s.store.snapshot.games.state === 'idle')
        await s.store.refresh('games');
    const games = s.store.snapshot.games.items;
    if (games.length === 0) {
        void vscode.window.showInformationMessage('Venix Cloud: nenhum servidor de jogo encontrado.');
        return undefined;
    }
    const picked = await vscode.window.showQuickPick(games.map((g) => ({ label: g.name, description: g.game, detail: g.id, game: g })), { placeHolder: 'Selecione o servidor' });
    return picked?.game;
}
async function gameCreate(s) {
    if (!(await (0, shared_1.ensureSignedIn)(s)))
        return;
    let eggs = [];
    try {
        eggs = await s.api().games.eggs();
    }
    catch (err) {
        s.logger.warn(`Catálogo de eggs indisponível: ${err instanceof Error ? err.message : 'erro'}`);
    }
    const fromApi = eggs.length > 0;
    if (!fromApi)
        eggs = (0, games_1.documentedEggs)();
    const egg = await vscode.window.showQuickPick(eggs.map((e) => ({ label: e.label, description: e.id !== e.label ? e.id : undefined, id: e.id })), { title: 'Novo servidor (1/4): jogo', placeHolder: fromApi ? 'Catálogo da Venix Cloud' : 'Catálogo indisponível — exibindo os jogos citados na documentação', ignoreFocusOut: true });
    if (!egg)
        return;
    const name = await vscode.window.showInputBox({ title: 'Novo servidor (2/4): nome', prompt: '1 a 60 caracteres.', ignoreFocusOut: true, validateInput: validators_1.checkGameName });
    if (!name)
        return;
    const ram = await (0, shared_1.askNumber)('Novo servidor (3/4): RAM em MB (256 a 65536)', '2048', validators_1.checkGameRam);
    if (ram === undefined)
        return;
    const region = await vscode.window.showQuickPick([...validators_1.REGIONS], { title: 'Novo servidor (4/4): região', ignoreFocusOut: true });
    if (!region)
        return;
    await (0, shared_1.run)(s, `Venix Cloud: criando servidor ${name}…`, () => s.api().games.create({ name, game: egg.id, ram, region: region }), {
        success: `servidor ${name} criado.`,
        refresh: ['games'],
    });
}
async function gameCommand(s, arg) {
    const game = await pickGame(s, arg);
    if (!game)
        return;
    const cmd = await vscode.window.showInputBox({
        title: `Console de ${game.name}`,
        prompt: 'Comando enviado ao console (stdin) do servidor.',
        ignoreFocusOut: true,
        validateInput: (v) => (v ? (0, validators_1.checkConsoleCommand)(v) : 'Informe o comando.'),
    });
    if (!cmd)
        return;
    try {
        await s.api().games.sendCommand(game.id, cmd);
        void vscode.window.showInformationMessage(`Venix Cloud: comando enviado ao servidor ${game.name}.`);
    }
    catch (err) {
        (0, shared_1.reportError)(s, 'Comando de console', err);
    }
}
async function gameDelete(s, arg) {
    const game = await pickGame(s, arg);
    if (!game)
        return;
    const ok = await (0, shared_1.confirmDestructive)(s, {
        action: 'Excluir servidor',
        name: game.name,
        typeName: true,
        detail: 'Exclui o servidor de jogo e libera a RAM e as portas UDP/TCP. Esta ação não pode ser desfeita.',
    });
    if (!ok)
        return;
    await (0, shared_1.run)(s, `Venix Cloud: excluindo ${game.name}…`, () => s.api().games.remove(game.id), { success: `servidor ${game.name} excluído.`, refresh: ['games'] });
}
//# sourceMappingURL=games.js.map