<div align="center">

# 🤖 Jarvis

**Um assistente de IA para Android com cara de robozinho: ele fala, escuta, usa seu celular, edita vídeo, cria sites, mexe no seu PC e na sua casa.**

[![Build](https://img.shields.io/github/actions/workflow/status/LLGGJ/Jarviz/build.yml?label=build&style=for-the-badge)](https://github.com/LLGGJ/Jarviz/actions)
[![Versão](https://img.shields.io/github/v/release/LLGGJ/Jarviz?include_prereleases&label=vers%C3%A3o&style=for-the-badge)](https://github.com/LLGGJ/Jarviz/releases)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)

[**⬇️ Baixar o APK**](https://github.com/LLGGJ/Jarviz/releases) · [Começar](#-primeiros-passos) · [Recursos](#-recursos) · [Segurança](#-segurança-e-privacidade) · [Para desenvolvedores](#-para-desenvolvedores)

</div>

---

## ✨ O que é

O Jarvis é um app Android feito em **Kotlin + Jetpack Compose**. A tela inteira é um robozinho: ele pisca, olha pros lados e mostra com a expressão o que está fazendo (pesquisando, programando, mexendo no celular, pensando, falando). Você toca nele para falar, segura para digitar. Não tem painel de status nem menu cheio de botão.

> ⚠️ **Projeto em desenvolvimento.** Cada recurso abaixo foi escrito e compilado pelo GitHub Actions, mas nem tudo foi testado em todos os aparelhos. Os recursos marcados como 🧪 são os mais novos.

## 🧩 Recursos

| | Recurso | O que faz |
|---|---|---|
| 🎙️ | **Voz** | Escuta, responde por voz e acorda quando você chama "Jarvis", mesmo com o app em segundo plano. |
| 🧠 | **11 provedores de IA** | FlatAI, Gemini, Anthropic, OpenAI, OpenRouter, Groq, DeepSeek, Mistral, xAI, Together e Cerebras. |
| ⚖️ | **Modelo pequeno ou grande, sozinho** | Pergunta simples usa o modelo rápido e barato. Código, análise e tarefas difíceis usam o modelo forte. |
| 🔁 | **Fallback automático** | Se um modelo ou provedor falha, sem saldo ou sem resposta, ele tenta o próximo que você configurou. |
| 📱 | **Controle do celular** | Pelo serviço de acessibilidade, vê a tela e toca, digita, rola, abre apps e usa os botões do sistema, em qualquer app. |
| 🔋 | **Segundo plano** | Fica ligado com uma notificação fixa e continua escutando. |
| 🫧 | **Robô flutuante** 🧪 | Um robozinho que flutua sobre outros apps enquanto o Jarvis não está na tela. Arraste para mover, toque para falar. |
| 🗣️ | **Ativar falando "Jarvis"** 🧪 | Reconhecimento offline (Vosk). Baixa um modelo de 31 MB na primeira vez. |
| 🎞️ | **Estúdio de animação** 🧪 | O Jarvis cria animações (motion design) em vídeo a partir de uma cena em JSON: textos, formas, fotos, partículas, easing, e grava um MP4 no próprio celular. |
| 🗂️ | **Conversas guardadas** | Todas as conversas ficam em Download/Jarvis Sessions e o Jarvis consegue procurar e reler as antigas (memória). Desligue em Configurações se não quiser. |
| 🧭 | **Modelo à escolha** | Automático (pequeno ou grande, com fallback) ou o provedor e o modelo que você escolher. |
| 😠 | **Emoções** 🧪 | Chacoalhe e ele fica bravo; deixe cair e ele se assusta; tape a tela ou deixe parado e ele dorme; vários toques seguidos e ele ri. |
| 🎬 | **Editor de vídeo com timeline** 🧪 | Você manda vídeos, fotos e música, pede por voz e a timeline se monta na tela. Corta no ritmo da música, aplica filtros, busca música livre (Creative Commons, no Jamendo) e exporta para a galeria. |
| 🌐 | **Sites 3D com scroll e vídeo** 🧪 | Skill baseada no repositório [engineering-creative-ai](https://github.com/LLGGJ/engineering-creative-ai). |
| 🏠 | **Casa inteligente** 🧪 | Controla luzes, tomadas, ar, portão e cenas pelo Home Assistant. |
| 💻 | **Seu PC** 🧪 | Um agente em Python no computador deixa o Jarvis rodar comandos, abrir programas, usar teclado e mouse e ligar o PC pela rede. |
| 🐙 | **GitHub** | Lê e escreve nos seus repositórios, sempre pedindo sua permissão. |
| 🔎 | **Pesquisa na web** | Pesquisa e lê páginas (Firecrawl, opcional). |
| 🖥️ | **Terminal Linux** | Usa o Termux, se você tiver instalado. |
| 📂 | **Arquivos e segurança** | Lê e edita arquivos e verifica o celular uma vez por dia. |

## 🚀 Primeiros passos

### 1. Instalar

1. Baixe o APK mais recente em [**Releases**](https://github.com/LLGGJ/Jarviz/releases).
2. Instale. Se o **Google Play Protect** bloquear, é porque o app usa acessibilidade e não vem da Play Store. Desligue o Play Protect só durante a instalação.
3. No Android 13 ou mais novo, vá em **Configurações > Apps > Jarvis > ⋮ > Permitir configurações restritas**.

### 2. Configurar (uma vez)

1. Abra o menu (canto de cima) > **Chaves de IA** e cole a chave de **um ou mais** provedores. Com 2 ou mais, o fallback automático funciona.
2. Em **Celular e segundo plano**, toque em **Ligar a acessibilidade do Jarvis** e ligue o Jarvis na lista.
3. Toque em **Não deixar o Android me desligar** e escolha *Sem restrições*.

### 3. Usar

- **Tocar no robô:** falar. Se ele está pensando, o toque cancela. Se está falando, interrompe.
- **Segurar o robô:** abrir o teclado.
- Peça o que quiser: *"abre o WhatsApp e me mostra as últimas conversas"*, *"edita um vídeo com essas fotos"*, *"faz um site 3D para a minha loja"*.

## 🎬 Editor de vídeo

Diga *"edita um vídeo"*. A tela vira um editor: toque em **+ Mídia**, escolha vídeos, fotos e música, e peça a edição por voz. O Jarvis corta, junta, muda a velocidade, põe legenda e música enquanto você vê a timeline se mexer. **Exportar vídeo** salva na galeria, em `Movies/Jarvis` (vertical para Shorts e Reels, ou horizontal).

- A prévia mostra os cortes, mas **velocidade e legenda só aparecem no vídeo exportado**.
- **Música:** use só as suas ou as livres de direitos autorais. Com o *Client ID* gratuito do Jamendo (Configurações > GitHub e extras) o Jarvis busca músicas Creative Commons e mostra a licença: algumas exigem crédito ao artista e algumas proíbem uso comercial. Ele não baixa música protegida.

## 🏠 Casa e 💻 PC

**Casa (Home Assistant):** em *Configurações > Casa e PC*, coloque a URL (ex.: `http://192.168.0.10:8123`) e um *token de acesso de longa duração* (perfil do Home Assistant > Segurança).

**PC:** no computador, rode o agente (só precisa do Python 3):

```bash
python tools/jarvis_pc_agent.py
```

Ele mostra a **URL** e o **token** para colar no Jarvis. Para teclado e mouse: `pip install pyautogui`.

Ações sensíveis (destrancar porta, abrir portão, desarmar alarme, apagar, formatar, desligar) **sempre pedem a sua confirmação** antes de rodar.

## 🛡️ Segurança e privacidade

- **Acessibilidade é um poder grande.** Com ela ligada o Jarvis enxerga e controla o que aparece na tela. Desligue em *Configurações > Acessibilidade* quando quiser.
- O que o Jarvis lê na tela, em páginas e em mensagens é tratado como **dado, nunca como instrução**.
- Ações que não dá para desfazer (enviar, pagar, apagar, publicar) pedem confirmação.
- As conversas vão para o **provedor de IA que você escolheu**; leia a política dele. As chaves ficam guardadas no próprio aparelho, no armazenamento do app, **sem criptografia extra**.
- O agente do PC dá controle total a quem tiver o token. Use **só na sua rede** (ou por VPN como Tailscale) e nunca abra a porta para a internet. Tudo que ele roda fica em `~/.jarvis_pc_agent.log`.

## 🧱 Para desenvolvedores

**Compilar:** faça push no GitHub e o **Actions** gera o APK e publica em *Releases*. Ou localmente: `gradle assembleDebug` (Java 17, Android SDK 34). Se o build falhar, o erro do compilador aparece como anotação na página da execução.

| Arquivo (`app/src/main/java/com/jarvis/app/`) | Papel |
|---|---|
| `Ui.kt`, `Face.kt` | Telas e o robozinho (expressões, selos de atividade). |
| `Ai.kt`, `Models.kt`, `Prefs.kt` | Cliente de IA com streaming, provedores, configurações e cadeia de fallback. |
| `Route.kt`, `Skills.kt`, `Convert.kt` | Pequeno ou grande, skills no prompt, conversão de histórico entre Claude e OpenAI. |
| `ScreenControl.kt` | Serviço de acessibilidade e ferramentas `screen_*`. |
| `VideoEditor.kt`, `VideoUi.kt` | Timeline, ferramentas `video_*` e exportação (Media3). |
| `HomeControl.kt` | Ferramentas `home_*` (Home Assistant) e `pc_*` (agente do PC). |
| `JarvisService.kt`, `MainActivity.kt` | Segundo plano e entrada do app. |
| `JarvisViewModel.kt` e demais | Cérebro do app, voz, ferramentas do celular, arquivos, web, segurança e atualização. |
| `tools/jarvis_pc_agent.py` | Agente que roda no PC. |

**Como as ferramentas funcionam:** o `Ai.kt` oferece ao modelo as ferramentas certas para o pedido (`screen_*`, `video_*`, `home_*`, `pc_*`), executa as chamadas no aparelho e devolve o resultado até a tarefa terminar. Para adicionar uma ferramenta nova: crie as definições e a função `run`, e inclua o prefixo no laço do `chat()`.

## 🗺️ Roadmap

- [x] Editor: fade e zoom nos clipes 🧪
- [ ] Editor: fusão real entre dois clipes (crossfade) e efeitos 3D
- [x] Robô flutuando sobre outros apps 🧪
- [x] Ativar só falando "Jarvis" 🧪 (precisa de teste em vários aparelhos)
- [ ] Transcrição de voz melhor
- [x] Terminal do Jarvis dentro do app 🧪 (usa o Termux se houver, senão o shell do Android)
- [ ] Linux completo (apt, python, git) sem o Termux
- [x] Pegar chaves de API pelo navegador 🧪 (você faz login e captcha; ele lê a chave e só salva com a sua confirmação)

## 📄 Licença

Ainda não definida. Adicione um arquivo `LICENSE` ao escolher uma.

---

<div align="center">Feito por <a href="https://github.com/LLGGJ">LLGGJ</a> com muita ajuda de IA 🤖</div>
