"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.VenixApiError = void 0;
exports.baseMessageFor = baseMessageFor;
exports.kindFromStatus = kindFromStatus;
exports.makeApiError = makeApiError;
exports.describeError = describeError;
const redact_1 = require("../security/redact");
/** Erro tipado da camada de API. Nunca carrega token, cabeçalhos ou corpo da requisição. */
class VenixApiError extends Error {
    kind;
    status;
    code;
    apiMessage;
    retryAfterMs;
    constructor(init) {
        super(init.message);
        this.name = 'VenixApiError';
        this.kind = init.kind;
        this.status = init.status;
        this.code = init.code;
        this.apiMessage = init.apiMessage ? (0, redact_1.sanitizeMessage)(init.apiMessage) : undefined;
        this.retryAfterMs = init.retryAfterMs;
    }
}
exports.VenixApiError = VenixApiError;
const BASE_MESSAGES = {
    'not-authenticated': 'Você não está conectado. Entre com sua conta Venix Cloud.',
    unauthorized: 'Sessão expirada, revogada ou token inválido. Entre novamente.',
    forbidden: 'Operação negada (403). Pode ser limite do plano/RAM excedido, teste grátis expirado ou permissão insuficiente.',
    'quota-exceeded': 'Limite do plano excedido (RAM/recursos) ou teste grátis expirado. Faça upgrade ou libere recursos.',
    'invalid-input': 'A API rejeitou os dados enviados.',
    'not-found': 'Recurso não encontrado. Verifique o ID ou o caminho informado.',
    conflict: 'Conflito: nome ou subdomínio já está em uso.',
    'not-allowed': 'Operação não permitida para este recurso.',
    'payload-too-large': 'O arquivo excede o limite permitido (100 MB no CDN, 500 MB em aplicações).',
    'rate-limited': 'Limite de requisições atingido (60 por minuto). Aguarde um instante.',
    'server-unavailable': 'Serviço ou nó da Venix Cloud temporariamente indisponível. Tente novamente em instantes.',
    'server-error': 'Erro interno no servidor da Venix Cloud.',
    timeout: 'A requisição demorou demais e foi cancelada (timeout).',
    network: 'Falha de conexão com a Venix Cloud. Verifique sua internet.',
    'invalid-response': 'A Venix Cloud devolveu uma resposta inesperada.',
    cancelled: 'Operação cancelada.',
    unexpected: 'A Venix Cloud devolveu um erro inesperado.',
};
function baseMessageFor(kind) {
    return BASE_MESSAGES[kind];
}
function kindFromStatus(status, code) {
    if (status === 400)
        return 'invalid-input';
    if (status === 401)
        return 'unauthorized';
    if (status === 403)
        return code === 'QUOTA_EXCEEDED' ? 'quota-exceeded' : 'forbidden';
    if (status === 404)
        return 'not-found';
    if (status === 405)
        return 'not-allowed';
    if (status === 409)
        return 'conflict';
    if (status === 413)
        return 'payload-too-large';
    if (status === 429)
        return 'rate-limited';
    if (status === 502 || status === 503 || status === 504)
        return 'server-unavailable';
    if (status >= 500)
        return 'server-error';
    if (status >= 300 && status < 400)
        return 'invalid-response';
    return 'unexpected';
}
function makeApiError(kind, extra = {}) {
    return new VenixApiError({ kind, message: baseMessageFor(kind), ...extra });
}
/** Mensagem segura para o usuário final (sem stack, sem tokens). */
function describeError(err) {
    if (err instanceof VenixApiError) {
        const detail = err.apiMessage && err.kind !== 'rate-limited' ? ` Detalhe: ${err.apiMessage}` : '';
        const retry = err.kind === 'rate-limited' && err.retryAfterMs
            ? ` Tente novamente em ${Math.ceil(err.retryAfterMs / 1000)}s.`
            : '';
        return `${err.message}${retry}${detail}`;
    }
    if (err instanceof Error && err.name === 'AbortError')
        return BASE_MESSAGES.cancelled;
    if (err instanceof Error && err.message)
        return (0, redact_1.sanitizeMessage)(err.message);
    return 'Ocorreu um erro inesperado.';
}
//# sourceMappingURL=errors.js.map