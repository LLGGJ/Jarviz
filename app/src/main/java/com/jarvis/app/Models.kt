package com.jarvis.app

const val CUSTOM = "Personalizado"
const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/openai"

/** Modelos conhecidos da FlatAI. O app descobre os do seu plano sozinho (botão "Atualizar" ou pelos erros 404). */
val DEFAULT_MODELS = listOf(
    "claude-sonnet-5",
    "z-ai/glm-5.3",
    "deepseek-ai/deepseek-v4.1-flash",
    "claude-opus-5-5",
    "claude-fable-5.1",
    "gpt-6-astra",
)

/** Lista inicial do Gemini; o botão "Atualizar modelos" traz a lista exata da sua chave do AI Studio. */
val DEFAULT_GEMINI = listOf(
    "gemini-3.7-flash",
    "gemini-3.6-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite",
    "gemini-3.1-pro-preview",
    "gemini-3-flash-preview",
    "gemini-2.5-pro",
    "gemini-2.5-flash",
    "gemini-2.5-flash-lite",
)

val PROVIDERS = listOf("flat" to "FlatAI", "gemini" to "Google Gemini (AI Studio)")

/** FlatAI: Claude fala Anthropic, o resto OpenAI. Gemini usa o endpoint compatível com OpenAI do AI Studio. */
fun apiFor(id: String, provider: String = "flat") =
    if (provider == "gemini") "openai" else if (id.startsWith("claude")) "anthropic" else "openai"

fun baseFor(api: String, provider: String = "flat") =
    if (provider == "gemini") GEMINI_BASE else if (api == "anthropic") "https://api.flatai.pro" else "https://api.flatai.pro/v1"
