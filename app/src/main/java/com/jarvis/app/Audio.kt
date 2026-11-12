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

/** Detecta duas palmas seguidas (150-800 ms entre elas) pelo microfone. */
class ClapDetector(private val onDouble: () -> Unit, private val onLevel: ((Float) -> Unit)? = null) {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
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
            var last = 0L
            var fired = false
            rec.startRecording()
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var peak = 0
                for (i in 0 until n) { val a = abs(buf[i].toInt()); if (a > peak) peak = a }
                onLevel?.let { cb -> val lv = ((peak - 2000f) / 14000f).coerceIn(0f, 1f); handler.post { cb(lv) } }
                val now = SystemClock.elapsedRealtime()
                if (peak > max(9000.0, noise * 6)) {
                    val dt = now - last
                    if (dt in 150..800) { fired = true; break }
                    else if (dt > 800) last = now
                } else noise = noise * 0.995 + peak * 0.005
            }
            rec.stop(); rec.release()
            running = false
            if (fired) Handler(Looper.getMainLooper()).post(onDouble)
        }.also { it.start() }
    }

    fun stop() { running = false }
}

/** Voz: reconhecimento (STT) e fala (TTS) em pt-BR. */
class Voice(private val ctx: Context) {
    private val ui = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sr: SpeechRecognizer? = null
    var onLevel: (Float) -> Unit = {}
    private val resetLevel = Runnable { onLevel(0.18f) }
    private fun kick() { ui.removeCallbacks(resetLevel); onLevel(0.9f); ui.postDelayed(resetLevel, 170) }

    init {
        tts = TextToSpeech(ctx) { s ->
            if (s == TextToSpeech.SUCCESS) { tts?.language = Locale("pt", "BR"); ttsReady = true }
        }
    }

    fun speak(text: String, onFinish: () -> Unit) {
        val t = tts
        if (t == null || !ttsReady) { onFinish(); return }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) { ui.post { kick() } }
            override fun onDone(id: String?) { ui.post { ui.removeCallbacks(resetLevel); onLevel(0f); onFinish() } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { ui.post { ui.removeCallbacks(resetLevel); onLevel(0f); onFinish() } }
        })
        t.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun stopSpeaking() { tts?.stop() }

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
