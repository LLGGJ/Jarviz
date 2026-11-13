package com.jarvis.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Detecta duas palmas: cada palma precisa ser um impulso curto (sobe de repente e cai logo em seguida),
 * bem acima do ruído; as duas ficam entre 200 e 750 ms. Fica desarmado enquanto você digita, toca na tela,
 * ou logo depois que a IA termina de falar (para não ouvir o eco dela).
 */
class ClapDetector(
    private val onDouble: () -> Unit,
    private val onLevel: ((Float) -> Unit)? = null,
    private val threshold: () -> Int = { 12000 },
    private val suppressed: () -> Boolean = { false },
) {
    @Volatile private var running = false
    @Volatile private var armedAt = 0L
    private var thread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun start() {
        armedAt = SystemClock.elapsedRealtime() + 1500
        running = true
        if (thread?.isAlive == true) return
        thread = Thread {
            val rate = 16000
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, max(min, 4096))
            } catch (e: Exception) { running = false; return@Thread }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { running = false; rec.release(); return@Thread }
            val buf = ShortArray(512)
            var noise = 1500.0
            var prevPeak = 0
            var onsetAt = 0L; var onsetPeak = 0
            var lastClap = 0L
            var fired = false
            rec.startRecording()
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var peak = 0
                for (i in 0 until n) { val a = abs(buf[i].toInt()); if (a > peak) peak = a }
                val now = SystemClock.elapsedRealtime()
                onLevel?.let { cb -> val lv = ((peak - 2000f) / 14000f).coerceIn(0f, 1f); handler.post { cb(lv) } }
                val thr = max(threshold().toDouble(), noise * 8)
                if (onsetAt != 0L) {
                    // o quadro seguinte ao impulso precisa cair: palma = impulso curto; fala e música se sustentam
                    if (peak < onsetPeak * 0.45 && now > armedAt && !suppressed()) {
                        if (lastClap != 0L && onsetAt - lastClap in 200..750) { fired = true; break }
                        lastClap = onsetAt
                    }
                    onsetAt = 0L
                } else if (peak > thr && prevPeak < thr * 0.3) {
                    onsetAt = now; onsetPeak = peak
                }
                if (peak <= thr) noise = noise * 0.995 + peak * 0.005
                if (lastClap != 0L && now - lastClap > 900) lastClap = 0L
                prevPeak = peak
            }
            rec.stop(); rec.release()
            running = false
            if (fired) handler.post(onDouble)
        }.also { it.start() }
    }

    fun stop() { running = false }
}

/** Limpa o texto para a fala: sem markdown, código, links nem emojis. */
fun speechClean(s: String): String = s
    .replace(Regex("(?s)```.*?```"), " ")
    .replace(Regex("https?://\\S+"), " link no chat ")
    .replace(Regex("[*#`_>|~]+"), " ")
    .replace(Regex("[\\p{So}\\p{Cs}]"), "")
    .replace(Regex("\\s+"), " ")
    .trim()

/** Voz: reconhecimento (STT) e fala (TTS) em pt-BR, com fila por frases e escolha da melhor voz. */
class Voice(private val ctx: Context) {
    private val ui = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sr: SpeechRecognizer? = null
    var onLevel: (Float) -> Unit = {}
    var onReady: () -> Unit = {}
    private var voiceName = ""
    private var rate = 1.0f
    private var pending = 0
    private var finishedStream = true
    private var onAll: () -> Unit = {}
    private var counter = 0
    private val resetLevel = Runnable { onLevel(0.18f) }

    private fun kick() { ui.removeCallbacks(resetLevel); onLevel(0.9f); ui.postDelayed(resetLevel, 170) }

    private fun done() {
        pending = (pending - 1).coerceAtLeast(0)
        if (pending == 0) {
            ui.removeCallbacks(resetLevel); onLevel(0f)
            if (finishedStream) { val cb = onAll; onAll = {}; cb() }
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(id: String?) {}
        override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) { ui.post { kick() } }
        override fun onDone(id: String?) { ui.post { done() } }
        @Deprecated("Deprecated in Java")
        override fun onError(id: String?) { ui.post { done() } }
    }

    init {
        tts = TextToSpeech(ctx) { s ->
            if (s == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.setOnUtteranceProgressListener(listener)
                ui.post { apply(); onReady() }
            }
        }
    }

    private fun ptList(): List<android.speech.tts.Voice> =
        (tts?.voices ?: emptySet()).filter { it.locale.language == "pt" && !it.features.contains("notInstalled") }
            .sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == "BR" }
                .thenByDescending { it.quality }.thenByDescending { it.isNetworkConnectionRequired }.thenBy { it.name })

    /** (nome interno, nome para mostrar) das vozes em português, da melhor para a pior. */
    fun ptVoices(): List<Pair<String, String>> = ptList().mapIndexed { i, v ->
        v.name to "Voz ${i + 1} (${if (v.isNetworkConnectionRequired) "rede" else "local"}, ${v.locale.country})"
    }

    private fun apply() {
        val t = tts ?: return
        if (!ttsReady) return
        val v = if (voiceName.isNotBlank()) t.voices?.firstOrNull { it.name == voiceName } else ptList().firstOrNull()
        if (v != null) t.voice = v else t.language = Locale("pt", "BR")
        t.setSpeechRate(rate)
        t.setPitch(0.97f)
    }

    fun configure(name: String, speed: Float) { voiceName = name; rate = speed; apply() }

    /** Começa uma fala em fila: chame enqueue() para cada frase e endStream() ao terminar. */
    fun beginStream(onFinished: () -> Unit) { pending = 0; finishedStream = false; onAll = onFinished }

    fun enqueue(text: String): Boolean {
        val t = speechClean(text)
        val engine = tts
        if (t.isBlank() || engine == null || !ttsReady) return false
        t.chunked(3500).forEach { part ->
            pending++
            engine.speak(part, TextToSpeech.QUEUE_ADD, null, "u${counter++}")
        }
        return true
    }

    fun endStream() {
        finishedStream = true
        if (pending == 0) { val cb = onAll; onAll = {}; ui.post(cb) }
    }

    fun stopSpeaking() {
        tts?.stop(); pending = 0; finishedStream = true; onAll = {}
        ui.removeCallbacks(resetLevel); onLevel(0f)
    }

    fun listen(onPartial: (String) -> Unit, onResult: (String?) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { onResult(null); return }
        sr?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) { onLevel(((v + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) { onResult(null) }
            override fun onResults(b: Bundle?) {
                onResult(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            }
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        )
    }

    fun release() { sr?.destroy(); tts?.stop(); tts?.shutdown() }
}
