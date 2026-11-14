"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.toBlob = toBlob;
/** Cria um Blob a partir de bytes (o cast evita incompatibilidades de tipagem entre versões do TypeScript). */
function toBlob(data) {
    return new Blob([data]);
}
//# sourceMappingURL=blob.js.map