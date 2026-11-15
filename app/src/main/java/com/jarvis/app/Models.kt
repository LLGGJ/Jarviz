package com.jarvis.app

const val CUSTOM = "Personalizado"
const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/openai"
const val GROQ_BASE = "https://api.groq.com/openai/v1"
const val DEEPSEEK_BASE = "https://api.deepseek.com/v1"
const val OPENAI_BASE = "https://api.openai.com/v1"

val DEFAULT_MODELS = listOf(
    "claude-sonnet-5",
    "z-ai/glm-5.3",
    "deepseek-ai/deepseek-v4.1-flash",
    "claude-opus-5-5",
    "claude-fable-5.1",
    "gpt-6-astra",
)

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

val DEFAULT_GROQ = listOf(
    "llama-3.3-70b-versatile",
    "llama-3.1-8b-instant",
    "mixtral-8x7b-32768",
    "gemma2-9b-it",
)

val DEFAULT_DEEPSEEK = listOf(
    "deepseek-chat",
    "deepseek-reasoner",
)

val DEFAULT_OPENAI = listOf(
    "gpt-4o",
    "gpt-4o-mini",
    "o1-preview",
    "o3-mini",
)

val PROVIDERS = listOf(
    "flat" to "FlatAI",
    "gemini" to "Google Gemini",
    "groq" to "Groq Cloud",
    "deepseek" to "DeepSeek",
    "openai" to "OpenAI"
)

fun apiFor(id: String, provider: String = "flat") =
    if (provider == "gemini" || provider == "groq" || provider == "deepseek" || provider == "openai") "openai"
    else if (id.startsWith("claude")) "anthropic" else "openai"

fun baseFor(api: String, provider: String = "flat") = when (provider) {
    "gemini" -> GEMINI_BASE
    "groq" -> GROQ_BASE
    "deepseek" -> DEEPSEEK_BASE
    "openai" -> OPENAI_BASE
    else -> if (api == "anthropic") "https://api.flatai.pro" else "https://api.flatai.pro/v1"
}
