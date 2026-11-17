package com.jarvis.app

import android.content.Context
import org.json.JSONObject

data class Config(
    val apiKey: String = "",
    val baseUrl: String = "https://api.flatai.pro",
    val model: String = "claude-sonnet-5",
    val effort: String = "medium",
    val ghToken: String = "",
    val speak: Boolean = true,
    val clap: Boolean = true,
    val preset: String = "claude-sonnet-5",
    val api: String = "anthropic",
    val models: String = "",
    val repo: String = "LLGGJ/Jarviz",
    val theme: String = "hole",
    val firecrawl: String = "",
    val autoScan: Boolean = true,
    val provider: String = "flat",
    val geminiKey: String = "",
    val geminiModels: String = "",
    val fast: Boolean = true,
    val clapSens: String = "mid",
    val voiceName: String = "",
    val voiceRate: String = "1.0",
    val voiceViz: String = "bars",
    /** Chaves dos outros provedores, em JSON {"openai":"sk-...","groq":"..."}. FlatAI e Gemini continuam em apiKey/geminiKey. */
    val keys: String = "",
    /** Listas de modelos buscadas dos outros provedores, em JSON {"openai":"gpt-4o,gpt-4.1"}. */
    val modelLists: String = "",
    /** Se um modelo/provedor não responder, tenta o próximo configurado. */
    val fallback: Boolean = true,
    /** Escolhe sozinho entre modelo pequeno (coisa simples) e grande (coisa difícil). */
    val auto: Boolean = true,
    /** Deixa o Jarvis tocar, digitar e rolar a tela (precisa do serviço de acessibilidade ligado). */
    val screen: Boolean = true,
    /** Fica ligado em segundo plano (serviço com notificação fixa). */
    val background: Boolean = true,
) {
    private fun jm(s: String): JSONObject = try { JSONObject(s.ifBlank { "{}" }) } catch (e: Exception) { JSONObject() }

    fun keyOf(p: String): String {
        val k = jm(keys).optString(p)
        if (k.isNotBlank()) return k
        return when (p) { "gemini" -> geminiKey; "flat" -> apiKey; else -> "" }
    }

    fun withKey(p: String, v: String): Config {
        val c = copy(keys = jm(keys).put(p, v).toString())
        return when (p) { "gemini" -> c.copy(geminiKey = v); "flat" -> c.copy(apiKey = v); else -> c }
    }

    fun key(): String = keyOf(provider)

    /** Modelos de um provedor: a lista buscada da chave do usuário, ou a lista padrão. */
    fun modelsOf(p: String): List<String> {
        val raw = when (p) { "flat" -> models; "gemini" -> geminiModels; else -> jm(modelLists).optString(p) }
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { defaultModelsFor(p) }
    }

    fun withModels(p: String, ids: List<String>): Config {
        val csv = ids.joinToString(",")
        return when (p) {
            "flat" -> copy(models = csv)
            "gemini" -> copy(geminiModels = csv)
            else -> copy(modelLists = jm(modelLists).put(p, csv).toString())
        }
    }

    /** Troca de provedor: escolhe o primeiro modelo dele e ajusta protocolo e URL. */
    fun switchedTo(p: String): Config {
        val m = modelsOf(p).first()
        val a = apiFor(m, p)
        return copy(provider = p, model = m, preset = m, api = a, baseUrl = baseFor(a, p))
    }

    /** Provedor que vai na frente: o escolhido, ou o primeiro que tenha chave. */
    fun baseProvider(): String = if (keyOf(provider).isNotBlank()) provider else (configured().firstOrNull() ?: provider)

    /**
     * Ordem de tentativa com roteamento automático: modelo pequeno ou grande do provedor principal,
     * o outro tamanho dele e depois o mesmo tamanho dos outros provedores com chave.
     */
    fun routeChain(heavy: Boolean): List<Config> {
        if (!auto) return fallbackChain()
        fun mk(p: String, m: String): Config {
            val a = apiFor(m, p)
            return copy(provider = p, model = m, preset = m, api = a, baseUrl = baseFor(a, p))
        }
        val bp = baseProvider()
        val first = tierModel(bp, heavy)
        val out = mutableListOf(mk(bp, first))
        if (!fallback) return out
        val other = tierModel(bp, !heavy)
        if (other != first) out.add(mk(bp, other))
        configured().filter { it != bp }.forEach { out.add(mk(it, tierModel(it, heavy))) }
        return out.take(8)
    }

    /** Provedores que têm chave cadastrada. */
    fun configured(): List<String> = PROVIDER_DEFS.map { it.id }.filter { keyOf(it).isNotBlank() }

    /**
     * Ordem de tentativa: o modelo escolhido, mais 2 outros modelos do mesmo provedor,
     * depois o primeiro modelo de cada outro provedor que tenha chave.
     */
    fun fallbackChain(): List<Config> {
        val out = mutableListOf(this)
        if (!fallback) return out
        fun mk(p: String, m: String): Config {
            val a = apiFor(m, p)
            return copy(provider = p, model = m, preset = m, api = a, baseUrl = baseFor(a, p))
        }
        modelsOf(provider).filter { it != model }.take(2).forEach { out.add(mk(provider, it)) }
        PROVIDER_DEFS.forEach { d ->
            if (d.id != provider && keyOf(d.id).isNotBlank()) modelsOf(d.id).firstOrNull()?.let { out.add(mk(d.id, it)) }
        }
        return out.take(8)
    }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("jarvis2", Context.MODE_PRIVATE)
    private val d = Config()
    fun load() = Config(
        apiKey = sp.getString("apiKey", d.apiKey)!!,
        baseUrl = sp.getString("baseUrl", d.baseUrl)!!,
        model = sp.getString("model", d.model)!!,
        effort = sp.getString("effort", d.effort)!!,
        ghToken = sp.getString("ghToken", d.ghToken)!!,
        speak = sp.getBoolean("speak", d.speak),
        clap = sp.getBoolean("clap", d.clap),
        preset = sp.getString("preset", d.preset)!!,
        api = sp.getString("api", d.api)!!,
        models = sp.getString("models", d.models)!!,
        repo = sp.getString("repo", d.repo)!!,
        theme = sp.getString("theme2", d.theme)!!,
        firecrawl = sp.getString("firecrawl", d.firecrawl)!!,
        autoScan = sp.getBoolean("autoScan", d.autoScan),
        provider = sp.getString("provider", d.provider)!!,
        geminiKey = sp.getString("geminiKey", d.geminiKey)!!,
        geminiModels = sp.getString("geminiModels", d.geminiModels)!!,
        fast = sp.getBoolean("fast", d.fast),
        clapSens = sp.getString("clapSens", d.clapSens)!!,
        voiceName = sp.getString("voiceName", d.voiceName)!!,
        voiceRate = sp.getString("voiceRate", d.voiceRate)!!,
        voiceViz = sp.getString("voiceViz", d.voiceViz)!!,
        keys = sp.getString("keys", d.keys)!!,
        modelLists = sp.getString("modelLists", d.modelLists)!!,
        fallback = sp.getBoolean("fallback", d.fallback),
        auto = sp.getBoolean("auto", d.auto),
        screen = sp.getBoolean("screen", d.screen),
        background = sp.getBoolean("background", d.background),
    )
    fun lastScan() = sp.getLong("lastScan", 0L)
    fun setLastScan(t: Long) = sp.edit().putLong("lastScan", t).apply()
    fun save(c: Config) = sp.edit()
        .putString("apiKey", c.apiKey).putString("baseUrl", c.baseUrl)
        .putString("model", c.model).putString("effort", c.effort)
        .putString("ghToken", c.ghToken).putBoolean("speak", c.speak)
        .putBoolean("clap", c.clap).putString("preset", c.preset)
        .putString("api", c.api).putString("models", c.models)
        .putString("repo", c.repo).putString("theme2", c.theme)
        .putString("firecrawl", c.firecrawl).putBoolean("autoScan", c.autoScan)
        .putString("provider", c.provider).putString("geminiKey", c.geminiKey).putString("geminiModels", c.geminiModels)
        .putBoolean("fast", c.fast).putString("clapSens", c.clapSens)
        .putString("voiceName", c.voiceName).putString("voiceRate", c.voiceRate).putString("voiceViz", c.voiceViz)
        .putString("keys", c.keys).putString("modelLists", c.modelLists).putBoolean("fallback", c.fallback)
        .putBoolean("auto", c.auto).putBoolean("screen", c.screen).putBoolean("background", c.background).apply()
}
