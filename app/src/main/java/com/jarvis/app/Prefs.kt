package com.jarvis.app

import android.content.Context

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
) {
    fun key() = if (provider == "gemini") geminiKey else apiKey
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
        .putString("voiceName", c.voiceName).putString("voiceRate", c.voiceRate).putString("voiceViz", c.voiceViz).apply()
}
