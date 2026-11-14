// Faz `import 'vscode'` resolver para o mock em memória durante os testes (fora do VS Code real).
import Module from 'node:module';
import * as path from 'node:path';

const mockPath = path.join(__dirname, '..', 'mocks', 'vscode.ts');
const original = (Module as unknown as { _resolveFilename: (...a: unknown[]) => string })._resolveFilename;
(Module as unknown as { _resolveFilename: (...a: unknown[]) => string })._resolveFilename = function (request: unknown, ...rest: unknown[]) {
  if (request === 'vscode') return mockPath;
  return original.call(this, request, ...rest);
};
