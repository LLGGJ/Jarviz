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
    val theme: String = "orb",
    val firecrawl: String = "",
    val autoScan: Boolean = true,
    val provider: String = "flat",
    val geminiKey: String = "",
    val geminiModels: String = "",
) {
    fun key() = if (provider == "gemini") geminiKey else apiKey
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("jarvis2", Context.MODE_PRIVATE)
    private val d = Config()
    fun load() = Config(
        sp.getString("apiKey", d.apiKey)!!,
        sp.getString("baseUrl", d.baseUrl)!!,
        sp.getString("model", d.model)!!,
        sp.getString("effort", d.effort)!!,
        sp.getString("ghToken", d.ghToken)!!,
        sp.getBoolean("speak", d.speak),
        sp.getBoolean("clap", d.clap),
        sp.getString("preset", d.preset)!!,
        sp.getString("api", d.api)!!,
        sp.getString("models", d.models)!!,
        sp.getString("repo", d.repo)!!,
        sp.getString("theme", d.theme)!!,
        sp.getString("firecrawl", d.firecrawl)!!,
        sp.getBoolean("autoScan", d.autoScan),
        sp.getString("provider", d.provider)!!,
        sp.getString("geminiKey", d.geminiKey)!!,
        sp.getString("geminiModels", d.geminiModels)!!,
    )
    fun lastScan() = sp.getLong("lastScan", 0L)
    fun setLastScan(t: Long) = sp.edit().putLong("lastScan", t).apply()
    fun save(c: Config) = sp.edit()
        .putString("apiKey", c.apiKey).putString("baseUrl", c.baseUrl)
        .putString("model", c.model).putString("effort", c.effort)
        .putString("ghToken", c.ghToken).putBoolean("speak", c.speak)
        .putBoolean("clap", c.clap).putString("preset", c.preset)
        .putString("api", c.api).putString("models", c.models)
        .putString("repo", c.repo).putString("theme", c.theme)
        .putString("firecrawl", c.firecrawl).putBoolean("autoScan", c.autoScan)
        .putString("provider", c.provider).putString("geminiKey", c.geminiKey).putString("geminiModels", c.geminiModels).apply()
}
