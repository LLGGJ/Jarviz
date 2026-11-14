# Venix Cloud para VS Code

Gerencie sua conta da [Venix Cloud](https://venixcloud.com) sem sair do editor: aplicações, servidores de jogos, bancos de dados, CDN e snapshots, com login por OAuth 2.0 (PKCE) e credenciais guardadas no armazenamento seguro do VS Code.

> **Não oficial?** Esta extensão usa a API pública v1.4 (`api-docs.html`) e, para login e listagem de aplicações, os mesmos endpoints da CLI oficial `venix` v1.0.21. A seção [Limitações](#limitações-conhecidas) separa com clareza o que é documentado do que foi confirmado apenas pela CLI.

## Funcionalidades

| Área | O que faz |
| --- | --- |
| **Conta** | Entrar (OAuth), entrar com token de API, sair, mostrar conta, verificar conexão. |
| **Visão geral** | Conta, estado da conexão, contagem por recurso, aplicações online/offline e avisos (erros por categoria, sessão perto de expirar). Só mostra o que a API devolve. |
| **Aplicações** | Listar, ver detalhes, iniciar/parar/reiniciar, disparar deploy (GitHub), editar RAM/comando de início/runtime, criar (com ou sem o código do workspace), excluir, logs ao vivo, abrir no navegador. |
| **Publicação** | Vincular a pasta a uma aplicação (`.venix.json`, compatível com a CLI), publicar o projeto (ZIP → upload → extração → reinício) com pré-visualização, enviar um arquivo avulso. |
| **Servidores de jogos** | Listar, ver detalhes, criar (catálogo de jogos da API), enviar comando ao console, excluir. |
| **Bancos de dados** | Listar, ver detalhes (credenciais ocultas), criar, baixar certificados mTLS, excluir. |
| **Armazenamento/CDN** | Listar objetos, enviar arquivo (1 KB–100 MB), copiar/abrir URL. |
| **Snapshots** | Listar, criar (aplicações e bancos), baixar, excluir. |

A barra de atividades ganha o ícone da Venix Cloud com a **Visão geral** e a árvore **Recursos**. Há também um item na barra de status e entradas no menu de contexto do Explorer.

## Pré-requisitos

- VS Code **1.90** ou superior (Node 20 embutido).
- Uma conta Venix Cloud.
- Para desenvolver: Node.js 20+ e npm.

## Instalação

**A partir de um `.vsix`:**

1. `Extensões` → menu `…` → **Instalar do VSIX…** e escolha `venix-cloud-1.0.0.vsix`.
   Ou: `code --install-extension venix-cloud-1.0.0.vsix`.
2. Clique no ícone da Venix Cloud na barra lateral e em **Entrar com Venix Cloud**.

## Autenticação

### OAuth 2.0 (recomendado)

Fluxo *Authorization Code* com **PKCE (S256)** para aplicativo instalado (cliente público, **sem `client_secret`**):

1. A extensão gera `code_verifier`, `code_challenge` e um `state` aleatório e sobe um servidor temporário em `127.0.0.1`.
2. Abre `https://venixcloud.com/en/auth/bridge` no navegador (`vscode.env.openExternal`).
3. Você entra na Venix Cloud e autoriza. **Sua senha nunca passa pela extensão.**
4. O navegador volta para o callback; a extensão valida o `state` (comparação em tempo constante), o cabeçalho `Host` e o caminho.
5. Troca o código por token em `POST https://api.venixcloud.com/v1/oauth2/token` (JSON, com `code_verifier`).
6. Valida o token chamando a API (`GET /me`) **antes** de guardá-lo; só então grava em `context.secrets`.

Se o navegador não alcança `127.0.0.1` (SSH, WSL, Codespaces) ou a porta está ocupada, use **Venix Cloud: Entrar (colar URL de retorno)**: autorize no navegador e cole a URL final (mesmo que mostre erro de conexão). O `state` é validado do mesmo jeito.

### Registro do aplicativo OAuth e URI de callback

- **client_id** usado: `venix_54620c6a2b626ec6d2` (identificador público; fornecido pelo mantenedor).
- **Redirect URIs que precisam estar registrados para esse client_id na Venix Cloud:**
  - `http://127.0.0.1:53682/callback` (modo padrão `loopback`, mesmo padrão da CLI oficial);
  - `vscode://venixcloud.venix-cloud/callback` (opcional, só para `venixCloud.oauth.redirectMode = "vscode"`; ajuste o *publisher* se o seu for outro).
- **Não foi possível confirmar** quais redirect URIs estão registrados para esse `client_id`. Se o login terminar com erro de redirecionamento, ajuste `venixCloud.oauth.callbackPort` para a porta registrada ou peça à Venix o registro do URI acima.

### Token de API (alternativa)

**Venix Cloud: Entrar com token de API** pede o token gerado no painel (campo mascarado), valida-o na API e o guarda em `SecretStorage`. Aparece na interface como **"Token de API"** — nunca como OAuth.

### O que NÃO acontece

- Senhas não são pedidas nem armazenadas; não há formulários de login próprios.
- Tokens não vão para `settings.json`, `globalState`, arquivos, logs, webviews ou terceiros (o logger remove `Bearer …`, `access_token`, `code`, `state` e o token ativo de toda mensagem).
- O código-fonte não contém tokens nem `client_secret`.

## Configuração

| Configuração | Padrão | Descrição |
| --- | --- | --- |
| `venixCloud.apiBaseUrl` | `https://api.venixcloud.com/v1` | Escopo *machine* (o workspace não consegue alterá-la). Só aceita **HTTPS em `venixcloud.com` ou subdomínios**; qualquer outro valor é rejeitado, a extensão volta ao padrão e avisa. |
| `venixCloud.autoRefresh` | `false` | Atualização automática (só com a janela em foco). |
| `venixCloud.refreshInterval` | `60` | Intervalo em segundos (30–3600). Cada rodada faz 5–6 requisições; o limite da API é 60/min. |
| `venixCloud.confirmDestructiveActions` | `true` | Confirmação explícita antes de excluir recursos. |
| `venixCloud.oauth.redirectMode` | `loopback` | `loopback` ou `vscode`. |
| `venixCloud.oauth.callbackPort` | `53682` | Porta do callback em `127.0.0.1`. |
| `venixCloud.publish.exclude` | `node_modules`, `*.log`, … | Exclusões da publicação (estilo `.gitignore`). |
| `venixCloud.publish.restartAfterUpload` | `true` | Reinicia a aplicação após publicar. |

## Permissões

- A API aplica o plano da conta: respostas **403** indicam limite de RAM/recursos excedido, teste grátis de 14 dias expirado ou permissão insuficiente do token. A extensão mostra a mensagem da API.
- Não há escopos OAuth configuráveis (a CLI oficial não envia `scope`). **Não está confirmado** que tokens OAuth tenham acesso a *todos* os módulos: a CLI oficial só usa aplicações e snapshots. Se jogos/bancos/CDN devolverem 401/403 com OAuth, use um token de API.

## Segurança da publicação

Ao publicar, a extensão **sempre** bloqueia (nada na configuração ou no `.venixignore` reinclui): `.env*`, `*.pem`, `*.key`, `*.p12`, `*.pfx`, `*.jks`, `id_rsa*`/`id_ed25519*`, `.npmrc`, `.netrc`, `credentials*`, `secrets*`, `service-account*.json`, `.venix.json`, `.git/`, `.ssh/`, `.aws/`, `.kube/`; links simbólicos são ignorados. Antes de enviar, você vê a pré-visualização (contagem, tamanho, bloqueados, lista completa). Workspaces não confiáveis não publicam. Envios avulsos de arquivos com cara de segredo pedem confirmação explícita.

## Comandos

Todos aparecem na paleta como **Venix Cloud: …**.

`Entrar` · `Entrar (colar URL de retorno)` · `Entrar com token de API` · `Sair` · `Atualizar` · `Mostrar Conta` · `Abrir Painel Web` · `Verificar Conexão` · `Configurações` · `Criar aplicação` · `Iniciar/Parar/Reiniciar aplicação` · `Disparar deploy (GitHub)` · `Editar configurações da aplicação` · `Excluir aplicação` · `Ver logs ao vivo` · `Abrir aplicação no navegador` · `Vincular projeto a uma aplicação` · `Publicar projeto na aplicação` · `Enviar arquivo para uma aplicação` · `Criar/Excluir servidor de jogo` · `Enviar comando ao console do servidor` · `Criar/Excluir banco de dados` · `Baixar certificados do banco` · `Enviar arquivo para o CDN` · `Criar/Baixar/Excluir snapshot`.

Operações de exclusão exigem **digitar o nome do recurso**.

## Endpoints usados

**Documentados em `api-docs.html` v1.4:** `POST /apps/create`, `POST /apps/:id/action`, `DELETE|PATCH /apps/:id`, `POST /apps/:id/deploy/trigger`, `POST|DELETE /apps/:id/files/*` (upload, extract, delete), `GET /instances/stream/:id` (SSE), `GET|POST|DELETE /instances/games…` (incl. `/eggs`, `/:id/command`), `GET|POST|DELETE /instances/databases…` (incl. `/:id/credentials/zip`), `GET|POST /objects`, `GET|POST|DELETE /snapshots…`.

**Não documentados; confirmados pelo código da CLI oficial:** `GET /me` (conta e lista `applications`), `GET /apps/status` (opcional), `GET https://venixcloud.com/en/auth/bridge` (autorização) e `POST /oauth2/token`.

## Desenvolvimento

```bash
npm install
npm run compile      # tsc → out/
npm run typecheck    # inclui os testes
npm run lint
npm test
```

`F5` abre um *Extension Development Host* (`.vscode/launch.json`).

A extensão não tem dependências de runtime. Núcleo (API, OAuth, sessão, workspace) não importa `vscode`, o que permite testá-lo isoladamente; a camada do editor é testada contra um mock em memória (`test/mocks/vscode.ts`).

### Testes

Usam o executor nativo `node:test` (via `tsx`), sem dependências extras. Cobrem: PKCE (vetor da RFC 7636), validação de `state`/Host/caminho do callback (servidor real em loopback), troca de código, sessão e `SecretStorage`, cliente HTTP (cabeçalhos, query, multipart, timeouts, retentativas só em GET, limite de 60/min, mapeamento de erros, vazamento de token), validações por endpoint, parsers, scanner/ZIP/`.venixignore`, bloqueio de segredos, registro e consistência de comandos/menus com o `package.json`, confirmações destrutivas, árvore, webviews (CSP, mensagens, mascaramento) e fluxo de publicação. Nenhum teste usa credenciais reais nem rede real.

### Empacotar (`.vsix`)

```bash
npm run package      # vsce package --no-dependencies  → venix-cloud-1.0.0.vsix
```

Antes de publicar no Marketplace, defina o seu `publisher` no `package.json` (o valor `venixcloud` é provisório) e, se mudar, ajuste o URI `vscode://…` acima.

## Limitações conhecidas

- **OAuth não está na documentação oficial.** Endpoints, formato JSON da troca de token e `client_id` vêm da CLI oficial e do cadastro informado; confirme com a Venix os redirect URIs registrados.
- **Sem refresh de token.** A CLI oficial guarda o `refresh_token` mas nunca o usa, e a documentação não o descreve; por isso a extensão não o armazena nem renova. Token expirado ou revogado (401) encerra a sessão e pede novo login.
- **Respostas de listagem sem schema documentado.** Campos são lidos de forma tolerante (várias chaves candidatas) e o painel de detalhes mostra exatamente o que a API devolveu. Se a API usar nomes inesperados, o recurso aparece com ID como nome ou sem alguns campos — nada é inventado.
- **Sem estatísticas de armazenamento/uso**, e sem estado de sincronização remoto: a API não oferece listagem de arquivos com hash. A extensão só registra, localmente, o último envio.
- **CDN:** a API não documenta exclusão de objetos; por isso não há esse comando.
- **Snapshots:** só aplicações e bancos (conforme a documentação).
- **Logs:** transmissão ao vivo (SSE); histórico não é documentado.
- Exige conexão para `127.0.0.1` no navegador no modo padrão (use o modo manual em ambientes remotos).
- Aplicações até 500 MB e CDN até 100 MB (limites da API); projetos com mais de 65.535 arquivos não são empacotados.

## Solução de problemas

| Sintoma | O que fazer |
| --- | --- |
| "A porta 53682 já está em uso" | Feche outro `venix login`/instância, ou use **Entrar (colar URL de retorno)**. |
| Erro de redirecionamento no navegador | O redirect URI não está registrado para o `client_id`; veja [Registro](#registro-do-aplicativo-oauth-e-uri-de-callback). |
| Navegador não abre / ambiente remoto | Use o modo manual; o botão de cópia no campo copia o link de autorização. |
| "Sessão expirada, revogada ou token inválido" | Entre novamente. |
| 403 em jogos/bancos/CDN com OAuth | Pode ser plano/RAM/teste expirado ou escopo do token; tente token de API. |
| "Limite de requisições atingido" | A API permite 60/min; desative `autoRefresh` ou aumente o intervalo. |
| `venixCloud.apiBaseUrl` rejeitada | Só `https://*.venixcloud.com` é aceito; o padrão é usado. |
| Logs da extensão | Painel **Saída → Venix Cloud** (sem tokens ou corpos de requisição). |

## Licença

MIT.
