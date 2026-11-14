import { redactSecrets } from '../security/redact';

export interface LogSink {
  appendLine(line: string): void;
}

export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

/**
 * Logger que remove tokens e parâmetros sensíveis de TODAS as mensagens.
 * Nunca recebe corpos de requisição nem stack traces.
 */
export class Logger {
  constructor(
    private readonly sink: LogSink,
    private readonly knownSecrets: () => readonly string[] = () => [],
  ) {}

  private write(level: LogLevel, message: string): void {
    const line = `${new Date().toISOString()} ${level.toUpperCase().padEnd(5)} ${redactSecrets(message, this.knownSecrets())}`;
    this.sink.appendLine(line);
  }

  debug(message: string): void {
    this.write('debug', message);
  }
  info(message: string): void {
    this.write('info', message);
  }
  warn(message: string): void {
    this.write('warn', message);
  }
  error(message: string, err?: unknown): void {
    const detail = err instanceof Error ? `${err.name}: ${err.message}` : err === undefined ? '' : String(err);
    this.write('error', detail ? `${message} (${detail})` : message);
  }
}

export const nullLogger = new Logger({ appendLine: () => undefined });
