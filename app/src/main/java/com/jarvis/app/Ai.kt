package com.jarvis.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Call(val id: String, val name: String, val args: JSONObject)
data class Reply(val text: String, val calls: List<Call>, val raw: Any)

/** Cliente da FlatAI: fala Anthropic Messages (Claude) ou OpenAI chat/completions (GPT, GLM). */
class AiClient {
    private val http = OkHttpClient.Builder().readTimeout(180, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    fun chat(cfg: Config, system: String, messages: JSONArray, tools: JSONArray): Reply =
        if (cfg.api == "anthropic") anthropic(cfg, system, messages, tools) else openai(cfg, system, messages, tools)

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val b = Request.Builder().url(url).post(body.toString().toRequestBody(json))
        headers.forEach { (k, v) -> b.header(k, v) }
        http.newCall(b.build()).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("HTTP ${r.code}: ${t.take(300)}")
            return JSONObject(t)
        }
    }

    private fun anthropic(cfg: Config, system: String, messages: JSONArray, tools: JSONArray): Reply {
        val base = cfg.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages"
        val budget = when (cfg.effort) { "low" -> 1024; "high" -> 10000; else -> 4096 }
        val at = JSONArray()
        for (i in 0 until tools.length()) {
            val f = tools.getJSONObject(i).getJSONObject("function")
            at.put(JSONObject().put("name", f.getString("name")).put("description", f.getString("description"))
                .put("input_schema", f.optJSONObject("parameters") ?: JSONObject().put("type", "object").put("properties", JSONObject())))
        }
        fun body(think: Boolean) = JSONObject().put("model", cfg.model)
            .put("max_tokens", if (think) budget + 4096 else 4096)
            .put("system", system).put("messages", messages).put("tools", at)
            .also { if (think) it.put("thinking", JSONObject().put("type", "enabled").put("budget_tokens", budget)) }
        val h = mapOf("x-api-key" to cfg.key(), "Authorization" to "Bearer ${cfg.key()}", "anthropic-version" to "2023-06-01")
        val res = try { post(url, h, body(true)) } catch (e: IllegalStateException) {
            if (e.message?.startsWith("HTTP 400") == true) post(url, h, body(false)) else throw e
        }
        val content = res.getJSONArray("content")
        val sb = StringBuilder(); val calls = mutableListOf<Call>()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            when (b.optString("type")) {
                "text" -> sb.append(b.optString("text"))
                "tool_use" -> calls.add(Call(b.getString("id"), b.getString("name"), b.optJSONObject("input") ?: JSONObject()))
            }
        }
        return Reply(sb.toString(), calls, content)
    }

    private fun openai(cfg: Config, system: String, messages: JSONArray, tools: JSONArray): Reply {
        val base = cfg.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/v1") || base.endsWith("/openai")) "$base/chat/completions" else "$base/v1/chat/completions"
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (i in 0 until messages.length()) msgs.put(messages.get(i))
        fun body(r: Boolean) = JSONObject().put("model", cfg.model).put("messages", msgs).put("tools", tools)
            .also { if (r) it.put("reasoning_effort", cfg.effort) }
        val h = mapOf("Authorization" to "Bearer ${cfg.key()}")
        val res = try { post(url, h, body(true)) } catch (e: IllegalStateException) {
            if (e.message?.startsWith("HTTP 400") == true) post(url, h, body(false)) else throw e
        }
        val m = res.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val calls = mutableListOf<Call>()
        val tc = m.optJSONArray("tool_calls")
        if (tc != null) for (i in 0 until tc.length()) {
            val c = tc.getJSONObject(i); val f = c.getJSONObject("function")
            val args = try { JSONObject(f.optString("arguments", "{}")) } catch (e: Exception) { JSONObject() }
            calls.add(Call(c.optString("id").ifBlank { "call_$i" }, f.getString("name"), args))
        }
        return Reply(if (m.isNull("content")) "" else m.getString("content"), calls, m)
    }

    /** Lista os modelos liberados no plano (GET /v1/models). */
    fun listModels(cfg: Config): List<String> {
        if (cfg.provider == "gemini") {
            val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000")
                .header("x-goog-api-key", cfg.key()).get().build()
            http.newCall(req).execute().use { r ->
                val t = r.body?.string().orEmpty()
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val arr = JSONObject(t).getJSONArray("models")
                val skip = listOf("tts", "image", "live", "embedding", "audio", "transcribe", "robotics", "computer-use", "omni", "imagen", "aqa")
                return (0 until arr.length()).map { arr.getJSONObject(it) }.filter { o ->
                    val methods = o.optJSONArray("supportedGenerationMethods")
                    val name = o.getString("name")
                    name.startsWith("models/gemini") && skip.none { name.contains(it) } &&
                        methods != null && (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                }.map { it.getString("name").removePrefix("models/") }.sortedDescending()
            }
        }
        val base = cfg.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
        val req = Request.Builder().url(url).get()
            .header("Authorization", "Bearer ${cfg.key()}").header("x-api-key", cfg.key()).build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val arr = JSONObject(t).getJSONArray("data")
            return (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
        }
    }

    fun appendAssistant(api: String, native: JSONArray, r: Reply) {
        if (api == "anthropic") native.put(JSONObject().put("role", "assistant").put("content", r.raw as JSONArray))
        else native.put(r.raw as JSONObject)
    }

    fun appendResults(api: String, native: JSONArray, outs: List<Pair<Call, String>>) {
        if (api == "anthropic") {
            val blocks = JSONArray()
            outs.forEach { (c, o) ->
                blocks.put(JSONObject().put("type", "tool_result").put("tool_use_id", c.id).put("content", o))
            }
            native.put(JSONObject().put("role", "user").put("content", blocks))
        } else outs.forEach { (c, o) ->
            native.put(JSONObject().put("role", "tool").put("tool_call_id", c.id).put("name", c.name).put("content", o))
        }
    }
}
