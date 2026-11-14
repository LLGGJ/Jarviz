/* Painel de detalhes. Valores sensíveis chegam mascarados; "Revelar" pede o valor à extensão. */
(function () {
  'use strict';
  const vscode = acquireVsCodeApi();
  const root = document.getElementById('root');
  const cells = new Map();
  const revealed = new Set();

  function el(tag, props, children) {
    const node = document.createElement(tag);
    if (props) for (const [k, v] of Object.entries(props)) (k === 'text' ? (node.textContent = v) : node.setAttribute(k, v));
    for (const c of children || []) if (c) node.appendChild(c);
    return node;
  }

  function button(label, title, onClick) {
    const b = el('button', { type: 'button', text: label, title: title, 'aria-label': title });
    b.addEventListener('click', onClick);
    return b;
  }

  function looksLikeUrl(v) {
    return /^https?:\/\/\S+$/i.test(v);
  }

  function render(init) {
    root.replaceChildren();
    root.appendChild(el('h1', { text: init.title }));
    if (init.subtitle) root.appendChild(el('p', { class: 'muted', text: init.subtitle }));
    if (init.fields.length === 0) {
      root.appendChild(el('p', { class: 'muted', text: 'A API não devolveu campos adicionais para este recurso.' }));
      return;
    }
    const table = el('table', { 'aria-label': 'Campos do recurso' });
    for (const f of init.fields) {
      const value = el('span', { text: f.display });
      cells.set(f.key, value);
      const btns = el('span', { class: 'btns' });
      if (f.sensitive) {
        const toggle = button('Revelar', 'Revelar ' + f.label, () => {
          if (revealed.has(f.key)) {
            revealed.delete(f.key);
            toggle.textContent = 'Revelar';
            vscode.postMessage({ type: 'hide', key: f.key });
          } else {
            revealed.add(f.key);
            toggle.textContent = 'Ocultar';
            vscode.postMessage({ type: 'reveal', key: f.key });
          }
        });
        btns.appendChild(toggle);
      }
      btns.appendChild(button('Copiar', 'Copiar ' + f.label, () => vscode.postMessage({ type: 'copy', key: f.key })));
      if (!f.sensitive && looksLikeUrl(f.display)) btns.appendChild(button('Abrir', 'Abrir ' + f.label, () => vscode.postMessage({ type: 'open', key: f.key })));
      table.appendChild(el('tr', null, [el('th', { scope: 'row', text: f.label }), el('td', null, [value, btns])]));
    }
    root.appendChild(table);
  }

  window.addEventListener('message', (event) => {
    const msg = event.data;
    if (!msg) return;
    if (msg.type === 'init') render(msg);
    else if (msg.type === 'value' && cells.has(msg.key)) cells.get(msg.key).textContent = String(msg.value);
  });
  vscode.postMessage({ type: 'ready' });
})();
