package com.jarvis.app

const val CUSTOM = "Personalizado"

/** Cada modelo carrega sua URL base e protocolo: ao escolher no menu, tudo troca sozinho. */
data class ModelPreset(val name: String, val id: String, val baseUrl: String, val api: String)

private const val ANTHROPIC_BASE = "https://api.flatai.pro"     // Anthropic Messages (/v1/messages)
private const val OPENAI_BASE = "https://api.flatai.pro/v1"     // OpenAI (/chat/completions)

// Modelos listados em flatai.pro (Basic: Sonnet 5 e GLM 5.3 | Pro: + Opus 5.5 e Fable 5.1 | Max: + GPT-6 Astra)
val PRESETS: List<ModelPreset> = listOf(
    ModelPreset("Claude Sonnet 5", "claude-sonnet-5", ANTHROPIC_BASE, "anthropic"),
    ModelPreset("Claude Opus 5.5", "claude-opus-5-5", ANTHROPIC_BASE, "anthropic"),
    ModelPreset("Claude Fable 5.1", "claude-fable-5.1", ANTHROPIC_BASE, "anthropic"),
    ModelPreset("GPT-6 Astra", "gpt-6-astra", OPENAI_BASE, "openai"),
    ModelPreset("GLM 5.3", "glm-5.3", OPENAI_BASE, "openai"),
)
