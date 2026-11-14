/**
 * Constantes da extensão.
 *
 * Origem dos valores OAuth: código-fonte da CLI oficial `venix` (internal/auth/auth.go).
 * Esses endpoints NÃO constam em api-docs.html (v1.4); veja a seção "Limitações" do README.
 * O client_id é um identificador público (cliente público + PKCE, sem client_secret).
 */
export const EXTENSION_NAME = 'Venix Cloud';
export const CONFIG_SECTION = 'venixCloud';
export const AUTH_PROVIDER_ID = 'venixcloud';

export const DEFAULT_API_BASE_URL = 'https://api.venixcloud.com/v1';
/** Raiz do site; o caminho exato do painel web não é documentado. */
export const WEB_PANEL_URL = 'https://venixcloud.com';

export const OAUTH_CLIENT_ID = 'venix_54620c6a2b626ec6d2';
export const OAUTH_AUTHORIZE_URL = 'https://venixcloud.com/en/auth/bridge';
export const OAUTH_TOKEN_URL = 'https://api.venixcloud.com/v1/oauth2/token';
export const OAUTH_DEFAULT_CALLBACK_PORT = 53682;
export const OAUTH_CALLBACK_PATH = '/callback';
export const OAUTH_TIMEOUT_MS = 5 * 60 * 1000;

export const SECRET_SESSION_KEY = 'venixCloud.session';
export const STATE_ACCOUNT_KEY = 'venixCloud.account';
export const STATE_LAST_PUSH_KEY = 'venixCloud.lastPush';

/** Arquivos compatíveis com a CLI `venix`. */
export const LINK_FILE = '.venix.json';
export const IGNORE_FILE = '.venixignore';
export const REMOTE_UPDATE_ZIP = '/__venix_update.zip';

/** Limites documentados em api-docs.html. */
export const LIMITS = {
  cdnMinBytes: 1024,
  cdnMaxBytes: 100 * 1024 * 1024,
  appArchiveMaxBytes: 500 * 1024 * 1024,
  requestsPerMinute: 60,
} as const;

export const MAX_JSON_BYTES = 10 * 1024 * 1024;
export const MAX_BINARY_BYTES = 100 * 1024 * 1024;
