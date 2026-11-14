"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.UntrustedUrlError = exports.TRUSTED_ROOT_DOMAIN = void 0;
exports.isTrustedHost = isTrustedHost;
exports.assertTrustedUrl = assertTrustedUrl;
exports.normalizeBaseUrl = normalizeBaseUrl;
exports.isSafeExternalUrl = isSafeExternalUrl;
exports.TRUSTED_ROOT_DOMAIN = 'venixcloud.com';
class UntrustedUrlError extends Error {
    constructor(message) {
        super(message);
        this.name = 'UntrustedUrlError';
    }
}
exports.UntrustedUrlError = UntrustedUrlError;
function isTrustedHost(host) {
    const h = host.toLowerCase().replace(/\.$/, '');
    return h === exports.TRUSTED_ROOT_DOMAIN || h.endsWith(`.${exports.TRUSTED_ROOT_DOMAIN}`);
}
/**
 * Política de domínios confiáveis: credenciais só podem ser enviadas por HTTPS
 * para venixcloud.com ou subdomínios. Sem usuário/senha embutidos, sem porta
 * customizada, sem query/fragmento.
 */
function assertTrustedUrl(raw) {
    let url;
    try {
        url = new URL(raw);
    }
    catch {
        throw new UntrustedUrlError('A URL informada é inválida.');
    }
    if (url.protocol !== 'https:') {
        throw new UntrustedUrlError('Somente HTTPS é permitido para a Venix Cloud.');
    }
    if (url.username || url.password) {
        throw new UntrustedUrlError('A URL não pode conter usuário ou senha.');
    }
    if (url.port && url.port !== '443') {
        throw new UntrustedUrlError('Portas customizadas não são permitidas.');
    }
    if (!isTrustedHost(url.hostname)) {
        throw new UntrustedUrlError(`O domínio "${url.hostname}" não é confiável. Use ${exports.TRUSTED_ROOT_DOMAIN} ou um subdomínio.`);
    }
    if (url.search || url.hash) {
        throw new UntrustedUrlError('A URL-base não pode ter query string nem fragmento.');
    }
    return url;
}
/** Valida e devolve a URL-base sem barra final. */
function normalizeBaseUrl(raw) {
    const url = assertTrustedUrl(raw.trim());
    return url.toString().replace(/\/+$/, '');
}
/** URLs que a extensão pode abrir no navegador (nenhuma credencial é enviada). */
function isSafeExternalUrl(raw) {
    try {
        const url = new URL(raw);
        return (url.protocol === 'https:' || url.protocol === 'http:') && !url.username && !url.password;
    }
    catch {
        return false;
    }
}
//# sourceMappingURL=trustedHosts.js.map