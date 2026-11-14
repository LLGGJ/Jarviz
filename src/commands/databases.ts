import * as fs from 'node:fs/promises';
import * as vscode from 'vscode';
import { checkDbName, checkDbRam, DB_ENGINES, REGIONS, type DbEngine, type Region } from '../api/validators';
import type { Services } from '../services';
import { askNumber, confirmDestructive, ensureSignedIn, nodeOf, run } from './shared';

async function pickDatabase(s: Services, arg?: unknown) {
  const node = nodeOf(arg, 'database');
  if (node) return node.item;
  if (!(await ensureSignedIn(s))) return undefined;
  if (s.store.snapshot.databases.state === 'idle') await s.store.refresh('databases');
  const dbs = s.store.snapshot.databases.items;
  if (dbs.length === 0) {
    void vscode.window.showInformationMessage('Venix Cloud: nenhum banco de dados encontrado.');
    return undefined;
  }
  const picked = await vscode.window.showQuickPick(dbs.map((d) => ({ label: d.name, description: d.engine, detail: d.id, db: d })), { placeHolder: 'Selecione o banco de dados' });
  return picked?.db;
}

export async function dbCreate(s: Services): Promise<void> {
  if (!(await ensureSignedIn(s))) return;
  const name = await vscode.window.showInputBox({
    title: 'Novo banco (1/5): nome',
    prompt: 'Mínimo 3 caracteres: letras minúsculas, números e hifens.',
    ignoreFocusOut: true,
    validateInput: checkDbName,
  });
  if (!name) return;
  const engine = await vscode.window.showQuickPick([...DB_ENGINES], { title: 'Novo banco (2/5): motor', ignoreFocusOut: true });
  if (!engine) return;
  const ram = await askNumber('Novo banco (3/5): RAM em MB (mínimo 512)', '1024', checkDbRam);
  if (ram === undefined) return;
  const region = await vscode.window.showQuickPick([...REGIONS], { title: 'Novo banco (4/5): região', ignoreFocusOut: true });
  if (!region) return;
  const manager = await vscode.window.showQuickPick(
    [
      { label: 'Sim', description: 'phpMyAdmin / pgAdmin / Mongo Express', value: true },
      { label: 'Não', value: false },
    ],
    { title: 'Novo banco (5/5): instalar painel web de gerenciamento?', ignoreFocusOut: true },
  );
  if (!manager) return;
  await run(
    s,
    `Venix Cloud: criando banco ${name}…`,
    () => s.api().databases.create({ name, engine: engine as DbEngine, ram, region: region as Region, installManager: manager.value }),
    { success: `banco ${name} criado.`, refresh: ['databases'] },
  );
}

export async function dbCertificates(s: Services, arg?: unknown): Promise<void> {
  const db = await pickDatabase(s, arg);
  if (!db) return;
  const target = await vscode.window.showSaveDialog({
    defaultUri: vscode.Uri.file(`${db.name}-certs.zip`),
    filters: { ZIP: ['zip'] },
    title: 'Salvar certificados mTLS',
  });
  if (!target) return;
  const res = await run(s, `Venix Cloud: baixando certificados de ${db.name}…`, (signal) => s.api().databases.downloadCertificates(db.id, signal), { cancellable: true });
  if (!res.ok) return;
  if (target.scheme === 'file') await fs.writeFile(target.fsPath, res.value, { mode: 0o600 });
  else await vscode.workspace.fs.writeFile(target, res.value);
  void vscode.window.showWarningMessage(`Venix Cloud: certificados salvos. O ZIP contém a CHAVE PRIVADA do cliente — não o versione nem o compartilhe.`);
}

export async function dbDelete(s: Services, arg?: unknown): Promise<void> {
  const db = await pickDatabase(s, arg);
  if (!db) return;
  const ok = await confirmDestructive(s, {
    action: 'Excluir banco',
    name: db.name,
    typeName: true,
    detail: 'Exclui PERMANENTEMENTE o banco de dados, o volume e o IP na malha. Todos os dados serão perdidos.',
  });
  if (!ok) return;
  await run(s, `Venix Cloud: excluindo ${db.name}…`, () => s.api().databases.remove(db.id), { success: `banco ${db.name} excluído.`, refresh: ['databases'] });
}
