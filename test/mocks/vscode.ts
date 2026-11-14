/* eslint-disable @typescript-eslint/no-explicit-any */
import * as fs from 'node:fs';
import * as path from 'node:path';

type Fn = (...args: any[]) => any;

export const pkg = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'package.json'), 'utf8'));

// ------------------------------------------------------------ infraestrutura de script
export const script = {
  queues: new Map<string, any[]>(),
  calls: [] as Array<{ fn: string; args: any[] }>,
  config: new Map<string, any>(),
};

/** Enfileira respostas para uma função da UI (ex.: showWarningMessage). */
export function answer(fn: string, ...values: any[]): void {
  const q = script.queues.get(fn) ?? [];
  q.push(...values);
  script.queues.set(fn, q);
}

export function callsOf(fn: string): any[][] {
  return script.calls.filter((c) => c.fn === fn).map((c) => c.args);
}

export function reset(): void {
  script.queues.clear();
  script.calls.length = 0;
  script.config.clear();
}

function scripted(name: string): Fn {
  return async (...args: any[]) => {
    script.calls.push({ fn: name, args });
    const q = script.queues.get(name);
    if (!q || q.length === 0) return undefined;
    const next = q.shift();
    return typeof next === 'function' ? next(...args) : next;
  };
}

// ------------------------------------------------------------ tipos básicos
export class Disposable {
  constructor(private cb: () => void = () => undefined) {}
  dispose(): void {
    this.cb();
  }
  static from(...items: Array<{ dispose(): any }>): Disposable {
    return new Disposable(() => items.forEach((i) => i.dispose()));
  }
}

export class EventEmitter<T> {
  private ls = new Set<(e: T) => any>();
  event = (l: (e: T) => any): Disposable => {
    this.ls.add(l);
    return new Disposable(() => this.ls.delete(l));
  };
  fire(e: T): void {
    for (const l of [...this.ls]) l(e);
  }
  dispose(): void {
    this.ls.clear();
  }
}

export class Uri {
  constructor(
    readonly scheme: string,
    readonly authority: string,
    readonly path: string,
    readonly query: string,
    readonly fragment: string,
  ) {}
  static parse(value: string): Uri {
    const u = new URL(value);
    return new Uri(u.protocol.replace(':', ''), u.host, u.pathname, u.search.replace(/^\?/, ''), u.hash.replace(/^#/, ''));
  }
  static file(p: string): Uri {
    return new Uri('file', '', p, '', '');
  }
  static joinPath(base: Uri, ...parts: string[]): Uri {
    return new Uri(base.scheme, base.authority, path.posix.join(base.path, ...parts), '', '');
  }
  get fsPath(): string {
    return this.path;
  }
  toString(): string {
    return this.scheme === 'file' ? `file://${this.path}` : `${this.scheme}://${this.authority}${this.path}${this.query ? `?${this.query}` : ''}`;
  }
}

export class ThemeColor {
  constructor(readonly id: string) {}
}
export class ThemeIcon {
  constructor(
    readonly id: string,
    readonly color?: ThemeColor,
  ) {}
}
export class MarkdownString {
  value = '';
  constructor(value = '') {
    this.value = value;
  }
  appendMarkdown(v: string): MarkdownString {
    this.value += v;
    return this;
  }
}
export enum TreeItemCollapsibleState {
  None = 0,
  Collapsed = 1,
  Expanded = 2,
}
export class TreeItem {
  id?: string;
  iconPath?: any;
  description?: string;
  tooltip?: any;
  command?: any;
  contextValue?: string;
  constructor(
    public label: string,
    public collapsibleState: TreeItemCollapsibleState = TreeItemCollapsibleState.None,
  ) {}
}
export enum ProgressLocation {
  SourceControl = 1,
  Window = 10,
  Notification = 15,
}
export enum ViewColumn {
  Active = -1,
}
export enum StatusBarAlignment {
  Left = 1,
  Right = 2,
}

// ------------------------------------------------------------ comandos
const registry = new Map<string, Fn>();
export const commands = {
  registerCommand(id: string, cb: Fn): Disposable {
    registry.set(id, cb);
    return new Disposable(() => registry.delete(id));
  },
  async executeCommand(id: string, ...args: any[]): Promise<any> {
    script.calls.push({ fn: 'executeCommand', args: [id, ...args] });
    const handler = registry.get(id);
    return handler ? handler(...args) : undefined;
  },
  registered(): string[] {
    return [...registry.keys()];
  },
};

// ------------------------------------------------------------ janela
function fakeWebview() {
  const listeners: Array<(m: any) => any> = [];
  return {
    options: {},
    html: '',
    cspSource: 'vscode-webview://test',
    posted: [] as any[],
    onDidReceiveMessage: (l: (m: any) => any) => {
      listeners.push(l);
      return new Disposable();
    },
    postMessage: async function (this: any, m: any) {
      this.posted.push(m);
      return true;
    },
    asWebviewUri: (u: Uri) => u,
    emit: async (m: any) => {
      for (const l of listeners) await l(m);
    },
  };
}

export const window = {
  showInformationMessage: scripted('showInformationMessage'),
  showWarningMessage: scripted('showWarningMessage'),
  showErrorMessage: scripted('showErrorMessage'),
  showQuickPick: scripted('showQuickPick'),
  showInputBox: scripted('showInputBox'),
  showSaveDialog: scripted('showSaveDialog'),
  showOpenDialog: scripted('showOpenDialog'),
  showWorkspaceFolderPick: scripted('showWorkspaceFolderPick'),
  showTextDocument: scripted('showTextDocument'),
  withProgress: async (_o: any, task: Fn) => {
    script.calls.push({ fn: 'withProgress', args: [_o] });
    return task({ report: () => undefined }, { isCancellationRequested: false, onCancellationRequested: () => new Disposable() });
  },
  createOutputChannel: (name: string) => ({ name, lines: [] as string[], append() {}, appendLine(l: string) { (this as any).lines.push(l); }, clear() {}, show() {}, dispose() {} }),
  createStatusBarItem: () => ({ text: '', tooltip: undefined as any, command: undefined as any, show() {}, hide() {}, dispose() {} }),
  createTreeView: (id: string, opts: any) => ({ id, opts, dispose() {} }),
  createInputBox: () => ({ dispose() {}, show() {}, hide() {}, onDidAccept: () => new Disposable(), onDidHide: () => new Disposable(), onDidTriggerButton: () => new Disposable() }),
  createWebviewPanel: (viewType: string, title: string) => {
    const webview = fakeWebview();
    const panel = { viewType, title, webview, disposed: false, onDidDispose: () => new Disposable(), reveal() {}, dispose() { panel.disposed = true; } };
    panels.push(panel);
    return panel;
  },
  registerWebviewViewProvider: (id: string, provider: any) => {
    providers.set(id, provider);
    return new Disposable();
  },
  registerUriHandler: () => new Disposable(),
  activeTextEditor: undefined as any,
  state: { focused: true },
};
export const panels: any[] = [];
export const providers = new Map<string, any>();

// ------------------------------------------------------------ workspace / env / auth
function defaultFor(key: string): any {
  const prop = pkg.contributes.configuration.properties[key];
  return prop ? prop.default : undefined;
}
export const workspace = {
  workspaceFolders: undefined as any,
  isTrusted: true,
  fs: { writeFile: async () => undefined },
  getConfiguration: (section?: string) => ({
    get: (key: string, def?: any) => {
      const full = section ? `${section}.${key}` : key;
      if (script.config.has(full)) return script.config.get(full);
      const d = defaultFor(full);
      return d !== undefined ? d : def;
    },
  }),
  openTextDocument: async (o: any) => ({ uri: Uri.file('/untitled'), ...o }),
  createFileSystemWatcher: () => ({ onDidChange: () => new Disposable(), onDidCreate: () => new Disposable(), onDidDelete: () => new Disposable(), dispose() {} }),
  onDidChangeConfiguration: () => new Disposable(),
  onDidChangeWorkspaceFolders: () => new Disposable(),
};

export const clipboardState = { text: '' };
export const env = {
  uriScheme: 'vscode',
  clipboard: {
    readText: async () => clipboardState.text,
    writeText: async (v: string) => {
      clipboardState.text = v;
    },
  },
  openExternal: async (u: Uri) => {
    script.calls.push({ fn: 'openExternal', args: [u] });
    return true;
  },
};

export const authProviders = new Map<string, any>();
export const authentication = {
  registerAuthenticationProvider: (id: string, label: string, provider: any) => {
    authProviders.set(id, { label, provider });
    return new Disposable();
  },
};

// ------------------------------------------------------------ contexto de extensão falso
export function makeContext(root = path.join(__dirname, '..', '..')) {
  const secretMap = new Map<string, string>();
  const memento = () => {
    const m = new Map<string, any>();
    return { map: m, get: (k: string, d?: any) => (m.has(k) ? m.get(k) : d), update: async (k: string, v: any) => void (v === undefined ? m.delete(k) : m.set(k, v)) };
  };
  return {
    subscriptions: [] as Array<{ dispose(): any }>,
    secrets: {
      map: secretMap,
      get: async (k: string) => secretMap.get(k),
      store: async (k: string, v: string) => void secretMap.set(k, v),
      delete: async (k: string) => void secretMap.delete(k),
    },
    globalState: memento(),
    workspaceState: memento(),
    extensionUri: Uri.file(root),
    extension: { id: 'venixcloud.venix-cloud', extensionUri: Uri.file(root) },
  };
}
