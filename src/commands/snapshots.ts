import * as vscode from 'vscode';
import { isSafeExternalUrl } from '../security/trustedHosts';
import type { Services } from '../services';
import { timestampName } from '../utils/format';
import { confirmDestructive, ensureLoaded, ensureSignedIn, nodeOf, run } from './shared';

export async function snapshotCreate(s: Services, arg?: unknown): Promise<void> {
  if (!(await ensureSignedIn(s))) return;
  let resource: { id: string; name: string } | undefined;
  const app = nodeOf(arg, 'app');
  const db = nodeOf(arg, 'database');
  if (app) resource = { id: app.item.id, name: app.item.name };
  else if (db) resource = { id: db.item.id, name: db.item.name };
  else {
    await ensureLoaded(s, 'apps');
    await ensureLoaded(s, 'databases');
    const picked = await vscode.window.showQuickPick(
      [
        ...s.store.snapshot.apps.items.map((a) => ({ label: a.name, description: 'aplicação', detail: a.id, res: { id: a.id, name: a.name } })),
        ...s.store.snapshot.databases.items.map((d) => ({ label: d.name, description: 'banco de dados', detail: d.id, res: { id: d.id, name: d.name } })),
      ],
      { placeHolder: 'Recurso para o snapshot (aplicações e bancos de dados)', matchOnDetail: true },
    );
    resource = picked?.res;
  }
  if (!resource) return;
  const target = resource;
  const name = await vscode.window.showInputBox({
    title: `Snapshot de ${target.name}`,
    prompt: 'Nome do snapshot',
    value: timestampName('backup'),
    ignoreFocusOut: true,
    validateInput: (v) => (v.trim() && v.length <= 100 ? undefined : 'Informe um nome de até 100 caracteres.'),
  });
  if (!name) return;
  await run(s, `Venix Cloud: criando snapshot de ${target.name}…`, () => s.api().snapshots.create(target.id, name), {
    success: `snapshot "${name}" solicitado.`,
    refresh: ['snapshots'],
  });
}

export async function snapshotDownload(s: Services, arg?: unknown): Promise<void> {
  const node = nodeOf(arg, 'snapshot');
  if (!node) return;
  const res = await run(s, 'Venix Cloud: gerando link de download…', () => s.api().snapshots.downloadUrl(node.item.id));
  if (!res.ok) return;
  if (!isSafeExternalUrl(res.value)) {
    void vscode.window.showErrorMessage('Venix Cloud: a API devolveu um link de download inválido.');
    return;
  }
  await vscode.env.openExternal(vscode.Uri.parse(res.value));
}

export async function snapshotDelete(s: Services, arg?: unknown): Promise<void> {
  const node = nodeOf(arg, 'snapshot');
  if (!node) return;
  const ok = await confirmDestructive(s, {
    action: 'Excluir snapshot',
    name: node.item.name,
    typeName: true,
    detail: 'Exclui o snapshot e libera o espaço de armazenamento. Esta ação não pode ser desfeita.',
  });
  if (!ok) return;
  await run(s, `Venix Cloud: excluindo ${node.item.name}…`, () => s.api().snapshots.remove(node.item.id), {
    success: `snapshot ${node.item.name} excluído.`,
    refresh: ['snapshots'],
  });
}
