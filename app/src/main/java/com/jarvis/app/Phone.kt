package com.jarvis.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Controle do celular por intents e serviços do Android (sem API externa). */
class PhoneControl(private val ctx: Context) {
    private fun start(i: Intent) { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()

    fun run(name: String, a: JSONObject): String = try {
        when (name) {
            "phone_set_alarm" -> alarm(a)
            "phone_set_timer" -> timer(a)
            "phone_add_event" -> event(a)
            "phone_open_app" -> openApp(a)
            "phone_call" -> call(a)
            "phone_send_message" -> message(a)
            "phone_flashlight" -> flashlight(a)
            "phone_volume" -> volume(a)
            else -> "Ferramenta desconhecida: $name"
        }
    } catch (e: Exception) {
        "Erro: ${e.message}"
    }

    private fun alarm(a: JSONObject): String {
        val h = a.optString("hour").toIntOrNull()
        val m = a.optString("minute", "0").toIntOrNull() ?: 0
        if (h == null || h !in 0..23 || m !in 0..59) return "Horário inválido."
        start(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label").ifBlank { "Jarvis" }).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        return "Alarme criado para %02d:%02d.".format(h, m)
    }

    private fun timer(a: JSONObject): String {
        val s = a.optString("seconds").toIntOrNull()
        if (s == null || s !in 1..86400) return "Duração inválida."
        start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s)
            .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label").ifBlank { "Jarvis" }).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        return "Timer de $s segundos iniciado."
    }

    private fun event(a: JSONObject): String {
        val begin = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(a.getString("start"))?.time ?: return "Data inválida."
        val dur = (a.optString("duration_min").toIntOrNull() ?: 60) * 60_000L
        start(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + dur)
            .putExtra(CalendarContract.Events.TITLE, a.getString("title"))
            .putExtra(CalendarContract.Events.EVENT_LOCATION, a.optString("location"))
            .putExtra(CalendarContract.Events.DESCRIPTION, a.optString("description")))
        return "Abri a agenda com o evento pronto; o usuário só precisa confirmar para salvar."
    }

    private fun openApp(a: JSONObject): String {
        val q = norm(a.getString("name"))
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.firstOrNull { norm(it.loadLabel(pm).toString()) == q }
            ?: apps.firstOrNull { norm(it.loadLabel(pm).toString()).contains(q) } ?: return "App não encontrado."
        val li = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "Não consegui abrir esse app."
        start(li)
        return "Abrindo ${hit.loadLabel(pm)}."
    }

    /** Número direto ou nome de contato -> (número, nome). */
    private fun resolve(to: String): Pair<String, String>? {
        if (Regex("^[+\\d\\s().-]{5,}$").matches(to.trim())) return to.filter { it.isDigit() || it == '+' } to to
        if (!has(Manifest.permission.READ_CONTACTS)) return null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%${to.trim()}%"), null
        )?.use { c -> if (c.moveToFirst()) return c.getString(1).filter { it.isDigit() || it == '+' } to c.getString(0) }
        return null
    }

    private fun call(a: JSONObject): String {
        val (num, who) = resolve(a.getString("to")) ?: return "Não achei esse contato (ou falta a permissão de contatos)."
        val uri = Uri.parse("tel:" + Uri.encode(num))
        start(Intent(if (has(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
        return "Ligando para $who."
    }

    private fun message(a: JSONObject): String {
        val (num, who) = resolve(a.getString("to")) ?: return "Não achei esse contato (ou falta a permissão de contatos)."
        val text = a.optString("text")
        if (a.optString("app", "sms").lowercase().contains("whats")) {
            var d = num.filter { it.isDigit() }
            if (d.length <= 11) d = "55$d"
            start(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$d?text=${Uri.encode(text)}")))
        } else {
            start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", text))
        }
        return "Abri a conversa com $who com a mensagem pronta; é só tocar em enviar."
    }

    private fun flashlight(a: JSONObject): String {
        val on = a.optString("on").equals("true", true)
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            val ch = cm.getCameraCharacteristics(it)
            ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return "Este aparelho não tem lanterna."
        cm.setTorchMode(id, on)
        return if (on) "Lanterna ligada." else "Lanterna desligada."
    }

    private fun volume(a: JSONObject): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = when (a.optString("stream", "music").lowercase()) {
            "ring", "toque" -> AudioManager.STREAM_RING
            "alarm", "alarme" -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }
        val max = am.getStreamMaxVolume(stream)
        when (a.optString("action").lowercase()) {
            "up" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "down" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> am.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
            else -> {
                val p = a.optString("percent").toIntOrNull() ?: return "Informe a porcentagem do volume."
                am.setStreamVolume(stream, (max * p.coerceIn(0, 100) / 100f).roundToInt(), AudioManager.FLAG_SHOW_UI)
            }
        }
        return "Volume em ${am.getStreamVolume(stream) * 100 / max}%."
    }
}

/** Notificações do Jarvis (aviso de build concluído). */
class Notifier(private val ctx: Context) {
    init {
        if (Build.VERSION.SDK_INT >= 26)
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_HIGH))
    }

    @SuppressLint("MissingPermission")
    fun show(title: String, text: String, url: String?) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = url?.let {
            PendingIntent.getActivity(ctx, 0, Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = NotificationCompat.Builder(ctx, "jarvis").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(pi).build()
        NotificationManagerCompat.from(ctx).notify(System.currentTimeMillis().toInt(), n)
    }
}
