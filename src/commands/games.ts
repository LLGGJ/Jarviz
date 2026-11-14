import * as vscode from 'vscode';
import { documentedEggs, type EggInfo } from '../api/modules/games';
import { checkConsoleCommand, checkGameName, checkGameRam, REGIONS, type Region } from '../api/validators';
import type { Services } from '../services';
import { askNumber, confirmDestructive, ensureSignedIn, nodeOf, reportError, run } from './shared';

async function pickGame(s: Services, arg?: unknown) {
  const node = nodeOf(arg, 'game');
  if (node) return node.item;
  if (!(await ensureSignedIn(s))) return undefined;
  if (s.store.snapshot.games.state === 'idle') await s.store.refresh('games');
  const games = s.store.snapshot.games.items;
  if (games.length === 0) {
    void vscode.window.showInformationMessage('Venix Cloud: nenhum servidor de jogo encontrado.');
    return undefined;
  }
  const picked = await vscode.window.showQuickPick(games.map((g) => ({ label: g.name, description: g.game, detail: g.id, game: g })), { placeHolder: 'Selecione o servidor' });
  return picked?.game;
}

export async function gameCreate(s: Services): Promise<void> {
  if (!(await ensureSignedIn(s))) return;
  let eggs: EggInfo[] = [];
  try {
    eggs = await s.api().games.eggs();
  } catch (err) {
    s.logger.warn(`Catálogo de eggs indisponível: ${err instanceof Error ? err.message : 'erro'}`);
  }
  const fromApi = eggs.length > 0;
  if (!fromApi) eggs = documentedEggs();
  const egg = await vscode.window.showQuickPick(
    eggs.map((e) => ({ label: e.label, description: e.id !== e.label ? e.id : undefined, id: e.id })),
    { title: 'Novo servidor (1/4): jogo', placeHolder: fromApi ? 'Catálogo da Venix Cloud' : 'Catálogo indisponível — exibindo os jogos citados na documentação', ignoreFocusOut: true },
  );
  if (!egg) return;
  const name = await vscode.window.showInputBox({ title: 'Novo servidor (2/4): nome', prompt: '1 a 60 caracteres.', ignoreFocusOut: true, validateInput: checkGameName });
  if (!name) return;
  const ram = await askNumber('Novo servidor (3/4): RAM em MB (256 a 65536)', '2048', checkGameRam);
  if (ram === undefined) return;
  const region = await vscode.window.showQuickPick([...REGIONS], { title: 'Novo servidor (4/4): região', ignoreFocusOut: true });
  if (!region) return;
  await run(s, `Venix Cloud: criando servidor ${name}…`, () => s.api().games.create({ name, game: egg.id, ram, region: region as Region }), {
    success: `servidor ${name} criado.`,
    refresh: ['games'],
  });
}

export async function gameCommand(s: Services, arg?: unknown): Promise<void> {
  const game = await pickGame(s, arg);
  if (!game) return;
  const cmd = await vscode.window.showInputBox({
    title: `Console de ${game.name}`,
    prompt: 'Comando enviado ao console (stdin) do servidor.',
    ignoreFocusOut: true,
    validateInput: (v) => (v ? checkConsoleCommand(v) : 'Informe o comando.'),
  });
  if (!cmd) return;
  try {
    await s.api().games.sendCommand(game.id, cmd);
    void vscode.window.showInformationMessage(`Venix Cloud: comando enviado ao servidor ${game.name}.`);
  } catch (err) {
    reportError(s, 'Comando de console', err);
  }
}

export async function gameDelete(s: Services, arg?: unknown): Promise<void> {
  const game = await pickGame(s, arg);
  if (!game) return;
  const ok = await confirmDestructive(s, {
    action: 'Excluir servidor',
    name: game.name,
    typeName: true,
    detail: 'Exclui o servidor de jogo e libera a RAM e as portas UDP/TCP. Esta ação não pode ser desfeita.',
  });
  if (!ok) return;
  await run(s, `Venix Cloud: excluindo ${game.name}…`, () => s.api().games.remove(game.id), { success: `servidor ${game.name} excluído.`, refresh: ['games'] });
}
