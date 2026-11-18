package com.jarvis.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.nio.ByteOrder
import java.util.Locale

/** Escurece o começo e o fim do clipe (dissolve pelo preto). O tempo é contado a partir do primeiro quadro do clipe. */
private class FadeEffect(private val totalUs: Long, private val inUs: Long, private val outUs: Long) : RgbMatrix {
    private var first = -1L
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        if (first < 0) first = presentationTimeUs
        val t = presentationTimeUs - first
        var f = 1f
        if (inUs > 0 && t < inUs) f = t.toFloat() / inUs
        if (outUs > 0 && t > totalUs - outUs) f = minOf(f, (totalUs - t).toFloat() / outUs)
        f = f.coerceIn(0f, 1f)
        return floatArrayOf(f, 0f, 0f, 0f, 0f, f, 0f, 0f, 0f, 0f, f, 0f, 0f, 0f, 0f, 1f)
    }
}

/** Zoom lento durante o clipe (de from até to). */
private class ZoomEffect(private val totalUs: Long, private val from: Float, private val to: Float) : MatrixTransformation {
    private var first = -1L
    override fun getMatrix(presentationTimeUs: Long): Matrix {
        if (first < 0) first = presentationTimeUs
        val p = if (totalUs > 0) ((presentationTimeUs - first).toFloat() / totalUs).coerceIn(0f, 1f) else 0f
        val s = from + (to - from) * p
        return Matrix().apply { postScale(s, s) }
    }
}

class VMedia(val id: Int, val uri: Uri, val name: String, val kind: String, val durMs: Long)

data class VClip(val media: VMedia, val startMs: Long, val endMs: Long, val speed: Float = 1f, val text: String = "", val filter: String = "", val transition: String = "", val transSec: Float = 0.5f) {
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
    var jamendoId = ""
    private val tracks = HashMap<String, JSONObject>()
    private val http = OkHttpClient()

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
        "CORTE NO RITMO: use video_beats na música (ou no vídeo) e monte clipes que terminem nas batidas. FILTROS: video_filter (pb, quente, frio, vivido, zoom). TRANSIÇÕES: video_transition (fade escurece o começo e o fim do clipe; fadein; fadeout; zoomin; zoomout). Isso é dissolver pelo preto e zoom, NÃO é fusão entre dois clipes: diga isso se pedirem crossfade. " +
        "MÚSICA LIVRE: video_music_search procura no Jamendo (licenças Creative Commons) e video_music_get baixa e adiciona; sempre informe a licença e peça para creditar o artista, pois algumas proíbem uso comercial. " +
        "Seja honesto: a prévia não mostra velocidade, filtros nem texto, só o vídeo final exportado mostra. Não diga que exportou antes de video_export responder."

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
        t("video_filter", "Aplica um filtro a um clipe: pb, quente, frio, vivido, zoom ou nenhum.", p("index" to "number", "filter" to "string"), listOf("index", "filter")),
        t("video_beats", "Analisa as batidas de uma mídia (música ou vídeo com som) e devolve o BPM aproximado e os tempos das batidas em segundos.", p("media" to "number", "max_s" to "number"), listOf("media")),
        t("video_music_search", "Procura música livre (Creative Commons) no Jamendo. Devolve id, nome, artista, duração e licença.", p("query" to "string"), listOf("query")),
        t("video_music_get", "Baixa uma música achada com video_music_search (track_id) e adiciona às mídias como áudio.", p("track_id" to "string"), listOf("track_id")),
        t("video_transition", "Põe uma transição num clipe: fade, fadein, fadeout, zoomin, zoomout ou nenhum. seconds = duração do fade.", p("index" to "number", "type" to "string", "seconds" to "number"), listOf("index", "type")),
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
                "video_filter" -> filter(a)
                "video_beats" -> beats(a)
                "video_music_search" -> musicSearch(a)
                "video_music_get" -> musicGet(a)
                "video_transition" -> transition(a)
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
            if (c.filter.isNotBlank()) sb.append(" filtro=${c.filter}")
            if (c.transition.isNotBlank()) sb.append(" transição=${c.transition}")
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

    // ---------- transições, filtros, ritmo e música livre ----------

    private fun transition(a: JSONObject): String {
        val i = a.optInt("index", -1)
        val c = clips.getOrNull(i) ?: return "Clipe $i não existe."
        val t = a.optString("type").lowercase().let { if (it == "nenhum") "" else it }
        if (t !in setOf("", "fade", "fadein", "fadeout", "zoomin", "zoomout")) return "Tipo inválido. Use fade, fadein, fadeout, zoomin, zoomout ou nenhum."
        val sec = a.optDouble("seconds", 0.5).toFloat().coerceIn(0.1f, 3f)
        clips[i] = c.copy(transition = t, transSec = sec)
        return "Transição do clipe $i: ${t.ifBlank { "nenhuma" }} (${sec}s)."
    }

    private fun filter(a: JSONObject): String {
        val i = a.optInt("index", -1)
        val c = clips.getOrNull(i) ?: return "Clipe $i não existe."
        val f = a.optString("filter").lowercase().let { if (it == "nenhum") "" else it }
        if (f !in setOf("", "pb", "quente", "frio", "vivido", "zoom")) return "Filtro inválido. Use pb, quente, frio, vivido, zoom ou nenhum."
        clips[i] = c.copy(filter = f)
        return "Filtro do clipe $i: ${f.ifBlank { "nenhum" }}."
    }

    /** Energia do áudio a cada 20 ms (decodifica o áudio e mede o volume). */
    private fun envelope(c: Context, uri: Uri, maxMs: Long): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(c, uri, null)
        var track = -1
        for (i in 0 until ex.trackCount) {
            if ((ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { track = i; break }
        }
        if (track < 0) { ex.release(); return FloatArray(0) }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        val hop = rate / 50
        val out = ArrayList<Float>()
        var acc = 0.0
        var cnt = 0
        val info = MediaCodec.BufferInfo()
        var inDone = false
        var outDone = false
        val limitUs = maxMs * 1000L
        while (!outDone) {
            if (!inDone) {
                val ii = codec.dequeueInputBuffer(10000)
                if (ii >= 0) {
                    val buf = codec.getInputBuffer(ii)!!
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0 || ex.sampleTime > limitUs) {
                        codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inDone = true
                    } else {
                        codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val oi = codec.dequeueOutputBuffer(info, 10000)
            if (oi >= 0) {
                val ob = codec.getOutputBuffer(oi)!!
                ob.position(info.offset)
                ob.limit(info.offset + info.size)
                val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                var k = 0
                while (sb.hasRemaining()) {
                    val v = sb.get().toDouble()
                    if (k % ch == 0) {
                        acc += v * v
                        cnt++
                        if (cnt >= hop) { out.add(Math.sqrt(acc / cnt).toFloat()); acc = 0.0; cnt = 0 }
                    }
                    k++
                }
                codec.releaseOutputBuffer(oi, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
            }
        }
        codec.stop()
        codec.release()
        ex.release()
        return out.toFloatArray()
    }

    private fun beatTimes(env: FloatArray): List<Double> {
        if (env.size < 30) return emptyList()
        val d = FloatArray(env.size) { if (it == 0) 0f else maxOf(0f, env[it] - env[it - 1]) }
        val beats = ArrayList<Double>()
        var last = -100
        for (i in 1 until d.size - 1) {
            val lo = maxOf(0, i - 25)
            val hi = minOf(d.size, i + 25)
            var sum = 0f
            for (k in lo until hi) sum += d[k]
            val mean = sum / (hi - lo)
            if (d[i] > mean * 1.8f && d[i] >= d[i - 1] && d[i] >= d[i + 1] && i - last >= 12) { beats.add(i * 0.02); last = i }
        }
        return beats
    }

    private fun beats(a: JSONObject): String {
        val m = media.firstOrNull { it.id == a.optInt("media", -1) } ?: return "Mídia não encontrada. Use video_media."
        val c = ctx ?: return "Sem contexto do app."
        if (m.kind == "image") return "Foto não tem batida."
        val maxS = a.optDouble("max_s", 60.0).coerceIn(5.0, 180.0)
        val b = beatTimes(envelope(c, m.uri, (maxS * 1000).toLong()))
        if (b.size < 4) return "Não achei batidas claras nessa mídia."
        val gaps = b.zipWithNext { x, y -> y - x }.sorted()
        val bpm = (60.0 / gaps[gaps.size / 2]).toInt()
        return "BPM aproximado: $bpm. Batidas (s): " + b.take(80).joinToString(", ") { String.format(Locale.US, "%.2f", it) }
    }

    private fun httpText(url: String): String {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return t
        }
    }

    private fun musicSearch(a: JSONObject): String {
        if (jamendoId.isBlank()) return "Falta o Client ID do Jamendo (grátis em devportal.jamendo.com). Peça ao usuário para colar em Configurações > GitHub e extras."
        val q = URLEncoder.encode(a.optString("query"), "UTF-8")
        val body = httpText("https://api.jamendo.com/v3.0/tracks/?client_id=$jamendoId&format=json&limit=6&audioformat=mp32&include=licenses&search=$q")
        val res = JSONObject(body).optJSONArray("results") ?: return "Nada encontrado."
        if (res.length() == 0) return "Nada encontrado para essa busca."
        val sb = StringBuilder()
        for (i in 0 until res.length()) {
            val o = res.getJSONObject(i)
            val id = o.optString("id")
            tracks[id] = o
            val lic = o.optString("license_ccurl")
            sb.append("$id | ${o.optString("name")} | ${o.optString("artist_name")} | ${o.optInt("duration")}s | licença: $lic")
            if (lic.contains("-nc")) sb.append(" (NÃO permite uso comercial)")
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun musicGet(a: JSONObject): String {
        val id = a.optString("track_id")
        val o = tracks[id] ?: return "Faça video_music_search antes."
        val c = ctx ?: return "Sem contexto do app."
        val url = o.optString("audio").ifBlank { o.optString("audiodownload") }
        if (url.isBlank()) return "Essa faixa não tem arquivo para baixar."
        val f = File(c.cacheDir, "jamendo_$id.mp3")
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            f.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
        }
        val title = o.optString("name") + " - " + o.optString("artist_name")
        val m = VMedia(nextId++, Uri.fromFile(f), title, "audio", o.optInt("duration") * 1000L)
        media.add(m)
        return "Adicionada como mídia ${m.id}: $title. Licença: ${o.optString("license_ccurl")}. Dê o crédito ao artista."
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
            when (cl.filter) {
                "pb" -> vfx.add(HslAdjustment.Builder().adjustSaturation(-100f).build())
                "quente" -> vfx.add(RgbAdjustment.Builder().setRedScale(1.15f).setBlueScale(0.9f).build())
                "frio" -> vfx.add(RgbAdjustment.Builder().setBlueScale(1.15f).setRedScale(0.9f).build())
                "vivido" -> { vfx.add(HslAdjustment.Builder().adjustSaturation(35f).build()); vfx.add(Contrast(0.2f)) }
                "zoom" -> vfx.add(ScaleAndRotateTransformation.Builder().setScale(1.25f, 1.25f).build())
            }
            val totalUs = cl.lenMs * 1000L
            val sUs = (cl.transSec * 1_000_000f).toLong()
            when (cl.transition) {
                "fade" -> vfx.add(FadeEffect(totalUs, sUs, sUs))
                "fadein" -> vfx.add(FadeEffect(totalUs, sUs, 0L))
                "fadeout" -> vfx.add(FadeEffect(totalUs, 0L, sUs))
                "zoomin" -> vfx.add(ZoomEffect(totalUs, 1f, 1.2f))
                "zoomout" -> vfx.add(ZoomEffect(totalUs, 1.2f, 1f))
            }
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
