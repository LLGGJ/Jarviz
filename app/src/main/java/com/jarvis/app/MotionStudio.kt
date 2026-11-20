package com.jarvis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Estúdio de animação (motion design): o Jarvis descreve uma cena em JSON (textos, formas, fotos, barras, partículas, com
 * entradas animadas e easing) e o app desenha quadro a quadro e grava um MP4 de verdade, sem servidor.
 * O vídeo pronto entra nas mídias do editor para juntar com seus vídeos, música e legendas.
 */
object MotionStudio {
    val PROMPT = """

SKILL: MOTION DESIGN (ferramenta video_motion). Crie animações em vídeo descrevendo uma cena em JSON (parâmetro scene, uma string JSON).
Formato: {"w":1080,"h":1920,"fps":30,"duration":6,"bg":"#0B1020" ou {"gradient":["#0B1020","#1B2A5A"],"angle":90},"layers":[...]}.
Cada camada tem "type", posição "x","y" (0 a 1, centro da camada), "from" e "to" (segundos), "in" (duração da entrada, padrão 0.6), "out" (saída, 0.3), "anim", "ease" e "color".
Tipos: text {"text":"Linha 1\nLinha 2","size":110,"weight":"bold","align":"center|left|right","stagger":0.045} | rect {"w":0.8,"h":0.02,"radius":12} | circle {"r":0.1,"stroke":0} | image {"media":2,"w":1,"h":1} (foto ou vídeo das mídias; kenburns dá zoom lento) | bar {"w":0.8,"h":0.03,"value":0.7} | line {"x2":0.9,"y2":0.5,"width":6} | particles {"count":50,"size":6}.
Animações (anim): fade, slide_up, slide_down, slide_left, slide_right, pop, zoom, float, pulse, spin, grow_x (rect), typewriter e stagger (text, letra por letra), kenburns (image).
Easings (ease): linear, outCubic (padrão), inCubic, inOutCubic, outBack (pop com overshoot), outElastic, outExpo.
Exemplo: {"w":1080,"h":1920,"duration":5,"bg":{"gradient":["#0B1020","#1B2A5A"],"angle":90},"layers":[{"type":"particles","count":40,"color":"#5AA9FF","from":0,"to":5},{"type":"text","text":"JARVIS","size":220,"x":0.5,"y":0.42,"color":"#FFFFFF","anim":"stagger","from":0.3,"to":4.6},{"type":"rect","x":0.5,"y":0.52,"w":0.5,"h":0.006,"color":"#5AA9FF","anim":"grow_x","from":1.2,"to":4.6},{"type":"text","text":"seu assistente","size":64,"x":0.5,"y":0.58,"color":"#B9C5DA","anim":"slide_up","from":1.5,"to":4.6}]}
BOA DIREÇÃO DE MOVIMENTO: hierarquia clara (um título forte, um apoio, no máximo 3 níveis); texto grande e legível, margem de segurança de 8%, bom contraste, no máximo 2 cores de destaque; entradas de 0.4 a 0.7 s com ease outCubic ou outBack (use overshoot com moderação); stagger de 0.04 a 0.08 s por letra; cada mensagem fica pelo menos 1.2 s na tela; saídas mais rápidas que as entradas; ritmo consistente e um elemento vivo no fundo (partículas, float, pulse); estrutura em 3 atos (abre, desenvolve, fecha com chamada). Vertical 1080x1920 para Reels e Shorts, horizontal 1920x1080 para YouTube. Depois de renderizar, use video_add_clip para combinar com os vídeos, ou deixe como está (add=true já põe na timeline).
"""

    private class Scene(val app: Context, val w: Int, val h: Int, val dur: Float) {
        val sc = w / 1080f
        private val cache = HashMap<Int, Bitmap?>()
        fun bitmap(id: Int): Bitmap? = cache.getOrPut(id) {
            val m = VideoEditor.media.firstOrNull { it.id == id && it.kind != "audio" } ?: return@getOrPut null
            try {
                if (m.kind == "image") {
                    val opt = BitmapFactory.Options().apply { inSampleSize = 3 }
                    app.contentResolver.openInputStream(m.uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
                } else {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(app, m.uri)
                    val f = r.getFrameAtTime(0)
                    r.release()
                    f
                }
            } catch (e: Exception) { null }
        }
        fun release() { cache.values.forEach { it?.recycle() }; cache.clear() }
    }

    private fun ease(name: String, x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return when (name) {
            "linear" -> t
            "inCubic" -> t * t * t
            "inOutCubic" -> if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
            "outBack" -> { val c1 = 1.70158f; val c3 = c1 + 1f; 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2) }
            "outElastic" -> if (t == 0f || t == 1f) t else 2f.pow(-10f * t) * sin((t * 10f - 0.75f) * (2f * PI.toFloat() / 3f)) + 1f
            "outExpo" -> if (t == 1f) 1f else 1f - 2f.pow(-10f * t)
            else -> 1f - (1f - t).pow(3)
        }
    }

    private fun parse(s: String, def: Int): Int = try { Color.parseColor(s) } catch (e: Exception) { def }

    private fun drawLayer(c: Canvas, p: Paint, L: JSONObject, t: Float, X: Scene) {
        val from = L.optDouble("from", 0.0).toFloat()
        val to = L.optDouble("to", 1e9).toFloat()
        if (t < from || t > to) return
        val lt = t - from
        val anim = L.optString("anim", "fade")
        val inD = L.optDouble("in", 0.6).toFloat().coerceAtLeast(0.01f)
        val outD = L.optDouble("out", 0.3).toFloat().coerceAtLeast(0.01f)
        val pin = ease(L.optString("ease", if (anim == "pop") "outBack" else "outCubic"), lt / inD)
        var alpha = if (to < 1e8f && t > to - outD) ((to - t) / outD).coerceIn(0f, 1f) else 1f
        val W = X.w.toFloat()
        val H = X.h.toFloat()
        val x = L.optDouble("x", 0.5).toFloat() * W
        val y = L.optDouble("y", 0.5).toFloat() * H
        var dx = 0f
        var dy = 0f
        var sc = 1f
        var rot = 0f
        when (anim) {
            "fade", "typewriter", "stagger", "grow_x", "kenburns" -> if (anim == "fade") alpha *= pin
            "slide_up" -> { dy = (1f - pin) * H * 0.08f; alpha *= pin }
            "slide_down" -> { dy = -(1f - pin) * H * 0.08f; alpha *= pin }
            "slide_left" -> { dx = (1f - pin) * W * 0.12f; alpha *= pin }
            "slide_right" -> { dx = -(1f - pin) * W * 0.12f; alpha *= pin }
            "pop" -> { sc = pin; alpha *= min(1f, pin * 2f) }
            "zoom" -> { sc = 0.6f + 0.4f * pin; alpha *= pin }
            "float" -> { dy = sin(lt * 2f) * H * 0.012f; alpha *= pin }
            "pulse" -> { sc = 1f + 0.06f * sin(lt * 5f); alpha *= pin }
            "spin" -> { rot = (1f - pin) * 180f; sc = pin; alpha *= pin }
        }
        c.save()
        c.translate(dx, dy)
        c.translate(x, y); c.scale(sc, sc); c.rotate(rot); c.translate(-x, -y)
        p.reset()
        p.isAntiAlias = true
        p.color = parse(L.optString("color", "#FFFFFF"), Color.WHITE)
        p.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        when (L.optString("type")) {
            "rect" -> {
                val full = L.optDouble("w", 0.5).toFloat() * W
                val rw = if (anim == "grow_x") full * pin else full
                val rh = L.optDouble("h", 0.1).toFloat() * H
                val left = x - full / 2f
                val rr = L.optDouble("radius", 0.0).toFloat() * X.sc
                c.drawRoundRect(RectF(left, y - rh / 2f, left + rw, y + rh / 2f), rr, rr, p)
            }
            "circle" -> {
                val stroke = L.optDouble("stroke", 0.0).toFloat() * X.sc
                if (stroke > 0f) { p.style = Paint.Style.STROKE; p.strokeWidth = stroke }
                c.drawCircle(x, y, L.optDouble("r", 0.1).toFloat() * min(W, H), p)
            }
            "text" -> {
                val size = L.optDouble("size", 96.0).toFloat() * X.sc
                p.textSize = size
                p.typeface = if (L.optString("weight", "bold") == "bold") Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                val lines = L.optString("text").split("\n")
                val lh = size * 1.18f
                val y0 = y - (lines.size - 1) * lh / 2f + size * 0.35f
                val align = L.optString("align", "center")
                val stag = L.optDouble("stagger", 0.045).toFloat()
                var gi = 0
                for ((li, line) in lines.withIndex()) {
                    val tw = p.measureText(line)
                    val px = when (align) { "left" -> x; "right" -> x - tw; else -> x - tw / 2f }
                    val ly = y0 + li * lh
                    if (anim == "typewriter" || anim == "stagger") {
                        var ax = px
                        for (ch in line) {
                            val s = ch.toString()
                            val cw = p.measureText(s)
                            val k = ease("outCubic", (lt - gi * stag) / 0.35f)
                            if (k > 0f) {
                                if (anim == "typewriter") c.drawText(s, ax, ly, p)
                                else {
                                    val a0 = p.alpha
                                    p.alpha = (a0 * k).toInt()
                                    c.drawText(s, ax, ly + (1f - k) * size * 0.45f, p)
                                    p.alpha = a0
                                }
                            }
                            ax += cw
                            gi++
                        }
                    } else c.drawText(line, px, ly, p)
                }
            }
            "image" -> {
                val bmp = X.bitmap(L.optInt("media", -1))
                if (bmp != null) {
                    val bw = L.optDouble("w", 1.0).toFloat() * W
                    val bh = L.optDouble("h", 1.0).toFloat() * H
                    val dst = RectF(x - bw / 2f, y - bh / 2f, x + bw / 2f, y + bh / 2f)
                    val span = if (to < 1e8f) to - from else X.dur - from
                    val k = if (anim == "kenburns") 1f + 0.12f * (lt / max(0.1f, span)) else 1f
                    val sa = bmp.width.toFloat() / bmp.height
                    val da = bw / bh
                    val src = if (sa > da) {
                        val nw = bmp.height * da
                        Rect(((bmp.width - nw) / 2f).toInt(), 0, ((bmp.width + nw) / 2f).toInt(), bmp.height)
                    } else {
                        val nh = bmp.width / da
                        Rect(0, ((bmp.height - nh) / 2f).toInt(), bmp.width, ((bmp.height + nh) / 2f).toInt())
                    }
                    c.save()
                    c.clipRect(dst)
                    c.translate(dst.centerX(), dst.centerY()); c.scale(k, k); c.translate(-dst.centerX(), -dst.centerY())
                    c.drawBitmap(bmp, src, dst, p)
                    c.restore()
                }
            }
            "bar" -> {
                val bw = L.optDouble("w", 0.8).toFloat() * W
                val bh = L.optDouble("h", 0.03).toFloat() * H
                val v = L.optDouble("value", 1.0).toFloat().coerceIn(0f, 1f) * pin
                val left = x - bw / 2f
                val a0 = p.alpha
                p.alpha = (a0 * 0.25f).toInt()
                c.drawRoundRect(RectF(left, y - bh / 2f, left + bw, y + bh / 2f), bh / 2f, bh / 2f, p)
                p.alpha = a0
                c.drawRoundRect(RectF(left, y - bh / 2f, left + bw * v, y + bh / 2f), bh / 2f, bh / 2f, p)
            }
            "line" -> {
                val x2 = L.optDouble("x2", 0.9).toFloat() * W
                val y2 = L.optDouble("y2", 0.5).toFloat() * H
                p.style = Paint.Style.STROKE
                p.strokeWidth = L.optDouble("width", 6.0).toFloat() * X.sc
                p.strokeCap = Paint.Cap.ROUND
                c.drawLine(x, y, x + (x2 - x) * pin, y + (y2 - y) * pin, p)
            }
            "particles" -> {
                val n = L.optInt("count", 40).coerceIn(1, 200)
                val rs = L.optDouble("size", 6.0).toFloat() * X.sc
                val a0 = p.alpha
                for (i in 0 until n) {
                    val rx = ((i * 7919) % 1000) / 1000f
                    val ry = ((i * 104729) % 1000) / 1000f
                    val sp = 0.02f + ((i * 31) % 100) / 100f * 0.06f
                    val py = ((ry - lt * sp) % 1f + 1f) % 1f
                    p.alpha = (a0 * (0.4f + 0.6f * abs(sin(lt * 2f + i)))).toInt()
                    c.drawCircle(rx * W, py * H, rs * (0.5f + ((i * 13) % 10) / 10f), p)
                }
            }
        }
        c.restore()
    }

    private fun drawScene(c: Canvas, p: Paint, scene: JSONObject, layers: JSONArray, t: Float, X: Scene) {
        val W = X.w.toFloat()
        val H = X.h.toFloat()
        val bg = scene.opt("bg")
        if (bg is JSONObject) {
            val cols = bg.optJSONArray("gradient")
            if (cols != null && cols.length() >= 2) {
                val ang = bg.optDouble("angle", 90.0) * PI / 180.0
                val dx = cos(ang).toFloat() * W / 2f
                val dy = sin(ang).toFloat() * H / 2f
                p.reset()
                p.shader = LinearGradient(W / 2f - dx, H / 2f - dy, W / 2f + dx, H / 2f + dy, parse(cols.optString(0), Color.BLACK), parse(cols.optString(1), Color.BLACK), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, W, H, p)
                p.shader = null
            } else c.drawColor(Color.BLACK)
        } else c.drawColor(parse(scene.optString("bg", "#0B1020"), Color.BLACK))
        for (i in 0 until layers.length()) {
            val L = layers.optJSONObject(i) ?: continue
            drawLayer(c, p, L, t, X)
        }
    }

    /** Renderiza a cena e devolve o arquivo MP4. Chamar fora da thread principal. */
    fun render(ctx: Context, scene: JSONObject, onProgress: (Int) -> Unit): File {
        val w = scene.optInt("w", 1080).coerceIn(320, 1920) / 2 * 2
        val h = scene.optInt("h", 1920).coerceIn(320, 1920) / 2 * 2
        val fps = scene.optInt("fps", 30).coerceIn(12, 60)
        val dur = scene.optDouble("duration", 5.0).coerceIn(1.0, 60.0).toFloat()
        val frames = (dur * fps).toInt()
        val layers = scene.optJSONArray("layers") ?: JSONArray()
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: ctx.filesDir
        dir.mkdirs()
        val out = File(dir, "motion_${System.currentTimeMillis()}.mp4")

        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, if (w * h > 1_500_000) 10_000_000 else 6_000_000)
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxing = false
        var outFrame = 0
        val info = MediaCodec.BufferInfo()
        val X = Scene(ctx, w, h, dur)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        fun drain(end: Boolean) {
            while (true) {
                val i = codec.dequeueOutputBuffer(info, if (end) 10_000L else 0L)
                if (i == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!end) return else continue }
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxing = true
                    continue
                }
                if (i < 0) continue
                val buf = codec.getOutputBuffer(i)
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                if (buf != null && info.size > 0 && muxing) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    info.presentationTimeUs = outFrame * 1_000_000L / fps
                    outFrame++
                    muxer.writeSampleData(track, buf, info)
                }
                codec.releaseOutputBuffer(i, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
            }
        }

        try {
            for (f in 0 until frames) {
                val c = surface.lockHardwareCanvas()
                try { drawScene(c, p, scene, layers, f / fps.toFloat(), X) } finally { surface.unlockCanvasAndPost(c) }
                drain(false)
                if (f % 5 == 0) onProgress(f * 100 / frames)
            }
            codec.signalEndOfInputStream()
            drain(true)
        } finally {
            try { codec.stop() } catch (e: Exception) { }
            codec.release()
            try { if (muxing) muxer.stop() } catch (e: Exception) { }
            muxer.release()
            surface.release()
            X.release()
        }
        return out
    }
}
