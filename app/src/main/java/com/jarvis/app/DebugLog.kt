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

/** Guarda o último travamento do app para aparecer em Configurações > Diagnóstico. */
object CrashLog {
    fun install(ctx: android.content.Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                java.io.File(ctx.filesDir, "crash.txt").writeText(
                    SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date()) + " em " + t.name + "\n" + android.util.Log.getStackTraceString(e).take(3000)
                )
            } catch (x: Exception) { }
            prev?.uncaughtException(t, e)
        }
    }

    fun last(ctx: android.content.Context): String {
        val f = java.io.File(ctx.filesDir, "crash.txt")
        return if (f.exists()) "ÚLTIMO TRAVAMENTO:\n" + f.readText().take(1500) + "\n\n" else ""
    }
}
