package com.jarvis.app

import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** Ações sobre o próprio app que a IA pode pedir. Hoje: salvar uma chave de API (só com confirmação do usuário). */
object AppActions {
    class KeyReq(val provider: String, val key: String)

    /** Chaves aguardando para entrar na configuração (a tela principal aplica). */
    val pendingKeys = mutableStateListOf<KeyReq>()

    private val WANTED = Regex("(?i)(chave|api key|apikey|api-key|token de api|criar.*api|pegar.*api)")
    fun wanted(userText: String): Boolean = WANTED.containsMatchIn(userText)

    fun defs(): List<JSONObject> = listOf(
        JSONObject().put("type", "function").put(
            "function", JSONObject().put("name", "app_set_key")
                .put("description", "Salva a chave de API de um provedor no Jarvis. Só chame com confirmed=true DEPOIS de mostrar ao usuário o provedor e os 4 últimos caracteres e ele dizer sim. provider: " +
                    PROVIDER_DEFS.joinToString(", ") { it.id } + ".")
                .put("parameters", JSONObject().put("type", "object").put("properties", JSONObject()
                    .put("provider", JSONObject().put("type", "string"))
                    .put("key", JSONObject().put("type", "string"))
                    .put("confirmed", JSONObject().put("type", "boolean")))
                    .put("required", JSONArray(listOf("provider", "key"))))
        )
    )

    fun run(name: String, a: JSONObject): String {
        if (name != "app_set_key") return "Ferramenta desconhecida: $name"
        val provider = a.optString("provider").lowercase()
        val key = a.optString("key").trim()
        if (PROVIDER_DEFS.none { it.id == provider }) return "Provedor inválido. Use um destes: " + PROVIDER_DEFS.joinToString(", ") { it.id }
        if (key.length < 16 || key.contains(' ')) return "Isso não parece uma chave de API. Confira o texto lido da tela."
        if (!a.optBoolean("confirmed", false))
            return "Pergunte ao usuário se pode salvar a chave de ${providerLabel(provider)} (termina em ${key.takeLast(4)}) e, só se ele disser sim, chame de novo com confirmed=true."
        pendingKeys.add(KeyReq(provider, key))
        return "Chave de ${providerLabel(provider)} salva (termina em ${key.takeLast(4)})."
    }
}
