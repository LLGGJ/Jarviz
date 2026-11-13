package com.jarvis.app

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

object TermuxBridge {
    val waiting = ConcurrentHashMap<Int, CompletableFuture<String>>()
    val counter = AtomicInteger(1000)
}

/** Recebe o resultado (stdout, stderr, código de saída) que o Termux devolve depois de rodar o comando. */
class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("execution_id", -1)
        val b = intent.getBundleExtra("result")
        val sb = StringBuilder()
        if (b != null) {
            val out = b.getString("stdout").orEmpty(); val err = b.getString("stderr").orEmpty()
            val msg = b.getString("errmsg").orEmpty()
            if (out.isNotBlank()) sb.append(out)
            if (err.isNotBlank()) sb.append("\n[stderr]\n").append(err)
            sb.append("\n[saída ${b.getInt("exitCode", -1)}]")
            if (msg.isNotBlank()) sb.append("\n[erro do Termux: $msg]")
        } else sb.append("(o Termux não devolveu resultado)")
        TermuxBridge.waiting.remove(id)?.complete(sb.toString().trim())
    }
}

const val TERMUX_SETUP = "mkdir -p ~/.termux && grep -q allow-external-apps ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties; pkg install -y curl git"

/** Terminal Linux: usa o Termux (Linux de verdade) quando disponível; senão o shell do Android e um curl embutido. */
class TerminalTools(private val ctx: Context) {
    fun termuxInstalled(): Boolean = try { ctx.packageManager.getPackageInfo("com.termux", 0); true } catch (e: Exception) { false }
    fun hasPermission() = ContextCompat.checkSelfPermission(ctx, "com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED

    fun status(): String = when {
        !termuxInstalled() -> "Termux não instalado. Sem ele o terminal usa o shell do Android (limitado) e um curl embutido."
        !hasPermission() -> "Termux instalado, falta permitir o acesso (botão abaixo)."
        else -> "Termux pronto. Se os comandos falharem, rode o comando de configuração dentro do Termux."
    }

    fun open() {
        ctx.packageManager.getLaunchIntentForPackage("com.termux")?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun copySetup() {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("termux", TERMUX_SETUP))
    }

    fun run(cmd: String): String {
        var hint = ""
        if (termuxInstalled() && hasPermission()) {
            val r = try { termux(cmd) } catch (e: Exception) { hint = "\n(Termux recusou: ${e.message}. Veja a seção Terminal Linux nas configurações.)"; null }
            if (r != null) return "[Linux/Termux]\n$r"
        } else hint = "\n(Para um Linux completo com curl, git e python, instale o Termux e ative nas configurações.)"
        if (cmd.trimStart().startsWith("curl ")) return "[curl embutido]\n" + MiniCurl.run(cmd.trimStart())
        return "[shell do Android]\n" + androidShell(cmd) + hint
    }

    private fun termux(cmd: String): String? {
        val id = TermuxBridge.counter.incrementAndGet()
        val fut = CompletableFuture<String>()
        TermuxBridge.waiting[id] = fut
        val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, TermuxResultReceiver::class.java).putExtra("execution_id", id),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val i = Intent("com.termux.RUN_COMMAND").setClassName("com.termux", "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", cmd))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
        try { ctx.startService(i) } catch (e: Exception) { TermuxBridge.waiting.remove(id); throw e }
        return try { fut.get(90, TimeUnit.SECONDS) } catch (e: TimeoutException) {
            TermuxBridge.waiting.remove(id); "Tempo esgotado esperando o Termux (90s)."
        }
    }

    private fun androidShell(cmd: String): String = try {
        val p = ProcessBuilder("/system/bin/sh", "-c", cmd).redirectErrorStream(true).directory(ctx.filesDir).start()
        val out = StringBuffer()
        val t = Thread {
            p.inputStream.bufferedReader().use { r ->
                val buf = CharArray(4096)
                while (true) { val n = r.read(buf); if (n < 0) break; if (out.length < 16000) out.append(buf, 0, n) }
            }
        }.also { it.start() }
        if (!p.waitFor(60, TimeUnit.SECONDS)) { p.destroyForcibly(); t.join(500); out.append("\n[tempo esgotado: 60s]") }
        else { t.join(1000); out.append("\n[saída ${p.exitValue()}]") }
        out.toString().trim()
    } catch (e: Exception) {
        "Erro: ${e.message}"
    }
}

/** curl simplificado (-X -H -d -I -i -L -u -m -A) para quando não há Termux. */
object MiniCurl {
    private fun split(s: String): List<String> {
        val out = mutableListOf<String>(); val cur = StringBuilder(); var q = ' '; var i = 0
        while (i < s.length) {
            val c = s[i]
            if (q != ' ') { if (c == q) q = ' ' else if (c == '\\' && q == '"' && i + 1 < s.length) { i++; cur.append(s[i]) } else cur.append(c) }
            else if (c == '"' || c == '\'') q = c
            else if (c.isWhitespace()) { if (cur.isNotEmpty()) { out.add(cur.toString()); cur.setLength(0) } }
            else if (c == '\\' && i + 1 < s.length) { i++; if (s[i] != '\n') cur.append(s[i]) }
            else cur.append(c)
            i++
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    fun run(cmd: String): String = try {
        val a = split(cmd).drop(1)
        var method: String? = null; var data: String? = null; var url: String? = null; var user: String? = null
        var head = false; var inc = false; var follow = false; var maxTime = 30L
        val headers = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < a.size) {
            val t = a[i]
            if (Regex("-[sSLkfiIv]{2,}").matches(t)) {
                for (ch in t.drop(1)) when (ch) { 'L' -> follow = true; 'i', 'v' -> inc = true; 'I' -> head = true }
                i++; continue
            }
            when (t) {
                "-X", "--request" -> method = a.getOrNull(++i)
                "-H", "--header" -> { val h = a.getOrNull(++i).orEmpty(); headers.add(h.substringBefore(":").trim() to h.substringAfter(":", "").trim()) }
                "-d", "--data", "--data-raw", "--data-binary" -> data = a.getOrNull(++i)
                "-I", "--head" -> head = true
                "-i", "--include", "-v" -> inc = true
                "-L", "--location" -> follow = true
                "-u", "--user" -> user = a.getOrNull(++i)
                "-m", "--max-time" -> maxTime = a.getOrNull(++i)?.toLongOrNull() ?: 30L
                "-A", "--user-agent" -> headers.add("User-Agent" to a.getOrNull(++i).orEmpty())
                "-o", "-w", "-e", "--referer", "-b", "--cookie" -> i++
                else -> if (!t.startsWith("-")) url = t
            }
            i++
        }
        var u = url ?: return "curl: informe uma URL."
        if (!u.startsWith("http")) u = "http://$u"
        val m = (method ?: if (head) "HEAD" else if (data != null) "POST" else "GET").uppercase()
        val ct = headers.firstOrNull { it.first.equals("Content-Type", true) }?.second ?: "application/x-www-form-urlencoded"
        val body = if (m == "GET" || m == "HEAD") null else (data ?: "").toRequestBody(ct.toMediaTypeOrNull())
        val rb = Request.Builder().url(u).method(m, body)
        headers.forEach { (k, v) -> rb.header(k, v) }
        if (user != null) rb.header("Authorization", okhttp3.Credentials.basic(user.substringBefore(":"), user.substringAfter(":", "")))
        val client = OkHttpClient.Builder().followRedirects(follow).followSslRedirects(follow).callTimeout(maxTime, TimeUnit.SECONDS).build()
        client.newCall(rb.build()).execute().use { r ->
            val sb = StringBuilder()
            if (head || inc) {
                sb.append("${r.protocol} ${r.code} ${r.message}\n")
                r.headers.forEach { (k, v) -> sb.append("$k: $v\n") }
                sb.append("\n")
            }
            if (!head) sb.append(r.peekBody(8000).string())
            sb.append("\n[HTTP ${r.code}]")
            sb.toString().trim()
        }
    } catch (e: Exception) {
        "curl: erro: ${e.message}"
    }
}
