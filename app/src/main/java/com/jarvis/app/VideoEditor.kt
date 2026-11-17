package com.jarvis.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

class VMedia(val id: Int, val uri: Uri, val name: String, val kind: String, val durMs: Long)

data class VClip(val media: VMedia, val startMs: Long, val endMs: Long, val speed: Float = 1f, val text: String = "") {
    val lenMs: Long get() = ((endMs - startMs) / speed).toLong()
}

/**
 * Editor de vídeo: o estado da timeline fica aqui (a tela observa e se mexe ao vivo) e as ferramentas video_* são
 * chamadas pela IA. A exportação usa o Media3 Transformer, que edita de verdade no celular.
 */
object VideoEditor {
    var open by mutableStateOf(false)
    val media = mutableStateListOf<VMedia>()
    val clips = mutableStateListOf<VClip>()
    var music by mutableStateOf<VMedia?>(null)
    var keepAudio by mutableStateOf(false)
    var selected by mutableIntStateOf(-1)
    var status by mutableStateOf("")
    @Volatile var exporting = false
    var ctx: Context? = null
    private var nextId = 0

    private val WANTED = Regex("(?i)(v[ií]deo|timeline|clipe|reels|shorts|montar um filme|editar foto|editar a foto)")
    fun wanted(userText: String): Boolean = open || media.isNotEmpty() || WANTED.containsMatchIn(userText)

    const val PROMPT = "\n\nSKILL: EDITOR DE VÍDEO (com timeline). Use as ferramentas video_* para editar vídeos e fotos que o usuário mandou. " +
        "A tela do editor se atualiza ao vivo enquanto você chama as ferramentas. Fluxo: " +
        "1) video_open abre o editor; 2) video_media lista o que o usuário já adicionou (cada item tem um número); se não houver nada, peça para ele tocar em \"+ Mídia\" no editor e escolher vídeos, fotos e música; " +
        "3) planeje a edição em poucas linhas (ritmo, quantos cortes, texto, música); 4) monte com video_add_clip (media, start_s, end_s, speed, text), ajuste com video_trim, video_move, video_remove, video_speed, video_text e adicione música com video_music; " +
        "5) confira com video_timeline; 6) exporte com video_export (vertical para Shorts/Reels/TikTok, horizontal para YouTube). O vídeo final é salvo na galeria, na pasta Movies/Jarvis. " +
        "Fotos viram clipes de poucos segundos (parâmetro seconds). Cortes rápidos de 1 a 3 segundos funcionam bem em vídeo curto; abra com o trecho mais forte. " +
        "MÚSICA: use somente músicas que o usuário forneceu ou que sejam livres de direitos autorais (por exemplo Pixabay Music, YouTube Audio Library ou Free Music Archive, conferindo a licença). " +
        "Nunca tente baixar música protegida nem tirar áudio do YouTube. Se o usuário quiser música livre, ajude a achar a biblioteca e abra a página; ele baixa e adiciona com \"+ Mídia\". " +
        "Seja honesto: a prévia não mostra velocidade nem texto, só o vídeo final exportado mostra. Não diga que exportou antes de video_export responder."

    private fun t(name: String, desc: String, props: JSONObject, req: List<String>) = JSONObject().put("type", "function").put(
        "function", JSONObject().put("name", name).put("description", desc)
            .put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(req)))
    )

    private fun p(vararg kv: Pair<String, String>): JSONObject {
        val o = JSONObject()
        kv.forEach { (k, ty) -> o.put(k, JSONObject().put("type", ty)) }
        return o
    }

    fun toolDefs(): List<JSONObject> = listOf(
        t("video_open", "Abre o editor de vídeo com a timeline na tela.", p(), emptyList()),
        t("video_media", "Lista as mídias (vídeos, fotos, músicas) que o usuário adicionou, com número e duração.", p(), emptyList()),
        t("video_add_clip", "Põe um clipe no fim da timeline. media = número da mídia; start_s e end_s em segundos (vídeo); seconds = duração de foto; speed = velocidade (0.25 a 4); text = legenda.",
            p("media" to "number", "start_s" to "number", "end_s" to "number", "seconds" to "number", "speed" to "number", "text" to "string"), listOf("media")),
        t("video_trim", "Muda o início e o fim de um clipe da timeline.", p("index" to "number", "start_s" to "number", "end_s" to "number"), listOf("index", "start_s", "end_s")),
        t("video_remove", "Tira um clipe da timeline.", p("index" to "number"), listOf("index")),
        t("video_move", "Move um clipe de uma posição para outra.", p("from" to "number", "to" to "number"), listOf("from", "to")),
        t("video_speed", "Muda a velocidade de um clipe (0.25 a 4).", p("index" to "number", "speed" to "number"), listOf("index", "speed")),
        t("video_text", "Põe ou troca a legenda de um clipe (texto vazio tira).", p("index" to "number", "text" to "string"), listOf("index", "text")),
        t("video_music", "Define a música de fundo (media = número de uma mídia de áudio). keep_audio=true mantém o som original dos clipes.", p("media" to "number", "keep_audio" to "boolean"), listOf("media")),
        t("video_clear", "Esvazia a timeline.", p(), emptyList()),
        t("video_timeline", "Descreve a timeline atual.", p(), emptyList()),
        t("video_export", "Exporta o vídeo final para a galeria. format: vertical ou horizontal.", p("format" to "string"), emptyList()),
    )

    fun run(name: String, a: JSONObject): String {
        return try {
            when (name) {
                "video_open" -> { open = true; "Editor aberto.\n" + listMedia() }
                "video_media" -> listMedia()
                "video_add_clip" -> addClip(a)
                "video_trim" -> trim(a)
                "video_remove" -> remove(a)
                "video_move" -> move(a)
                "video_speed" -> speed(a)
                "video_text" -> text(a)
                "video_music" -> setMusic(a)
                "video_clear" -> { clips.clear(); selected = -1; "Timeline vazia." }
                "video_timeline" -> describe()
                "video_export" -> export(a.optString("format", "vertical"))
                else -> "Ferramenta desconhecida: $name"
            }
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }

    private fun fmt(ms: Long): String = String.format(Locale.US, "%.1fs", ms / 1000.0)
    private fun secs(a: JSONObject, k: String): Long = (a.optDouble(k) * 1000).toLong()

    private fun listMedia(): String =
        if (media.isEmpty()) "Nenhuma mídia ainda. Peça ao usuário para tocar em \"+ Mídia\" no editor."
        else media.joinToString("\n") { "${it.id}: ${it.name} (${it.kind}${if (it.kind == "image") "" else ", " + fmt(it.durMs)})" }

    private fun describe(): String {
        if (clips.isEmpty()) return "Timeline vazia."
        val sb = StringBuilder()
        clips.forEachIndexed { i, c ->
            sb.append("$i: ${c.media.name} ${fmt(c.startMs)}-${fmt(c.endMs)} x${c.speed}")
            if (c.text.isNotBlank()) sb.append(" texto=\"${c.text}\"")
            sb.append('\n')
        }
        sb.append("Duração total: ${fmt(clips.sumOf { it.lenMs })}")
        music?.let { sb.append("\nMúsica: ${it.name}${if (keepAudio) " (mantém o som original)" else ""}") }
        return sb.toString()
    }

    private fun addClip(a: JSONObject): String {
        val m = media.firstOrNull { it.id == a.optInt("media", -1) } ?: return "Mídia não encontrada. Use video_media."
        if (m.kind == "audio") return "Isso é áudio; use video_music."
        val s = if (a.has("start_s")) secs(a, "start_s").coerceAtLeast(0L) else 0L
        val defEnd = if (m.kind == "image") s + (a.optDouble("seconds", 3.0) * 1000).toLong() else m.durMs
        var end = if (a.has("end_s")) secs(a, "end_s") else defEnd
        if (m.kind == "video" && m.durMs > 0) end = end.coerceAtMost(m.durMs)
        if (end <= s) return "Intervalo inválido."
        clips.add(VClip(m, s, end, a.optDouble("speed", 1.0).toFloat().coerceIn(0.25f, 4f), a.optString("text")))
        selected = clips.size - 1
        return "Clipe ${clips.size - 1} adicionado: ${m.name} de ${fmt(s)} a ${fmt(end)}."
    }

    private fun trim(a: JSONObject): String {
        val i = a.optInt("index", -1)
        val c = clips.getOrNull(i) ?: return "Clipe $i não existe."
        var s = secs(a, "start_s").coerceAtLeast(0L)
        var e = secs(a, "end_s")
        if (c.media.kind == "video" && c.media.durMs > 0) e = e.coerceAtMost(c.media.durMs)
        if (e <= s) return "Intervalo inválido."
        clips[i] = c.copy(startMs = s, endMs = e)
        return "Clipe $i agora vai de ${fmt(s)} a ${fmt(e)}."
    }

    private fun remove(a: JSONObject): String {
        val i = a.optInt("index", -1)
        if (clips.getOrNull(i) == null) return "Clipe $i não existe."
        clips.removeAt(i)
        selected = -1
        return "Clipe $i removido."
    }

    private fun move(a: JSONObject): String {
        val from = a.optInt("from", -1); val to = a.optInt("to", -1)
        if (clips.getOrNull(from) == null || to < 0 || to >= clips.size) return "Posição inválida."
        val c = clips.removeAt(from)
        clips.add(to, c)
        selected = to
        return "Clipe movido para a posição $to."
    }

    private fun speed(a: JSONObject): String {
        val i = a.optInt("index", -1)
        val c = clips.getOrNull(i) ?: return "Clipe $i não existe."
        clips[i] = c.copy(speed = a.optDouble("speed", 1.0).toFloat().coerceIn(0.25f, 4f))
        return "Velocidade do clipe $i: x${clips[i].speed}."
    }

    private fun text(a: JSONObject): String {
        val i = a.optInt("index", -1)
        val c = clips.getOrNull(i) ?: return "Clipe $i não existe."
        clips[i] = c.copy(text = a.optString("text"))
        return "Legenda do clipe $i atualizada."
    }

    private fun setMusic(a: JSONObject): String {
        val m = media.firstOrNull { it.id == a.optInt("media", -1) } ?: return "Mídia não encontrada. Use video_media."
        if (m.kind != "audio") return "Essa mídia não é áudio."
        music = m
        keepAudio = a.optBoolean("keep_audio", false)
        return "Música definida: ${m.name}."
    }

    /** Importa os arquivos que o usuário escolheu (vídeo, foto, áudio). */
    fun addMedia(c: Context, uris: List<Uri>) {
        for (u in uris) {
            try { c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            val mime = c.contentResolver.getType(u).orEmpty()
            val kind = when {
                mime.startsWith("video") -> "video"
                mime.startsWith("image") -> "image"
                mime.startsWith("audio") -> "audio"
                else -> continue
            }
            var name = "arquivo"
            c.contentResolver.query(u, null, null, null, null)?.use { cur ->
                val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && cur.moveToFirst()) name = cur.getString(i)
            }
            var dur = 3000L
            if (kind != "image") {
                dur = try {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(c, u)
                    val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    r.release()
                    d
                } catch (e: Exception) { 0L }
            }
            media.add(VMedia(nextId++, u, name, kind, dur))
        }
    }

    // ---------- exportação ----------

    fun export(format: String): String {
        val c = ctx ?: return "Sem contexto do app."
        if (clips.isEmpty()) return "A timeline está vazia."
        if (exporting) return "Já estou exportando."
        exporting = true
        status = "Preparando exportação..."
        val horizontal = format.lowercase().startsWith("h")
        val w = if (horizontal) 1920 else 1080
        val h = if (horizontal) 1080 else 1920
        val snap = clips.toList()
        val mus = music
        val keep = keepAudio
        Handler(Looper.getMainLooper()).post {
            try { startExport(c, snap, mus, keep, w, h) } catch (e: Exception) { exporting = false; status = "Falhou: ${e.message}" }
        }
        return "Exportando em ${w}x$h. O progresso aparece no editor; quando terminar o vídeo fica na galeria (Movies/Jarvis)."
    }

    private fun startExport(c: Context, list: List<VClip>, mus: VMedia?, keep: Boolean, w: Int, h: Int) {
        val total = list.sumOf { it.lenMs }
        val items = list.map { cl ->
            val b = MediaItem.Builder().setUri(cl.media.uri)
            if (cl.media.kind == "video") {
                b.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(cl.startMs).setEndPositionMs(cl.endMs).build()
                )
            }
            val eb = EditedMediaItem.Builder(b.build())
            if (cl.media.kind == "image") eb.setDurationUs((cl.endMs - cl.startMs) * 1000L).setFrameRate(30)
            if (mus != null && !keep) eb.setRemoveAudio(true)
            val vfx = mutableListOf<Effect>()
            val afx = mutableListOf<AudioProcessor>()
            vfx.add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))
            if (cl.speed != 1f && cl.media.kind == "video") {
                vfx.add(SpeedChangeEffect(cl.speed))
                afx.add(SonicAudioProcessor().apply { setSpeed(cl.speed) })
            }
            if (cl.text.isNotBlank()) {
                val sp = SpannableString(cl.text)
                sp.setSpan(ForegroundColorSpan(android.graphics.Color.WHITE), 0, sp.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(AbsoluteSizeSpan(if (w > h) 64 else 72), 0, sp.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                val settings = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, -0.65f).build()
                vfx.add(OverlayEffect(ImmutableList.of<TextureOverlay>(TextOverlay.createStaticTextOverlay(sp, settings))))
            }
            eb.setEffects(Effects(afx, vfx))
            eb.build()
        }
        val seqs = mutableListOf(EditedMediaItemSequence(items))
        if (mus != null) {
            val endMs = if (mus.durMs > 0) minOf(mus.durMs, total) else total
            val mi = MediaItem.Builder().setUri(mus.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(endMs).build()
            ).build()
            seqs.add(EditedMediaItemSequence(listOf(EditedMediaItem.Builder(mi).setRemoveVideo(true).build())))
        }
        val comp = Composition.Builder(seqs).build()
        val dir = c.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: c.filesDir
        dir.mkdirs()
        val out = File(dir, "jarvis_${System.currentTimeMillis()}.mp4")
        val tr = Transformer.Builder(c).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                exporting = false
                status = "Pronto! " + saveToGallery(c, out)
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                exporting = false
                status = "Falhou: ${exportException.message}"
            }
        }).build()
        tr.start(comp, out.absolutePath)
        val hd = Handler(Looper.getMainLooper())
        val ph = ProgressHolder()
        val tick = object : Runnable {
            override fun run() {
                if (!exporting) return
                if (tr.getProgress(ph) == Transformer.PROGRESS_STATE_AVAILABLE) status = "Exportando ${ph.progress}%"
                hd.postDelayed(this, 500)
            }
        }
        hd.post(tick)
    }

    private fun saveToGallery(c: Context, f: File): String {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, f.name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Jarvis")
                }
                val uri = c.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)
                if (uri != null) {
                    c.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
                    "Salvo na galeria (Movies/Jarvis)."
                } else "Salvo em ${f.absolutePath}"
            } else "Salvo em ${f.absolutePath}"
        } catch (e: Exception) {
            "Salvo em ${f.absolutePath} (não consegui copiar para a galeria)."
        }
    }
}
