const ESCAPES: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export const escapeHtml = (s: string): string => s.replace(/[&<>"']/g, (c) => ESCAPES[c] ?? c);

/** Página exibida no navegador após o redirecionamento. Sem scripts nem recursos externos. */
export function renderCallbackPage(ok: boolean, title: string, message: string): string {
  const accent = ok ? '#22D3EE' : '#F87171';
  const hint = ok ? 'Você já pode fechar esta aba e voltar ao VS Code.' : 'Volte ao VS Code e tente entrar novamente.';
  return `<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>${escapeHtml(title)} • Venix Cloud</title>
<style>
:root{color-scheme:dark}
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#020202;color:#F4F4F5;font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{width:min(92vw,420px);padding:32px 28px;border:1px solid #1F1F26;border-radius:14px;background:#09090D;text-align:center}
.brand{font-size:12px;letter-spacing:.14em;text-transform:uppercase;color:#A1A1AA}
.dot{display:inline-block;width:10px;height:10px;border-radius:3px;background:#06B6D4;margin-right:8px;transform:rotate(45deg)}
h1{margin:20px 0 8px;font-size:22px;color:${accent}}
p{margin:0;color:#A1A1AA;line-height:1.55}
.hint{margin-top:18px;font-size:13px}
</style></head><body><main role="main">
<div class="brand"><span class="dot"></span>Venix Cloud</div>
<h1>${escapeHtml(title)}</h1><p>${escapeHtml(message)}</p><p class="hint">${hint}</p>
</main></body></html>`;
}
