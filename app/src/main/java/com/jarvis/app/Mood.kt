package com.jarvis.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.sqrt

/**
 * Emoções do robô, como nos robozinhos de mesa (Eilik, Emo, Vector):
 * chacoalhar = bravo, queda livre = assustado, tapar a tela ou ficar parado = sonolento, vários toques seguidos = feliz.
 * current: "", "angry", "scared", "sleepy" ou "joy".
 */
object Mood {
    var current by mutableStateOf("")
    private var until = 0L
    private var started = false
    private var lastTouch = SystemClock.elapsedRealtime()
    private var shakes = 0
    private var lastShake = 0L
    private var fallSamples = 0
    private var covered = false
    private var taps = 0
    private var lastTap = 0L
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private fun now() = SystemClock.elapsedRealtime()

    fun set(m: String, ms: Long) { current = m; until = now() + ms }
    fun surprise() = set("surprised", 1300)

    /** Chamar a cada toque na tela. */
    fun touch() {
        lastTouch = now()
        if ((current == "sleepy" || current == "bored") && !covered) current = ""
    }

    /** Toque no robô: 4 toques em 1,5 s deixam ele feliz (cócegas). */
    fun tapped() {
        val t = now()
        taps = if (t - lastTap < 1500) taps + 1 else 1
        lastTap = t
        if (taps >= 4) { taps = 0; set("joy", 3500) }
    }

    /** Chamar uma vez por segundo. */
    fun tick(idle: Boolean) {
        val t = now()
        if (!idle) lastTouch = t
        if (current.isNotEmpty() && current != "sleepy" && current != "bored" && t > until) current = ""
        if ((current == "" || current == "bored") && idle && !covered && t - lastTouch > 90_000) current = "sleepy"
        else if (current == "" && idle && !covered && t - lastTouch > 40_000) current = "bored"
        if ((current == "sleepy" || current == "bored") && !idle) current = ""
    }

    private fun say(c: Context, text: String) {
        try {
            val app = c.applicationContext as android.app.Application
            if (!BrainStore.get(app).cfg.speak) return
            if (tts == null) {
                tts = TextToSpeech(app) { st ->
                    if (st == TextToSpeech.SUCCESS) {
                        tts?.language = Locale("pt", "BR")
                        ttsReady = true
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mood")
                    }
                }
            } else if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mood")
        } catch (e: Exception) { }
    }

    /** Liga os sensores (acelerômetro e proximidade). Pode chamar várias vezes. */
    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { s ->
            sm.registerListener(object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                    val t = now()
                    if (g > 22f && t - lastShake > 180) {
                        shakes = if (t - lastShake < 1200) shakes + 1 else 1
                        lastShake = t
                        if (shakes >= 3 && current != "angry") {
                            shakes = 0
                            set("angry", 5000)
                            say(app, "Ei! Para de me chacoalhar!")
                        }
                    }
                    if (g < 2f) {
                        fallSamples++
                        if (fallSamples == 4 && current != "scared") { set("scared", 3000); say(app, "Aaaah! Tô caindo!") }
                    } else fallSamples = 0
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }, s, SensorManager.SENSOR_DELAY_GAME)
        }
        sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { s ->
            sm.registerListener(object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    covered = e.values[0] < s.maximumRange
                    if (covered) current = "sleepy" else if (current == "sleepy") current = ""
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }, s, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
}
