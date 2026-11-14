"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.generateCodeVerifier = generateCodeVerifier;
exports.codeChallengeS256 = codeChallengeS256;
exports.generateState = generateState;
exports.safeEqual = safeEqual;
const node_crypto_1 = require("node:crypto");
/** Verifier PKCE (RFC 7636): 32 bytes aleatórios em base64url = 43 caracteres. */
function generateCodeVerifier(bytes = 32) {
    return (0, node_crypto_1.randomBytes)(bytes).toString('base64url');
}
/** code_challenge com método S256. */
function codeChallengeS256(verifier) {
    return (0, node_crypto_1.createHash)('sha256').update(verifier, 'ascii').digest().toString('base64url');
}
/** Valor de `state` imprevisível, para proteção contra CSRF. */
function generateState() {
    return (0, node_crypto_1.randomBytes)(24).toString('base64url');
}
/** Comparação em tempo constante. */
function safeEqual(a, b) {
    const ba = Buffer.from(a);
    const bb = Buffer.from(b);
    return ba.length === bb.length && (0, node_crypto_1.timingSafeEqual)(ba, bb);
}
//# sourceMappingURL=pkce.js.map