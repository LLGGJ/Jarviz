package com.jarvis.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.Manifest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Guarda o "cérebro" (ViewModel) fora da tela: ele continua vivo e escutando mesmo com o app fechado. */
object BrainStore : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
    fun get(app: Application): JarvisViewModel =
        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[JarvisViewModel::class.java]
}

/** Liga ou desliga o serviço de segundo plano conforme a configuração (só com o microfone liberado). */
fun syncBackground(ctx: Context, on: Boolean) {
    val i = Intent(ctx, JarvisService::class.java)
    try {
        if (on && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            ContextCompat.startForegroundService(ctx, i)
        else ctx.stopService(i)
    } catch (e: Exception) { }
}

class JarvisService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("jarvis_bg", "Jarvis ligado", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "jarvis_bg")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Jarvis ligado")
            .setContentText("Bata 2 palmas ou toque aqui para abrir").setContentIntent(open).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1, n)
        } catch (e: Exception) { stopSelf(); return }
        val brain = BrainStore.get(application)
        brain.onMic(true)
        brain.resumeClap()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
}
