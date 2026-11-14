"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MAX_BINARY_BYTES = exports.MAX_JSON_BYTES = exports.LIMITS = exports.REMOTE_UPDATE_ZIP = exports.IGNORE_FILE = exports.LINK_FILE = exports.STATE_LAST_PUSH_KEY = exports.STATE_ACCOUNT_KEY = exports.SECRET_SESSION_KEY = exports.OAUTH_TIMEOUT_MS = exports.OAUTH_CALLBACK_PATH = exports.OAUTH_DEFAULT_CALLBACK_PORT = exports.OAUTH_TOKEN_URL = exports.OAUTH_AUTHORIZE_URL = exports.OAUTH_CLIENT_ID = exports.WEB_PANEL_URL = exports.DEFAULT_API_BASE_URL = exports.AUTH_PROVIDER_ID = exports.CONFIG_SECTION = exports.EXTENSION_NAME = void 0;
/**
 * Constantes da extensão.
 *
 * Origem dos valores OAuth: código-fonte da CLI oficial `venix` (internal/auth/auth.go).
 * Esses endpoints NÃO constam em api-docs.html (v1.4); veja a seção "Limitações" do README.
 * O client_id é um identificador público (cliente público + PKCE, sem client_secret).
 */
exports.EXTENSION_NAME = 'Venix Cloud';
exports.CONFIG_SECTION = 'venixCloud';
exports.AUTH_PROVIDER_ID = 'venixcloud';
exports.DEFAULT_API_BASE_URL = 'https://api.venixcloud.com/v1';
/** Raiz do site; o caminho exato do painel web não é documentado. */
exports.WEB_PANEL_URL = 'https://venixcloud.com';
exports.OAUTH_CLIENT_ID = 'venix_54620c6a2b626ec6d2';
exports.OAUTH_AUTHORIZE_URL = 'https://venixcloud.com/en/auth/bridge';
exports.OAUTH_TOKEN_URL = 'https://api.venixcloud.com/v1/oauth2/token';
exports.OAUTH_DEFAULT_CALLBACK_PORT = 53682;
exports.OAUTH_CALLBACK_PATH = '/callback';
exports.OAUTH_TIMEOUT_MS = 5 * 60 * 1000;
exports.SECRET_SESSION_KEY = 'venixCloud.session';
exports.STATE_ACCOUNT_KEY = 'venixCloud.account';
exports.STATE_LAST_PUSH_KEY = 'venixCloud.lastPush';
/** Arquivos compatíveis com a CLI `venix`. */
exports.LINK_FILE = '.venix.json';
exports.IGNORE_FILE = '.venixignore';
exports.REMOTE_UPDATE_ZIP = '/__venix_update.zip';
/** Limites documentados em api-docs.html. */
exports.LIMITS = {
    cdnMinBytes: 1024,
    cdnMaxBytes: 100 * 1024 * 1024,
    appArchiveMaxBytes: 500 * 1024 * 1024,
    requestsPerMinute: 60,
};
exports.MAX_JSON_BYTES = 10 * 1024 * 1024;
exports.MAX_BINARY_BYTES = 100 * 1024 * 1024;
//# sourceMappingURL=constants.js.map