package com.jarvis.app

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Ferramentas do GitHub que a IA pode chamar. */
class GitHub(private val token: String) {
    private val http = OkHttpClient()

    private fun call(path: String, body: JSONObject? = null): String {
        val b = Request.Builder().url("https://api.github.com$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
        if (body != null) b.post(body.toString().toRequestBody("application/json".toMediaType())) else b.get()
        http.newCall(b.build()).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw RuntimeException("GitHub ${r.code}: ${t.take(200)}")
            return t
        }
    }

    fun run(name: String, a: JSONObject): String = try {
        when (name) {
            "github_list_repos" -> {
                val arr = JSONArray(call("/user/repos?sort=updated&per_page=25"))
                (0 until arr.length()).joinToString("\n") {
                    val o = arr.getJSONObject(it)
                    "${o.getString("full_name")} - ${o.optString("description", "")}"
                }
            }
            "github_read_file" -> {
                val o = JSONObject(call("/repos/${a.getString("repo")}/contents/${a.getString("path")}"))
                String(Base64.decode(o.getString("content"), Base64.DEFAULT)).take(6000)
            }
            "github_list_issues" -> {
                val arr = JSONArray(call("/repos/${a.getString("repo")}/issues?state=open&per_page=15"))
                if (arr.length() == 0) "Nenhuma issue aberta." else (0 until arr.length()).joinToString("\n") {
                    val o = arr.getJSONObject(it)
                    "#${o.getInt("number")} ${o.getString("title")}"
                }
            }
            "github_create_issue" -> {
                val o = JSONObject(
                    call(
                        "/repos/${a.getString("repo")}/issues",
                        JSONObject().put("title", a.getString("title")).put("body", a.optString("body", ""))
                    )
                )
                "Issue criada: ${o.getString("html_url")}"
            }
            else -> "Ferramenta desconhecida: $name"
        }
    } catch (e: Exception) {
        "Erro: ${e.message}"
    }
}
