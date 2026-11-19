package com.jarvis.app

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock

/** Silencia o "plim" do Google quando o Jarvis começa e termina de escutar (volta ao normal logo depois). */
object BeepGuard {
    private var started = false
    @Volatile private var muted = false
    @Volatile private var lastListen = 0L

    private fun setMute(am: AudioManager, on: Boolean) {
        val flag = if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        for (s in intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)) {
            try { am.adjustStreamVolume(s, flag, 0) } catch (e: Exception) { }
        }
    }

    /** Chamar logo antes de pedir para escutar (o plim de início sai antes do estado mudar). */
    fun pre() {
        val app = VideoEditor.ctx ?: return
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!muted) { setMute(am, true); muted = true }
        lastListen = SystemClock.elapsedRealtime()
    }

    fun start(app: Application) {
        if (started) return
        started = true
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Thread {
            val vm = BrainStore.get(app)
            while (true) {
                val now = SystemClock.elapsedRealtime()
                if (vm.state == State.LISTENING) {
                    lastListen = now
                    if (!muted) { setMute(am, true); muted = true }
                } else if (muted && now - lastListen > 1000) {
                    setMute(am, false)
                    muted = false
                }
                Thread.sleep(80)
            }
        }.also { it.isDaemon = true; it.start() }
    }
}
