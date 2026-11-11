package com.jarvis.app

const val CUSTOM = "Personalizado"

/** Modelos conhecidos da FlatAI. O app descobre os do seu plano sozinho (botão "Atualizar" ou pelos erros 404). */
val DEFAULT_MODELS = listOf(
    "claude-sonnet-5",
    "z-ai/glm-5.3",
    "deepseek-ai/deepseek-v4.1-flash",
    "claude-opus-5-5",
    "claude-fable-5.1",
    "gpt-6-astra",
)

/** Claude fala o protocolo Anthropic; o resto fala OpenAI. */
fun apiFor(id: String) = if (id.startsWith("claude")) "anthropic" else "openai"
fun baseFor(api: String) = if (api == "anthropic") "https://api.flatai.pro" else "https://api.flatai.pro/v1"
