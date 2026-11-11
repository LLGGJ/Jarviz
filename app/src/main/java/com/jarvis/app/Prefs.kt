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
    val preset: String = "Claude Sonnet 5",
    val api: String = "anthropic",
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
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
    )
    fun save(c: Config) = sp.edit()
        .putString("apiKey", c.apiKey).putString("baseUrl", c.baseUrl)
        .putString("model", c.model).putString("effort", c.effort)
        .putString("ghToken", c.ghToken).putBoolean("speak", c.speak)
        .putBoolean("clap", c.clap).putString("preset", c.preset)
        .putString("api", c.api).apply()
}
