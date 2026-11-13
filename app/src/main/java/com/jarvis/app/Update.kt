package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** Baixa o .apk direto da Release do build (ou, em builds antigos, do artifact) e abre o instalador do Android. */
class Updater(private val ctx: Context) {
    private val http = OkHttpClient.Builder().readTimeout(180, TimeUnit.SECONDS).build()

    private fun get(gh: GitHub, path: String) = gh.run("github_api", JSONObject().put("method", "GET").put("path", path))

    /** which: vazio = último build; "build-12" = pela tag; número = id da execução do workflow. */
    private fun release(gh: GitHub, repo: String, which: String): JSONObject? {
        val w = which.trim()
        val path = when {
            w.startsWith("build-") -> "/repos/$repo/releases/tags/$w"
            w.isNotEmpty() && w.all { it.isDigit() } -> {
                val n = try { JSONObject(get(gh, "/repos/$repo/actions/runs/$w")).optInt("run_number", 0) } catch (e: Exception) { 0 }
                if (n > 0) "/repos/$repo/releases/tags/build-$n" else null
            }
            else -> null
        }
        if (path != null) return try { JSONObject(get(gh, path)) } catch (e: Exception) { null }
        return try {
            val arr = JSONArray(get(gh, "/repos/$repo/releases?per_page=15"))
            (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { r ->
                val a = r.optJSONArray("assets")
                a != null && (0 until a.length()).any { a.getJSONObject(it).optString("name").endsWith(".apk") }
            }
        } catch (e: Exception) { null }
    }

    fun install(gh: GitHub, token: String, repo: String, which: String?): String {
        val dir = File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val apk = File(dir, "Jarvis.apk")
        val rel = release(gh, repo, which.orEmpty())
        var label = ""
        val assets = rel?.optJSONArray("assets")
        var assetUrl: String? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) { assetUrl = a.getString("url"); label = rel.optString("name"); break }
        }
        if (assetUrl != null) {
            http.newCall(Request.Builder().url(assetUrl).header("Authorization", "Bearer $token")
                .header("Accept", "application/octet-stream").build()).execute().use { r ->
                if (!r.isSuccessful) return "Falha ao baixar o APK (HTTP ${r.code}). O token precisa do escopo repo."
                apk.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
            }
        } else if (!fromArtifact(gh, token, repo, which.orEmpty(), apk)) {
            return "Não achei um APK desse build. Rode um build novo (ele publica o .apk direto na aba Releases)."
        }
        return openInstaller(apk, label.ifBlank { "o build" })
    }

    /** Builds antigos: o APK vinha dentro de um zip de artifact. */
    private fun fromArtifact(gh: GitHub, token: String, repo: String, which: String, apk: File): Boolean {
        val id = which.takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } } ?: run {
            val arr = try { JSONObject(get(gh, "/repos/$repo/actions/runs?status=success&per_page=1")).getJSONArray("workflow_runs") } catch (e: Exception) { return false }
            if (arr.length() == 0) return false
            arr.getJSONObject(0).getLong("id").toString()
        }
        val arts = try { JSONObject(get(gh, "/repos/$repo/actions/runs/$id/artifacts")).getJSONArray("artifacts") } catch (e: Exception) { return false }
        var url: String? = null
        for (i in 0 until arts.length()) {
            val o = arts.getJSONObject(i)
            if (!o.optBoolean("expired")) { url = o.getString("archive_download_url"); break }
        }
        if (url == null) return false
        var found = false
        http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { r ->
            if (!r.isSuccessful) return false
            ZipInputStream(r.body!!.byteStream()).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    if (e.name.endsWith(".apk")) { apk.outputStream().use { zis.copyTo(it) }; found = true; break }
                    e = zis.nextEntry
                }
            }
        }
        return found
    }

    private fun openInstaller(apk: File, label: String): String {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "APK baixado. Permita \"instalar apps desconhecidos\" para o Jarvis na tela que abri e toque em instalar de novo."
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        return "Abri o instalador do Android com $label. Toque em instalar."
    }
}
