package com.jarvis.app

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** GitHub com token ghp_: a IA pode chamar qualquer endpoint da API REST. Escritas pedem permissão no app. */
class GitHub(private val token: String) {
    private val http = OkHttpClient()

    private fun call(method: String, path: String, body: String? = null): String {
        val p = path.removePrefix("https://api.github.com").let { if (it.startsWith("/")) it else "/$it" }
        val m = method.uppercase()
        val rb = if (m == "GET" || m == "HEAD") null
        else (body?.takeIf { it.isNotBlank() } ?: "{}").toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("https://api.github.com$p")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .method(m, rb).build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw RuntimeException("GitHub ${r.code}: ${t.take(300)}")
            return t.ifBlank { "OK (${r.code})" }
        }
    }

    fun run(name: String, a: JSONObject): String = try {
        when (name) {
            "github_api" -> call(a.optString("method", "GET"), a.getString("path"), a.optString("body")).take(8000)
            "github_list_repos" -> {
                val arr = JSONArray(call("GET", "/user/repos?sort=updated&per_page=30"))
                (0 until arr.length()).joinToString("\n") {
                    val o = arr.getJSONObject(it)
                    "${o.getString("full_name")} - ${o.optString("description", "")}"
                }
            }
            "github_read_file" -> {
                val o = JSONObject(call("GET", "/repos/${a.getString("repo")}/contents/${a.getString("path")}"))
                String(Base64.decode(o.getString("content"), Base64.DEFAULT)).take(8000)
            }
            "github_list_issues" -> {
                val arr = JSONArray(call("GET", "/repos/${a.getString("repo")}/issues?state=open&per_page=20"))
                if (arr.length() == 0) "Nenhuma issue aberta." else (0 until arr.length()).joinToString("\n") {
                    val o = arr.getJSONObject(it)
                    "#${o.getInt("number")} ${o.getString("title")}"
                }
            }
            "github_create_issue" -> {
                val o = JSONObject(call("POST", "/repos/${a.getString("repo")}/issues",
                    JSONObject().put("title", a.getString("title")).put("body", a.optString("body", "")).toString()))
                "Issue criada: ${o.getString("html_url")}"
            }
            else -> "Ferramenta desconhecida: $name"
        }
    } catch (e: Exception) {
        "Erro: ${e.message}"
    }
}
