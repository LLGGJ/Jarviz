export type Listener<T> = (event: T) => void;

export interface Disposable {
  dispose(): void;
}

/** Emissor mínimo, independente do módulo `vscode` (permite testar o núcleo sem o editor). */
export class Emitter<T> {
  private readonly listeners = new Set<Listener<T>>();

  readonly event = (listener: Listener<T>): Disposable => {
    this.listeners.add(listener);
    return {
      dispose: () => {
        this.listeners.delete(listener);
      },
    };
  };

  fire(event: T): void {
    for (const listener of [...this.listeners]) {
      try {
        listener(event);
      } catch {
        // Um ouvinte com defeito não pode quebrar os demais.
      }
    }
  }

  dispose(): void {
    this.listeners.clear();
  }
}
