/* Visão geral da Venix Cloud. Toda a renderização usa textContent (sem innerHTML). */
(function () {
  'use strict';
  const vscode = acquireVsCodeApi();
  const root = document.getElementById('root');
  const LABELS = {
    apps: 'Aplicações',
    games: 'Servidores de jogos',
    databases: 'Bancos de dados',
    storage: 'Objetos no CDN',
    snapshots: 'Snapshots',
  };

  function el(tag, props, children) {
    const node = document.createElement(tag);
    if (props) {
      for (const [k, v] of Object.entries(props)) {
        if (k === 'class') node.className = v;
        else if (k === 'text') node.textContent = v;
        else node.setAttribute(k, v);
      }
    }
    for (const child of children || []) if (child) node.appendChild(child);
    return node;
  }

  function action(label, name, primary) {
    const b = el('button', { type: 'button', text: label, class: primary ? 'primary' : '' });
    b.addEventListener('click', () => vscode.postMessage({ type: 'action', action: name }));
    return b;
  }

  function formatTime(ms) {
    return ms ? new Date(ms).toLocaleTimeString() : '—';
  }

  function stat(key, c) {
    let value;
    let cls = 'n';
    if (c.state === 'loaded') value = String(c.count);
    else if (c.state === 'loading') value = '…';
    else if (c.state === 'error') { value = 'erro'; cls = 'n err'; }
    else value = '—';
    const node = el('div', { class: 'stat', role: 'listitem' }, [
      el('div', { class: cls, text: value }),
      el('div', { class: 'l', text: LABELS[key] }),
    ]);
    if (c.error) node.title = c.error;
    return node;
  }

  function render(state) {
    root.replaceChildren();
    const conn = state.connection;
    const pillClass = { ok: 'ok', error: 'err', loading: 'load', unknown: '' }[conn.status] || '';
    const pillText = { ok: 'Conectado', error: 'Com falhas', loading: 'Atualizando…', unknown: 'Não verificado' }[conn.status] || '';

    const account = state.account;
    root.appendChild(
      el('section', { class: 'card', 'aria-label': 'Conta' }, [
        el('div', { class: 'row' }, [
          el('div', { class: 'brand' }, [el('span', { class: 'logo', 'aria-hidden': 'true' }), el('h1', { text: account ? account.label : 'Venix Cloud' })]),
          el('span', { class: 'pill ' + pillClass }, [el('span', { class: 'dot', 'aria-hidden': 'true' }), el('span', { text: pillText })]),
        ]),
        account && account.email ? el('div', { class: 'muted', text: account.email }) : null,
        account ? el('div', { class: 'muted', text: [account.plan, account.method].filter(Boolean).join(' • ') }) : null,
        el('div', { class: 'muted', text: 'Atualizado às ' + formatTime(conn.lastRefresh) }),
      ]),
    );

    const total = state.appStatus.online + state.appStatus.offline + state.appStatus.other;
    if (total > 0) {
      const pct = (n) => String((n / total) * 100) + '%';
      const bar = el('div', { class: 'bar', role: 'img', 'aria-label': state.appStatus.online + ' online, ' + state.appStatus.offline + ' offline, ' + state.appStatus.other + ' outros' }, [
        el('span', { class: 'on' }),
        el('span', { class: 'off' }),
        el('span', { class: 'other' }),
      ]);
      // CSSOM (não o atributo style): compatível com a CSP sem 'unsafe-inline'.
      bar.children[0].style.width = pct(state.appStatus.online);
      bar.children[1].style.width = pct(state.appStatus.offline);
      bar.children[2].style.width = pct(state.appStatus.other);
      root.appendChild(
        el('section', { class: 'card', 'aria-label': 'Situação das aplicações' }, [
          el('div', { class: 'row' }, [el('span', { text: 'Aplicações' }), el('span', { class: 'muted', text: state.appStatus.online + ' online · ' + state.appStatus.offline + ' offline' })]),
          bar,
        ]),
      );
    }

    root.appendChild(el('h2', { text: 'Recursos' }));
    const grid = el('div', { class: 'grid', role: 'list' });
    for (const key of Object.keys(LABELS)) grid.appendChild(stat(key, state.counts[key]));
    root.appendChild(grid);

    if (state.warnings.length > 0) {
      const ul = el('ul');
      for (const w of state.warnings) ul.appendChild(el('li', { text: w }));
      root.appendChild(el('section', { class: 'card warn gap', role: 'alert' }, [el('strong', { text: 'Avisos' }), ul]));
    }

    root.appendChild(
      el('div', { class: 'actions' }, [
        action('Atualizar', 'refresh', true),
        action('Verificar conexão', 'checkConnection'),
        action('Painel web', 'openWeb'),
        action('Sair', 'signOut'),
      ]),
    );
  }

  window.addEventListener('message', (event) => {
    const msg = event.data;
    if (msg && msg.type === 'state' && msg.state) render(msg.state);
  });
  vscode.postMessage({ type: 'ready' });
})();
