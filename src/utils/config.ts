import * as vscode from 'vscode';
import { CONFIG_SECTION, DEFAULT_API_BASE_URL, OAUTH_DEFAULT_CALLBACK_PORT } from '../constants';
import { normalizeBaseUrl } from '../security/trustedHosts';

export interface VenixSettings {
  /** URL-base JÁ validada contra a política de domínios confiáveis. */
  apiBaseUrl: string;
  apiBaseUrlError?: string;
  autoRefresh: boolean;
  refreshIntervalSec: number;
  confirmDestructive: boolean;
  redirectMode: 'loopback' | 'vscode';
  callbackPort: number;
  publishExclude: string[];
  restartAfterUpload: boolean;
}

const clamp = (n: number, min: number, max: number): number => Math.min(max, Math.max(min, Math.round(n)));

export function readSettings(): VenixSettings {
  const cfg = vscode.workspace.getConfiguration(CONFIG_SECTION);
  const requested = cfg.get<string>('apiBaseUrl', DEFAULT_API_BASE_URL);
  let apiBaseUrl = DEFAULT_API_BASE_URL;
  let apiBaseUrlError: string | undefined;
  try {
    apiBaseUrl = normalizeBaseUrl(requested);
  } catch (err) {
    // Valor inválido ou não confiável: volta ao padrão; o token NUNCA vai para o destino rejeitado.
    apiBaseUrlError = err instanceof Error ? err.message : 'URL-base inválida.';
  }
  const mode = cfg.get<string>('oauth.redirectMode', 'loopback');
  const exclude = cfg.get<unknown>('publish.exclude', []);
  return {
    apiBaseUrl,
    apiBaseUrlError,
    autoRefresh: cfg.get<boolean>('autoRefresh', false),
    refreshIntervalSec: clamp(cfg.get<number>('refreshInterval', 60), 30, 3600),
    confirmDestructive: cfg.get<boolean>('confirmDestructiveActions', true),
    redirectMode: mode === 'vscode' ? 'vscode' : 'loopback',
    callbackPort: clamp(cfg.get<number>('oauth.callbackPort', OAUTH_DEFAULT_CALLBACK_PORT), 1024, 65535),
    publishExclude: Array.isArray(exclude) ? exclude.filter((x): x is string => typeof x === 'string') : [],
    restartAfterUpload: cfg.get<boolean>('publish.restartAfterUpload', true),
  };
}
