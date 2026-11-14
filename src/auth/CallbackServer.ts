import * as http from 'node:http';
import type { AddressInfo } from 'node:net';
import { OAUTH_CALLBACK_PATH } from '../constants';
import { renderCallbackPage } from './callbackPage';
import { OAuthError, type CallbackParams, type CallbackReceiver, type CallbackResult } from './OAuthProvider';

/**
 * Recebe o redirecionamento em http://127.0.0.1:<porta>/callback (RFC 8252, §7.3).
 * Defesas: só escuta em loopback, valida o cabeçalho Host (anti DNS-rebinding),
 * aceita apenas GET no caminho exato e não carrega recursos externos.
 */
export class LoopbackReceiver implements CallbackReceiver {
  private server?: http.Server;
  private boundPort: number;

  constructor(
    port: number,
    private readonly callbackPath: string = OAUTH_CALLBACK_PATH,
  ) {
    this.boundPort = port;
  }

  get redirectUri(): string {
    return `http://127.0.0.1:${this.boundPort}${this.callbackPath}`;
  }

  listen(handler: (params: CallbackParams) => CallbackResult): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      const server = http.createServer((req, res) => this.handle(req, res, handler));
      server.on('error', (err: NodeJS.ErrnoException) => {
        reject(
          new OAuthError(
            'listen-failed',
            err.code === 'EADDRINUSE'
              ? `A porta ${this.boundPort} já está em uso.`
              : 'Não foi possível abrir o servidor local de callback.',
          ),
        );
      });
      server.listen(this.boundPort, '127.0.0.1', () => {
        this.boundPort = (server.address() as AddressInfo).port;
        this.server = server;
        resolve();
      });
    });
  }

  dispose(): void {
    const server = this.server;
    this.server = undefined;
    if (server) {
      server.close();
      server.closeAllConnections();
    }
  }

  private handle(req: http.IncomingMessage, res: http.ServerResponse, handler: (p: CallbackParams) => CallbackResult): void {
    const send = (status: number, title: string, message: string): void => {
      res.writeHead(status, {
        'Content-Type': 'text/html; charset=utf-8',
        'Cache-Control': 'no-store',
        'Referrer-Policy': 'no-referrer',
        'X-Content-Type-Options': 'nosniff',
        'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'",
      });
      res.end(renderCallbackPage(status === 200, title, message));
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
    let url: URL;
    try {
      url = new URL(req.url ?? '/', `http://127.0.0.1:${this.boundPort}`);
    } catch {
      send(400, 'Requisição inválida', 'URL malformada.');
      return;
    }
    if (url.pathname !== this.callbackPath) {
      send(404, 'Não encontrado', 'Caminho desconhecido.');
      return;
    }
    const params: CallbackParams = {};
    for (const [k, v] of url.searchParams) {
      if (!(k in params)) params[k] = v;
    }
    const result = handler(params);
    if (result.ok) send(200, 'Autorização recebida', 'A Venix Cloud autorizou a extensão. Finalizando o login no VS Code.');
    else send(400, result.title, result.message);
  }
}
