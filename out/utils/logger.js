"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.nullLogger = exports.Logger = void 0;
const redact_1 = require("../security/redact");
/**
 * Logger que remove tokens e parâmetros sensíveis de TODAS as mensagens.
 * Nunca recebe corpos de requisição nem stack traces.
 */
class Logger {
    sink;
    knownSecrets;
    constructor(sink, knownSecrets = () => []) {
        this.sink = sink;
        this.knownSecrets = knownSecrets;
    }
    write(level, message) {
        const line = `${new Date().toISOString()} ${level.toUpperCase().padEnd(5)} ${(0, redact_1.redactSecrets)(message, this.knownSecrets())}`;
        this.sink.appendLine(line);
    }
    debug(message) {
        this.write('debug', message);
    }
    info(message) {
        this.write('info', message);
    }
    warn(message) {
        this.write('warn', message);
    }
    error(message, err) {
        const detail = err instanceof Error ? `${err.name}: ${err.message}` : err === undefined ? '' : String(err);
        this.write('error', detail ? `${message} (${detail})` : message);
    }
}
exports.Logger = Logger;
exports.nullLogger = new Logger({ appendLine: () => undefined });
//# sourceMappingURL=logger.js.map