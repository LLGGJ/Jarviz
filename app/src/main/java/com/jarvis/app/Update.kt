package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** Baixa o APK do artifact do GitHub Actions com o token e abre o instalador do Android. */
class Updater(private val ctx: Context) {
    private val http = OkHttpClient.Builder().readTimeout(180, TimeUnit.SECONDS).build()

    fun install(gh: GitHub, token: String, repo: String, runId: String?): String {
        val id = runId?.takeIf { it.isNotBlank() } ?: run {
            val t = gh.run("github_api", JSONObject().put("method", "GET").put("path", "/repos/$repo/actions/runs?status=success&per_page=1"))
            val arr = try { JSONObject(t).getJSONArray("workflow_runs") } catch (e: Exception) { return t }
            if (arr.length() == 0) return "Nenhum build com sucesso encontrado em $repo."
            arr.getJSONObject(0).getLong("id").toString()
        }
        val t = gh.run("github_api", JSONObject().put("method", "GET").put("path", "/repos/$repo/actions/runs/$id/artifacts"))
        val arts = try { JSONObject(t).getJSONArray("artifacts") } catch (e: Exception) { return t }
        var url: String? = null
        for (i in 0 until arts.length()) {
            val o = arts.getJSONObject(i)
            if (!o.optBoolean("expired")) { url = o.getString("archive_download_url"); break }
        }
        if (url == null) return "O build $id não tem artifact disponível (pode ter expirado)."
        val dir = File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val apk = File(dir, "Jarvis.apk")
        var found = false
        http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { r ->
            if (!r.isSuccessful) return "Falha ao baixar o artifact (HTTP ${r.code}). O token precisa do escopo repo."
            ZipInputStream(r.body!!.byteStream()).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    if (e.name.endsWith(".apk")) { apk.outputStream().use { zis.copyTo(it) }; found = true; break }
                    e = zis.nextEntry
                }
            }
        }
        if (!found) return "Não achei um .apk dentro do artifact."
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "APK baixado. Permita \"instalar apps desconhecidos\" para o Jarvis na tela que abri e peça a instalação de novo."
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        return "Abri o instalador do Android com o APK do build $id. Toque em instalar."
    }
}
