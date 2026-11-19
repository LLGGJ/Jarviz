package com.jarvis.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Ativa o Jarvis só falando "Jarvis", sem palma. Usa o Vosk, um reconhecedor de voz que roda no próprio celular
 * (baixa uma vez um modelo em português de uns 31 MB). Fica parado enquanto o Jarvis escuta, pensa ou fala.
 */
object WakeWord {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"
    private val HIT = Regex("jarvis|jarves|jarvi[sz]|garvis|charvis|zarvis|j[áa] vis")

    @Volatile private var running = false
    @Volatile var status = ""

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    /** Aceita variações de como o reconhecedor escreve "Jarvis". */
    private fun heard(text: String): Boolean {
        if (HIT.containsMatchIn(text)) return true
        val words = Regex("[a-zà-ú]+").findAll(text).map { it.value }.toList()
        for (i in words.indices) {
            if (words[i].length >= 4 && lev(words[i], "jarvis") <= 2) return true
            if (i + 1 < words.size && (words[i] + words[i + 1]).length >= 5 && lev(words[i] + words[i + 1], "jarvis") <= 2) return true
        }
        return false
    }

    private fun modelDir(c: Context) = File(c.filesDir, "vosk-pt")
    private fun hasModel(c: Context) = File(modelDir(c), "am").isDirectory

    /** Liga ou desliga conforme a configuração e a permissão do microfone. */
    fun sync(c: Context, vm: JarvisViewModel) {
        val app = c.applicationContext
        val mic = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (vm.cfg.wake && mic) {
            if (!running) {
                running = true
                Thread { loop(app, vm) }.also { it.isDaemon = true; it.start() }
            }
        } else {
            running = false
            if (!vm.cfg.wake) status = ""
        }
    }

    private fun download(c: Context) {
        val zip = File(c.cacheDir, "vosk-pt.zip")
        OkHttpClient().newCall(Request.Builder().url(MODEL_URL).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            zip.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
        }
        val dir = modelDir(c)
        dir.deleteRecursively()
        dir.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val rel = e.name.substringAfter('/', "")
                if (rel.isNotEmpty() && !rel.contains("..")) {
                    val f = File(dir, rel)
                    if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { zin.copyTo(it) } }
                }
                e = zin.nextEntry
            }
        }
        zip.delete()
    }

    private fun loop(c: Context, vm: JarvisViewModel) {
        try {
            if (!hasModel(c)) { status = "Baixando o modelo de voz (31 MB)..."; download(c) }
            status = "Carregando..."
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val model = Model(modelDir(c).absolutePath)
            val rec = Recognizer(model, 16000f)
            val buf = ByteArray(3200)
            val bufSize = maxOf(AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 6400)
            var ar: AudioRecord? = null
            status = "Escutando \"Jarvis\"."
            DebugLog.add("wake: escutando")
            while (running) {
                if (vm.state != State.IDLE) {
                    ar?.stop(); ar?.release(); ar = null
                    Thread.sleep(300)
                    continue
                }
                if (ar == null) {
                    ar = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
                    ar.startRecording()
                    rec.reset()
                }
                val n = ar.read(buf, 0, buf.size)
                if (n > 0) {
                    val fin = rec.acceptWaveForm(buf, n)
                    val text = (if (fin) rec.result else rec.partialResult).lowercase()
                    if (fin && text.length > 18) DebugLog.add("wake ouviu: " + text.take(80))
                    if (heard(text)) {
                        DebugLog.add("wake: ouvi Jarvis")
                        rec.reset()
                        ar.stop(); ar.release(); ar = null
                        BeepGuard.pre()
                        Handler(Looper.getMainLooper()).post { vm.listenNow() }
                        Thread.sleep(1500)
                    }
                }
            }
            ar?.stop(); ar?.release()
            rec.close()
            model.close()
            status = ""
        } catch (e: Throwable) {
            status = "Erro: ${e.message}"
            DebugLog.add("wake erro: ${e.message}")
        } finally {
            running = false
        }
    }
}
