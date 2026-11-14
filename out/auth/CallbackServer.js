"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.LoopbackReceiver = void 0;
const http = __importStar(require("node:http"));
const constants_1 = require("../constants");
const callbackPage_1 = require("./callbackPage");
const OAuthProvider_1 = require("./OAuthProvider");
/**
 * Recebe o redirecionamento em http://127.0.0.1:<porta>/callback (RFC 8252, §7.3).
 * Defesas: só escuta em loopback, valida o cabeçalho Host (anti DNS-rebinding),
 * aceita apenas GET no caminho exato e não carrega recursos externos.
 */
class LoopbackReceiver {
    callbackPath;
    server;
    boundPort;
    constructor(port, callbackPath = constants_1.OAUTH_CALLBACK_PATH) {
        this.callbackPath = callbackPath;
        this.boundPort = port;
    }
    get redirectUri() {
        return `http://127.0.0.1:${this.boundPort}${this.callbackPath}`;
    }
    listen(handler) {
        return new Promise((resolve, reject) => {
            const server = http.createServer((req, res) => this.handle(req, res, handler));
            server.on('error', (err) => {
                reject(new OAuthProvider_1.OAuthError('listen-failed', err.code === 'EADDRINUSE'
                    ? `A porta ${this.boundPort} já está em uso.`
                    : 'Não foi possível abrir o servidor local de callback.'));
            });
            server.listen(this.boundPort, '127.0.0.1', () => {
                this.boundPort = server.address().port;
                this.server = server;
                resolve();
            });
        });
    }
    dispose() {
        const server = this.server;
        this.server = undefined;
        if (server) {
            server.close();
            server.closeAllConnections();
        }
    }
    handle(req, res, handler) {
        const send = (status, title, message) => {
            res.writeHead(status, {
                'Content-Type': 'text/html; charset=utf-8',
                'Cache-Control': 'no-store',
                'Referrer-Policy': 'no-referrer',
                'X-Content-Type-Options': 'nosniff',
                'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'",
            });
            res.end((0, callbackPage_1.renderCallbackPage)(status === 200, title, message));
        };
        const host = (req.headers.host ?? '').toLowerCase();
        if (host !== `127.0.0.1:${this.boundPort}` && host !== `localhost:${this.boundPort}`) {
            send(400, 'Requisição inválida', 'Cabeçalho Host não permitido.');
            return;
        }
        if (req.method !== 'GET') {
            send(405, 'Método não permitido', 'Use o link de autorização da Venix Cloud.');
            return;
        }
        let url;
        try {
            url = new URL(req.url ?? '/', `http://127.0.0.1:${this.boundPort}`);
        }
        catch {
            send(400, 'Requisição inválida', 'URL malformada.');
            return;
        }
        if (url.pathname !== this.callbackPath) {
            send(404, 'Não encontrado', 'Caminho desconhecido.');
            return;
        }
        const params = {};
        for (const [k, v] of url.searchParams) {
            if (!(k in params))
                params[k] = v;
        }
        const result = handler(params);
        if (result.ok)
            send(200, 'Autorização recebida', 'A Venix Cloud autorizou a extensão. Finalizando o login no VS Code.');
        else
            send(400, result.title, result.message);
    }
}
exports.LoopbackReceiver = LoopbackReceiver;
//# sourceMappingURL=CallbackServer.js.map