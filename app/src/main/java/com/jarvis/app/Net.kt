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

    private fun ensureBranch(repo: String, branch: String) {
        try { call("GET", "/repos/$repo/git/ref/heads/$branch"); return } catch (e: Exception) { }
        val def = JSONObject(call("GET", "/repos/$repo")).getString("default_branch")
        val sha = JSONObject(call("GET", "/repos/$repo/git/ref/heads/$def")).getJSONObject("object").getString("sha")
        call("POST", "/repos/$repo/git/refs", JSONObject().put("ref", "refs/heads/$branch").put("sha", sha).toString())
    }

    private fun commit(repo: String, branch: String, path: String, content: String, msg: String): String {
        ensureBranch(repo, branch)
        val sha = try { JSONObject(call("GET", "/repos/$repo/contents/$path?ref=$branch")).optString("sha") } catch (e: Exception) { "" }
        val body = JSONObject().put("message", msg.ifBlank { "Atualiza $path (Jarvis)" })
            .put("content", Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)).put("branch", branch)
        if (sha.isNotBlank()) body.put("sha", sha)
        val o = JSONObject(call("PUT", "/repos/$repo/contents/$path", body.toString()))
        return "Commit feito na branch $branch: ${o.getJSONObject("commit").getString("html_url")}"
    }

    private fun edit(a: JSONObject): String {
        val repo = a.getString("repo"); val branch = a.getString("branch"); val path = a.getString("path")
        ensureBranch(repo, branch)
        val o = JSONObject(call("GET", "/repos/$repo/contents/$path?ref=$branch"))
        val text = String(Base64.decode(o.getString("content"), Base64.DEFAULT))
        val old = a.getString("old")
        if (old.isEmpty()) return "Erro: o trecho a trocar está vazio."
        val n = text.split(old).size - 1
        if (n != 1) return "Erro: o trecho aparece $n vezes em $path (precisa aparecer exatamente 1). Use um trecho maior e único."
        return commit(repo, branch, path, text.replace(old, a.getString("new")), a.optString("message"))
    }

    private fun openPr(a: JSONObject): String {
        val repo = a.getString("repo")
        val base = a.optString("base").ifBlank { JSONObject(call("GET", "/repos/$repo")).getString("default_branch") }
        val o = JSONObject(call("POST", "/repos/$repo/pulls", JSONObject().put("title", a.getString("title"))
            .put("head", a.getString("head")).put("base", base).put("body", a.optString("body")).toString()))
        return "Pull request aberto: ${o.getString("html_url")}"
    }

    private fun listDir(a: JSONObject): String {
        val repo = a.getString("repo"); val path = a.optString("path"); val ref = a.optString("ref")
        val q = if (ref.isBlank()) "" else "?ref=$ref"
        val txt = call("GET", if (path.isBlank()) "/repos/$repo/contents$q" else "/repos/$repo/contents/$path$q")
        val arr = JSONArray(txt)
        return (0 until arr.length()).joinToString("\n") { val o = arr.getJSONObject(it); "${o.getString("type")} ${o.getString("path")}" }
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
                val ref = a.optString("ref")
                val o = JSONObject(call("GET", "/repos/${a.getString("repo")}/contents/${a.getString("path")}" + if (ref.isBlank()) "" else "?ref=$ref"))
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
            "github_list_dir" -> listDir(a)
            "github_commit_file" -> commit(a.getString("repo"), a.getString("branch"), a.getString("path"), a.getString("content"), a.optString("message"))
            "github_edit_file" -> edit(a)
            "github_open_pr" -> openPr(a)
            else -> "Ferramenta desconhecida: $name"
        }
    } catch (e: Exception) {
        "Erro: ${e.message}"
    }
}
