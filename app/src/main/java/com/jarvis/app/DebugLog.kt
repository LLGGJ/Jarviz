package com.jarvis.app

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro do que a IA e as ferramentas fizeram. Aparece em Configurações > Diagnóstico e dá para copiar. */
object DebugLog {
    val lines = mutableStateListOf<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(s: String) {
        val line = fmt.format(Date()) + " " + s.replace('\n', ' ').take(260)
        synchronized(lines) {
            lines.add(line)
            while (lines.size > 80) lines.removeAt(0)
        }
    }
}
