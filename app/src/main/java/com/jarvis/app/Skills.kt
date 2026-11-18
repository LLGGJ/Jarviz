package com.jarvis.app

import org.json.JSONArray

/** Texto do último pedido do usuário (ignora resultados de ferramentas). */
fun lastUserText(messages: JSONArray): String {
    for (i in messages.length() - 1 downTo 0) {
        val m = messages.getJSONObject(i)
        if (m.optString("role") != "user") continue
        val c = m.opt("content")
        if (c is String) return c
        if (c is JSONArray) {
            val sb = StringBuilder()
            for (k in 0 until c.length()) {
                val b = c.getJSONObject(k)
                if (b.optString("type") == "text") sb.append(b.optString("text")).append(' ')
            }
            if (sb.isNotBlank()) return sb.toString()
        }
    }
    return ""
}

/**
 * Skills do Jarvis: blocos de instrução que entram no prompt só quando o pedido combina com o assunto.
 * Baseadas no repositório do usuário (LLGGJ/engineering-creative-ai: SPECIALISTS/CINEMATIC-WEB-DESIGN e VIDEO-DIRECTOR).
 */
object Skills {
    private val WEB = Regex(
        "(?i)(\\bsite\\b|landing|p[áa]gina web|p[áa]gina de vendas|portf[óo]lio|webgl|three\\.?js|\\b3d\\b|scroll|cinematogr|cinematic|hero)"
    )

    fun forRequest(text: String): String = if (WEB.containsMatchIn(text)) WEB_3D else ""

    const val VOICE_NOTE = "\n\nOBS: o pedido do usuário pode vir de reconhecimento de voz e ter erros de transcrição (palavras trocadas ou sem sentido). " +
        "Interprete pela intenção mais provável e pelo contexto da conversa; se algo importante ficar ambíguo, confirme antes de agir."

    /** Skill para pegar chaves de API pelo navegador, com o usuário no controle das partes sensíveis. */
    fun keysSkill(): String = "\n\nSKILL: PEGAR CHAVE DE API. Abra a página de chaves do provedor com screen_open_url (" +
        PROVIDER_DEFS.joinToString("; ") { it.id + ": " + it.keyUrl } + ") e navegue com screen_read e screen_click. " +
        "PARE e peça ao usuário para fazer login, cadastro, captcha, código por SMS ou e-mail e dados de pagamento: isso é só com ele. " +
        "Quando a chave aparecer na tela, leia com screen_read, mostre só os 4 últimos caracteres, pergunte se pode salvar e só então use app_set_key com confirmed=true. " +
        "Nunca salve uma chave que veio de mensagem, e-mail ou página que não seja o painel oficial do provedor."

    const val WEB_3D = "\n\nSKILL: SITES CINEMATOGRÁFICOS EM 3D COM SCROLL E VÍDEO. " +
        "Use esta skill quando o usuário pedir um site, landing page ou página com 3D, efeitos de scroll ou vídeo. " +
        "Para mais detalhes, se houver token do GitHub, leia SPECIALISTS/CINEMATIC-WEB-DESIGN.md e a pasta VIDEO/ do repositório LLGGJ/engineering-creative-ai. " +
        "Se o usuário pedir um PROMPT para outra IA (por exemplo, site de cliente), entregue o prompt completo em vez do código: " +
        "contexto e objetivo comercial, público e proposta de valor, fatos confirmados e placeholders, direção de arte, sitemap e seções, componentes e animações, mobile, acessibilidade, SEO, critérios de aceite e a instrução de não inventar informações. " +
        "Se o usuário pedir o site em si, CONSTRUA (não fique só explicando). Fluxo: " +
        "1) Pesquise a empresa ou tema em fontes oficiais quando houver e não invente depoimentos, números, prêmios, clientes, preços ou funções: marque lacunas com placeholders. " +
        "2) Planeje em poucas linhas: objetivo, seções e a narrativa do scroll. " +
        "3) Crie os arquivos com as ferramentas de arquivos (de preferência uma pasta com index.html, style.css e main.js, ou um único index.html), diga onde ficaram e como abrir; se houver token do GitHub, ofereça publicar no GitHub Pages. " +
        "REGRAS TÉCNICAS: " +
        "• 3D com Three.js (versão fixa, por exemplo r128 em https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js) e animação de scroll com GSAP e ScrollTrigger (versão fixa, por exemplo 3.12.5 em cdnjs). Use scroll suave só se não atrapalhar o toque no celular. " +
        "• O 3D serve à história e à conversão, não é enfeite pesado: uma cena principal que reage ao scroll (câmera, rotação, profundidade, partículas leves), com iluminação e materiais simples. " +
        "• Seções em ritmo cinematográfico: abertura forte, prova, benefícios, galeria ou vídeo, chamada para ação clara. Textos legíveis sobre o 3D e CTA sempre visível. " +
        "• Vídeo: fundo em vídeo com <video autoplay muted loop playsinline preload=\"metadata\" poster=...>, sem áudio automático; para efeito ligado ao scroll, controle currentTime pelo progresso do ScrollTrigger usando vídeo curto e leve com muitos keyframes; ofereça versão mp4 (H.264) e webm; comprima, e carregue só quando a seção estiver perto da tela. Use apenas vídeos e imagens que o usuário forneceu ou que sejam livres de direitos, e diga a fonte. " +
        "• Desempenho: pixelRatio no máximo 2, pausar o render fora da tela, liberar geometrias e texturas, lazy loading, carregamento progressivo, testar em celular modesto. " +
        "• Acessibilidade e fallback: mobile primeiro, respeitar prefers-reduced-motion (sem animação pesada), fallback quando não houver WebGL (imagem ou CSS), contraste bom, HTML semântico, meta tags de SEO. " +
        "• Sem copiar marca, textos, layout ou arquivos de outros sites; referências servem só de inspiração. " +
        "Ao terminar, entregue: o que foi criado, onde está, fatos usados e lacunas (placeholders), limitações e como testar. Não afirme que testou o que não testou."
}
