package com.jarvis.app

import org.json.JSONArray

private val HEAVY = Regex(
    "(?i)(luz|\\bcasa\\b|computador|\\bpc\\b|tomada|timeline|clipe|landing|scroll|webgl|three|\\b3d\\b|anima|c[óo]digo|programa|script|fun[çc][ãa]o|\\bbug|erro|compil|github|reposit[óo]rio|analis|explic|detalh|compar|resum|traduz|escreva|\\bcrie\\b|desenvolv|calcul|planej|pesquis|relat[óo]rio|projeto|\\bsite\\b|\\bapp\\b|v[ií]deo|document|passo a passo|por que|porque|" +
        "tela|clique|toque|digite|abra |abrir|mande|envie|mensagem|instal|configur|whatsapp|instagram)"
)

/**
 * Decide se o pedido é "grande" (usa o modelo forte) ou "pequeno" (usa o modelo rápido).
 * Grande: texto longo, palavras de código/análise/ações no celular, imagem anexada, conversa longa ou cadeia de ferramentas.
 */
fun isHeavy(messages: JSONArray, tools: JSONArray): Boolean {
    var text = ""
    var image = false
    var toolMsgs = 0
    for (i in messages.length() - 1 downTo 0) {
        val m = messages.getJSONObject(i)
        val role = m.optString("role")
        if (role == "tool") { toolMsgs++; continue }
        if (role != "user") continue
        val c = m.opt("content")
        if (c is String) { text = c; break }
        if (c is JSONArray) {
            val sb = StringBuilder()
            var onlyResults = c.length() > 0
            for (k in 0 until c.length()) {
                val b = c.getJSONObject(k)
                when (b.optString("type")) {
                    "text" -> { sb.append(b.optString("text")).append(' '); onlyResults = false }
                    "image", "image_url", "document" -> { image = true; onlyResults = false }
                    "tool_result" -> toolMsgs++
                    else -> onlyResults = false
                }
            }
            if (!onlyResults) { text = sb.toString(); break }
        }
    }
    return image || text.length > 220 || HEAVY.containsMatchIn(text) || toolMsgs >= 2 || messages.length() > 14
}
