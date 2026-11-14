"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Emitter = void 0;
/** Emissor mínimo, independente do módulo `vscode` (permite testar o núcleo sem o editor). */
class Emitter {
    listeners = new Set();
    event = (listener) => {
        this.listeners.add(listener);
        return {
            dispose: () => {
                this.listeners.delete(listener);
            },
        };
    };
    fire(event) {
        for (const listener of [...this.listeners]) {
            try {
                listener(event);
            }
            catch {
                // Um ouvinte com defeito não pode quebrar os demais.
            }
        }
    }
    dispose() {
        this.listeners.clear();
    }
}
exports.Emitter = Emitter;
//# sourceMappingURL=events.js.map