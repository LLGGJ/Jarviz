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

/** Um provedor de IA. base = URL base (OpenAI-compatível, exceto Anthropic e FlatAI/Claude). */
class ProviderDef(val id: String, val label: String, val base: String, val models: List<String>, val keyUrl: String)

/**
 * Provedores disponíveis. Os modelos abaixo são só o ponto de partida: o botão "Atualizar" nas configurações
 * busca a lista real da sua chave em cada provedor.
 */
val PROVIDER_DEFS = listOf(
    ProviderDef("flat", "FlatAI", "https://api.flatai.pro", DEFAULT_MODELS, "https://flatai.pro"),
    ProviderDef("gemini", "Google Gemini (AI Studio)", GEMINI_BASE, DEFAULT_GEMINI, "https://aistudio.google.com/apikey"),
    ProviderDef("anthropic", "Anthropic (Claude)", "https://api.anthropic.com",
        listOf("claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-5-5"), "https://console.anthropic.com/settings/keys"),
    ProviderDef("openai", "OpenAI", "https://api.openai.com/v1",
        listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4o", "gpt-4o-mini"), "https://platform.openai.com/api-keys"),
    ProviderDef("openrouter", "OpenRouter", "https://openrouter.ai/api/v1",
        listOf("openrouter/auto", "meta-llama/llama-3.3-70b-instruct", "deepseek/deepseek-chat"), "https://openrouter.ai/keys"),
    ProviderDef("groq", "Groq", "https://api.groq.com/openai/v1",
        listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"), "https://console.groq.com/keys"),
    ProviderDef("deepseek", "DeepSeek", "https://api.deepseek.com/v1",
        listOf("deepseek-chat", "deepseek-reasoner"), "https://platform.deepseek.com/api_keys"),
    ProviderDef("mistral", "Mistral", "https://api.mistral.ai/v1",
        listOf("mistral-large-latest", "mistral-small-latest"), "https://console.mistral.ai/api-keys"),
    ProviderDef("xai", "xAI (Grok)", "https://api.x.ai/v1",
        listOf("grok-3", "grok-3-mini"), "https://console.x.ai"),
    ProviderDef("together", "Together AI", "https://api.together.xyz/v1",
        listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3"), "https://api.together.ai/settings/api-keys"),
    ProviderDef("cerebras", "Cerebras", "https://api.cerebras.ai/v1",
        listOf("llama-3.3-70b", "llama3.1-8b"), "https://cloud.cerebras.ai"),
)

val PROVIDERS = PROVIDER_DEFS.map { it.id to it.label }

fun providerDef(id: String): ProviderDef = PROVIDER_DEFS.firstOrNull { it.id == id } ?: PROVIDER_DEFS.first()
fun providerLabel(id: String): String = PROVIDER_DEFS.firstOrNull { it.id == id }?.label ?: id
fun defaultModelsFor(id: String): List<String> = PROVIDER_DEFS.firstOrNull { it.id == id }?.models ?: DEFAULT_MODELS

/** FlatAI: Claude fala Anthropic, o resto OpenAI. Anthropic direto fala Anthropic. Todos os outros usam o protocolo OpenAI. */
fun apiFor(id: String, provider: String = "flat"): String = when (provider) {
    "flat" -> if (id.startsWith("claude")) "anthropic" else "openai"
    "anthropic" -> "anthropic"
    else -> "openai"
}

fun baseFor(api: String, provider: String = "flat"): String = when (provider) {
    "flat" -> if (api == "anthropic") "https://api.flatai.pro" else "https://api.flatai.pro/v1"
    else -> PROVIDER_DEFS.firstOrNull { it.id == provider }?.base ?: "https://api.flatai.pro/v1"
}
