"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createIgnoreMatcher = createIgnoreMatcher;
function globToRegex(glob) {
    let out = '';
    for (let i = 0; i < glob.length; i++) {
        const c = glob[i];
        if (c === '*') {
            if (glob[i + 1] === '*') {
                i++;
                if (glob[i + 1] === '/') {
                    i++;
                    out += '(?:.*/)?';
                }
                else {
                    out += '.*';
                }
            }
            else {
                out += '[^/]*';
            }
        }
        else if (c === '?') {
            out += '[^/]';
        }
        else {
            out += c.replace(/[.+^${}()|[\]\\]/g, '\\$&');
        }
    }
    return out;
}
function createIgnoreMatcher(patterns) {
    const compiled = [];
    for (const raw of patterns) {
        let p = raw.trim();
        if (!p || p.startsWith('#') || p.startsWith('!'))
            continue;
        const dirOnly = p.endsWith('/');
        if (dirOnly)
            p = p.slice(0, -1);
        const anchored = p.startsWith('/') || (p.includes('/') && !p.startsWith('**/'));
        p = p.replace(/^\//, '');
        const body = globToRegex(p);
        const prefix = anchored ? '^' : '^(?:.*/)?';
        compiled.push({ exact: new RegExp(`${prefix}${body}$`), descendant: new RegExp(`${prefix}${body}/.+$`), dirOnly });
    }
    return (relPath, isDirectory) => {
        const path = relPath.replace(/\\/g, '/').replace(/^\.?\//, '');
        return compiled.some(({ exact, descendant, dirOnly }) => {
            if (descendant.test(path))
                return true;
            return exact.test(path) && (!dirOnly || isDirectory);
        });
    };
}
//# sourceMappingURL=ignore.js.map