package com.jarvis.app

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Busca os modelos liberados na chave do provedor escolhido e guarda a lista (serve para qualquer provedor). */
fun JarvisViewModel.refreshProviderModels(onMsg: (String) -> Unit) {
    val c = cfg
    if (c.key().isBlank()) { onMsg("Cole a API Key deste provedor primeiro."); return }
    onMsg("Buscando...")
    viewModelScope.launch {
        val r = withContext(Dispatchers.IO) { runCatching { AiClient().listModels(c) } }
        r.onSuccess { ids ->
            if (ids.isEmpty()) onMsg("Nenhum modelo retornado")
            else {
                updateCfg(cfg.withModels(c.provider, ids.take(150)))
                onMsg("${ids.size} modelos disponíveis na sua chave")
            }
        }.onFailure { onMsg("Não consegui listar (${it.message}). Os modelos padrão continuam valendo.") }
    }
}
