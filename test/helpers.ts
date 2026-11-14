export interface RecordedCall {
  url: string;
  method: string;
  headers: Record<string, string>;
  body?: BodyInit | null;
}

export type Responder = (call: RecordedCall, index: number) => Response | Promise<Response>;

export function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(typeof body === 'string' ? body : JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', ...headers },
  });
}

/** fetch falso que grava as chamadas e responde conforme o `responder`. */
export function fakeFetch(responder: Responder): { fetch: typeof fetch; calls: RecordedCall[] } {
  const calls: RecordedCall[] = [];
  const impl = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
    const headers: Record<string, string> = {};
    new Headers(init?.headers).forEach((v, k) => {
      headers[k.toLowerCase()] = v;
    });
    const call: RecordedCall = { url: String(input), method: init?.method ?? 'GET', headers, body: init?.body };
    calls.push(call);
    if (init?.signal?.aborted) throw new DOMException('aborted', 'AbortError');
    return responder(call, calls.length - 1);
  };
  return { fetch: impl as typeof fetch, calls };
}

export const noSleep = async (): Promise<void> => undefined;
export const TOKEN = 'tok_TEST_0123456789abcdef';
