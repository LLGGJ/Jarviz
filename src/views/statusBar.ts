import * as vscode from 'vscode';
import type { AuthenticationManager } from '../auth/AuthenticationManager';
import type { ResourceStore } from '../providers/ResourceStore';
import { pluralize } from '../utils/format';

export class VenixStatusBar implements vscode.Disposable {
  private readonly item = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 50);
  private readonly subs: Array<{ dispose(): void }> = [];

  constructor(
    private readonly auth: AuthenticationManager,
    private readonly store: ResourceStore,
    private linkedAppName: () => string | undefined,
  ) {
    this.subs.push(auth.onDidChange(() => this.update()), store.onDidChange(() => this.update()));
    this.update();
    this.item.show();
  }

  update(): void {
    const session = this.auth.getSessionInfo();
    if (!session) {
      this.item.text = '$(cloud) Venix: entrar';
      this.item.tooltip = 'Entrar com a Venix Cloud';
      this.item.command = 'venixCloud.signIn';
      return;
    }
    const linked = this.linkedAppName();
    const online = this.store.snapshot.apps.items.filter((a) => a.status === 'online').length;
    const total = this.store.snapshot.apps.items.length;
    this.item.text = `$(cloud) Venix${linked ? ` • ${linked}` : ''}`;
    this.item.tooltip = new vscode.MarkdownString(
      `**Venix Cloud** — ${session.account.label}\n\n${pluralize(online, 'aplicação online', 'aplicações online')} de ${total}` +
        (linked ? `\n\nProjeto vinculado: \`${linked}\`` : ''),
    );
    this.item.command = 'venixCloud.showAccount';
  }

  dispose(): void {
    for (const s of this.subs) s.dispose();
    this.item.dispose();
  }
}
