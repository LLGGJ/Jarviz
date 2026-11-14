# Changelog

## 1.0.0

Primeira versão.

- Login OAuth 2.0 (Authorization Code + PKCE S256, cliente público) com callback em `127.0.0.1`, modo manual (colar URL) e modo `vscode://`.
- Alternativa documentada: token de API (rotulado como token, não como OAuth).
- Sessão em `SecretStorage`; expiração/revogação tratadas; "Sair" apaga todos os dados locais.
- Provedor de autenticação do VS Code (menu Contas).
- Árvore nativa: Conta, Aplicações, Servidores de jogos, Bancos de dados, Armazenamento, Snapshots, Configurações.
- Visão geral (webview com CSP restritiva) e painel de detalhes com segredos mascarados.
- Aplicações: iniciar/parar/reiniciar, deploy, editar RAM/comando/runtime, criar, excluir, logs ao vivo (SSE), publicar projeto, enviar arquivo.
- Servidores de jogos: criar, comando de console, excluir.
- Bancos de dados: criar, baixar certificados mTLS, excluir.
- CDN: enviar arquivo, copiar/abrir URL.
- Snapshots: criar, baixar, excluir.
- Publicação com pré-visualização, `.venixignore` e bloqueio permanente de arquivos secretos.
