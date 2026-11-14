import type * as vscode from 'vscode';
import type { VenixApi } from './api';
import type { AuthenticationManager } from './auth/AuthenticationManager';
import type { UriCallbackRouter } from './auth/vscodeReceivers';
import type { ResourceStore } from './providers/ResourceStore';
import type { VenixSettings } from './utils/config';
import type { Logger } from './utils/logger';

/** Dependências compartilhadas entre os comandos (injetadas na ativação). */
export interface Services {
  context: vscode.ExtensionContext;
  logger: Logger;
  auth: AuthenticationManager;
  store: ResourceStore;
  uriRouter: UriCallbackRouter;
  settings(): VenixSettings;
  /** Cliente autenticado; lança VenixApiError('not-authenticated') se não houver sessão. */
  api(): VenixApi;
  /** Atualiza o contexto do projeto vinculado (status bar/árvore). */
  setLinkedApp(id: string | undefined, name: string | undefined): void;
  linkedApp(): { id: string; name?: string } | undefined;
}
