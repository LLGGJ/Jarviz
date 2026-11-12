package com.jarvis.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Pesquisa e leitura de páginas. Usa Firecrawl se houver chave; senão DuckDuckGo (sem chave). */
class WebTools(private val firecrawlKey: String) {
    private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private val ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36"

    fun run(name: String, a: JSONObject): String = try {
        when (name) {
            "web_search" -> search(a.getString("query"))
            "web_fetch" -> fetch(a.getString("url"))
            else -> "Ferramenta desconhecida: $name"
        }
    } catch (e: Exception) {
        "Erro: ${e.message}"
    }

    private fun clean(s: String) = s.replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

    private fun firecrawl(path: String, body: JSONObject): JSONObject {
        val req = Request.Builder().url("https://api.firecrawl.dev$path")
            .header("Authorization", "Bearer $firecrawlKey")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("Firecrawl HTTP ${r.code}")
            return JSONObject(t)
        }
    }

    private fun search(q: String): String {
        if (firecrawlKey.isNotBlank()) try {
            val root = firecrawl("/v1/search", JSONObject().put("query", q).put("limit", 6))
            val arr = root.optJSONArray("data") ?: root.optJSONObject("data")?.optJSONArray("web")
            if (arr != null && arr.length() > 0) return (0 until arr.length()).joinToString("\n\n") {
                val o = arr.getJSONObject(it)
                "${o.optString("title")}\n${o.optString("url")}\n${o.optString("description").take(300)}"
            }
        } catch (e: Exception) { /* cai para o DuckDuckGo */ }
        val req = Request.Builder().url("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8"))
            .header("User-Agent", ua).build()
        val html = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        val links = Regex("class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
        val snips = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
        if (links.isEmpty()) return "Nenhum resultado (a busca gratuita pode estar bloqueada; configure a chave do Firecrawl)."
        return links.take(6).mapIndexed { i, m ->
            var url = m.groupValues[1]
            if (url.contains("uddg=")) url = URLDecoder.decode(url.substringAfter("uddg=").substringBefore("&"), "UTF-8")
            if (url.startsWith("//")) url = "https:$url"
            "${clean(m.groupValues[2])}\n$url\n${snips.getOrNull(i)?.let { clean(it.groupValues[1]).take(300) }.orEmpty()}"
        }.joinToString("\n\n")
    }

    private fun fetch(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "URL inválida."
        if (firecrawlKey.isNotBlank()) try {
            val root = firecrawl("/v1/scrape", JSONObject().put("url", url).put("formats", org.json.JSONArray().put("markdown")))
            val md = root.optJSONObject("data")?.optString("markdown").orEmpty()
            if (md.isNotBlank()) return md.take(7000)
        } catch (e: Exception) { /* cai para a leitura direta */ }
        val req = Request.Builder().url(url).header("User-Agent", ua).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return "HTTP ${r.code}"
            val ins = r.body?.byteStream() ?: return "Página vazia."
            val buf = java.io.ByteArrayOutputStream()
            val b = ByteArray(8192)
            while (buf.size() < 400_000) { val n = ins.read(b); if (n < 0) break; buf.write(b, 0, n) }
            var h = buf.toString("UTF-8")
            h = h.replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ").replace(Regex("(?s)<!--.*?-->"), " ")
                .replace(Regex("(?i)</?(p|div|br|li|h[1-6]|tr)[^>]*>"), "\n")
            return h.let { clean(it.replace("\n", " ¶ ")).replace(" ¶ ", "\n") }.take(7000)
        }
    }
}
