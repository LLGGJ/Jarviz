/**
 * Matcher de exclusão no estilo .gitignore simplificado:
 *  - `nome` casa em qualquer nível (arquivo ou pasta);
 *  - `dir/` casa somente pastas; `/raiz` ancora na raiz;
 *  - `*`, `?` e `**` são suportados; `#` inicia comentário.
 * Negações (`!`) são ignoradas de propósito: nada aqui pode reincluir arquivos bloqueados.
 */
export type IgnoreMatcher = (relPath: string, isDirectory: boolean) => boolean;

function globToRegex(glob: string): string {
  let out = '';
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i] as string;
    if (c === '*') {
      if (glob[i + 1] === '*') {
        i++;
        if (glob[i + 1] === '/') {
          i++;
          out += '(?:.*/)?';
        } else {
          out += '.*';
        }
      } else {
        out += '[^/]*';
      }
    } else if (c === '?') {
      out += '[^/]';
    } else {
      out += c.replace(/[.+^${}()|[\]\\]/g, '\\$&');
    }
  }
  return out;
}

export function createIgnoreMatcher(patterns: readonly string[]): IgnoreMatcher {
  const compiled: Array<{ exact: RegExp; descendant: RegExp; dirOnly: boolean }> = [];
  for (const raw of patterns) {
    let p = raw.trim();
    if (!p || p.startsWith('#') || p.startsWith('!')) continue;
    const dirOnly = p.endsWith('/');
    if (dirOnly) p = p.slice(0, -1);
    const anchored = p.startsWith('/') || (p.includes('/') && !p.startsWith('**/'));
    p = p.replace(/^\//, '');
    const body = globToRegex(p);
    const prefix = anchored ? '^' : '^(?:.*/)?';
    compiled.push({ exact: new RegExp(`${prefix}${body}$`), descendant: new RegExp(`${prefix}${body}/.+$`), dirOnly });
  }
  return (relPath, isDirectory) => {
    const path = relPath.replace(/\\/g, '/').replace(/^\.?\//, '');
    return compiled.some(({ exact, descendant, dirOnly }) => {
      if (descendant.test(path)) return true;
      return exact.test(path) && (!dirOnly || isDirectory);
    });
  };
}
