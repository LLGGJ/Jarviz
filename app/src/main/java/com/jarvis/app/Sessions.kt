package com.jarvis.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda todas as conversas em Download/Jarvis Sessions (um arquivo de texto por conversa) e deixa o Jarvis procurar
 * e reler conversas antigas (memória). Atenção: a pasta Downloads é visível para outros apps com acesso a arquivos.
 */
object Sessions {
    const val FOLDER = "Jarvis Sessions"
    private var uri: Uri? = null
    private var file: File? = null
    private val WANT = Regex("(?i)(lembra|lembrar|conversa|conversamos|falamos|antes|ontem|hist[óo]rico|j[áa] falei|sess[ãa]o|anterior|mem[óo]ria|semana passada)")

    fun wanted(userText: String): Boolean = WANT.containsMatchIn(userText)

    private class SFile(val name: String, val read: () -> String)

    private fun render(msgs: List<Msg>): String {
        val sb = StringBuilder("# Conversa com o Jarvis - ")
        sb.append(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())).append("\n\n")
        for (m in msgs) sb.append(if (m.role == "user") "**Você:** " else "**Jarvis:** ").append(m.text.trim()).append("\n\n")
        return sb.toString()
    }

    /** Salva (ou atualiza) o arquivo da conversa atual. Conversa vazia = a próxima começa um arquivo novo. */
    fun save(ctx: Context, msgs: List<Msg>) {
        if (msgs.isEmpty()) { uri = null; file = null; return }
        try {
            val text = render(msgs)
            val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(Date())
            if (Build.VERSION.SDK_INT >= 29) {
                if (uri == null) {
                    val v = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, "Conversa $stamp.txt")
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
                    }
                    uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                }
                uri?.let { u -> ctx.contentResolver.openOutputStream(u, "wt")?.use { it.write(text.toByteArray()) } }
            } else {
                if (file == null) {
                    val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
                    d.mkdirs()
                    file = File(d, "Conversa $stamp.txt")
                }
                file?.writeText(text)
            }
        } catch (e: Exception) {
            DebugLog.add("conversas: não consegui salvar (${e.message})")
        }
    }

    private fun files(ctx: Context): List<SFile> {
        val out = ArrayList<SFile>()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val proj = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
                ctx.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj, MediaStore.Downloads.RELATIVE_PATH + " LIKE ?",
                    arrayOf(Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "%"), MediaStore.Downloads.DATE_MODIFIED + " DESC"
                )?.use { c ->
                    while (c.moveToNext() && out.size < 60) {
                        val u = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0))
                        out.add(SFile(c.getString(1)) { ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() }.orEmpty() })
                    }
                }
            } else {
                val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
                d.listFiles()?.sortedByDescending { it.lastModified() }?.take(60)?.forEach { f -> out.add(SFile(f.name) { f.readText() }) }
            }
        } catch (e: Exception) { }
        return out
    }

    private fun t(name: String, desc: String, props: JSONObject, req: List<String>) = JSONObject().put("type", "function").put(
        "function", JSONObject().put("name", name).put("description", desc)
            .put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(req)))
    )

    fun defs(): List<JSONObject> = listOf(
        t("session_search", "Procura um texto nas conversas antigas guardadas em Download/Jarvis Sessions (memória do Jarvis). Devolve arquivo e trecho.",
            JSONObject().put("query", JSONObject().put("type", "string")), listOf("query")),
        t("session_read", "Lê uma conversa antiga pelo nome do arquivo (vazio = a mais recente).",
            JSONObject().put("name", JSONObject().put("type", "string")), emptyList()),
    )

    fun run(name: String, a: JSONObject): String {
        val c = VideoEditor.ctx ?: return "Sem contexto do app."
        return try {
            when (name) {
                "session_search" -> search(c, a.optString("query"))
                "session_read" -> readOne(c, a.optString("name"))
                else -> "Ferramenta desconhecida: $name"
            }
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }

    private fun search(c: Context, q: String): String {
        if (q.isBlank()) return "Diga o que procurar."
        val sb = StringBuilder()
        var hits = 0
        for (f in files(c)) {
            for (line in f.read().lineSequence()) {
                if (line.contains(q, ignoreCase = true)) {
                    sb.append(f.name).append(": ").append(line.take(220)).append('\n')
                    if (++hits >= 12) return sb.toString()
                }
            }
        }
        return if (hits == 0) "Não achei nada sobre \"$q\" nas conversas guardadas." else sb.toString()
    }

    private fun readOne(c: Context, name: String): String {
        val list = files(c)
        val f = (if (name.isBlank()) list.firstOrNull() else list.firstOrNull { it.name.contains(name, ignoreCase = true) }) ?: return "Conversa não encontrada."
        return f.name + "\n" + f.read().take(4000)
    }
}
