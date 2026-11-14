/** Cria um Blob a partir de bytes (o cast evita incompatibilidades de tipagem entre versões do TypeScript). */
export function toBlob(data: Uint8Array): Blob {
  return new Blob([data as unknown as BlobPart]);
}
