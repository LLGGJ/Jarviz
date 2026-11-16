package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject

/** FlatAI aceita x-api-key e Bearer; a Anthropic oficial só quer x-api-key. */
fun anthropicHeaders(cfg: Config): Map<String, String> =
    if (cfg.provider == "anthropic") mapOf("x-api-key" to cfg.key(), "anthropic-version" to "2023-06-01")
    else mapOf("x-api-key" to cfg.key(), "Authorization" to "Bearer ${cfg.key()}", "anthropic-version" to "2023-06-01")

/** Converte o histórico (inclusive chamadas de ferramentas) de um formato para o outro, para trocar de provedor no meio da tarefa. */
fun convertMessages(src: JSONArray, from: String, to: String): JSONArray =
    if (from == to) src else if (to == "openai") anthropicToOpenAi(src) else openAiToAnthropic(src)

private fun anthropicToOpenAi(src: JSONArray): JSONArray {
    val out = JSONArray()
    for (i in 0 until src.length()) {
        val m = src.getJSONObject(i)
        val role = m.optString("role")
        val c = m.opt("content")
        if (c !is JSONArray) { out.put(m); continue }
        if (role == "assistant") {
            val text = StringBuilder()
            val tcs = JSONArray()
            for (k in 0 until c.length()) {
                val b = c.getJSONObject(k)
                when (b.optString("type")) {
                    "text" -> text.append(b.optString("text"))
                    "tool_use" -> tcs.put(
                        JSONObject().put("id", b.optString("id")).put("type", "function")
                            .put("function", JSONObject().put("name", b.optString("name"))
                                .put("arguments", (b.optJSONObject("input") ?: JSONObject()).toString()))
                    )
                }
            }
            val o = JSONObject().put("role", "assistant").put("content", if (text.isEmpty()) JSONObject.NULL else text.toString())
            if (tcs.length() > 0) o.put("tool_calls", tcs)
            out.put(o)
        } else {
            val parts = JSONArray()
            for (k in 0 until c.length()) {
                val b = c.getJSONObject(k)
                when (b.optString("type")) {
                    "tool_result" -> out.put(
                        JSONObject().put("role", "tool").put("tool_call_id", b.optString("tool_use_id")).put("content", b.optString("content"))
                    )
                    "text" -> parts.put(b)
                    "image" -> {
                        val s = b.optJSONObject("source")
                        if (s != null) parts.put(
                            JSONObject().put("type", "image_url")
                                .put("image_url", JSONObject().put("url", "data:${s.optString("media_type")};base64,${s.optString("data")}"))
                        )
                    }
                }
            }
            if (parts.length() > 0) out.put(JSONObject().put("role", role).put("content", parts))
        }
    }
    return out
}

private fun openAiToAnthropic(src: JSONArray): JSONArray {
    val out = JSONArray()
    var i = 0
    while (i < src.length()) {
        val m = src.getJSONObject(i)
        val role = m.optString("role")
        if (role == "tool") {
            val blocks = JSONArray()
            while (i < src.length() && src.getJSONObject(i).optString("role") == "tool") {
                val t = src.getJSONObject(i)
                blocks.put(JSONObject().put("type", "tool_result").put("tool_use_id", t.optString("tool_call_id")).put("content", t.optString("content")))
                i++
            }
            out.put(JSONObject().put("role", "user").put("content", blocks))
            continue
        }
        if (role == "assistant") {
            val blocks = JSONArray()
            val text = if (m.isNull("content")) "" else m.optString("content")
            if (text.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", text))
            val tcs = m.optJSONArray("tool_calls")
            if (tcs != null) for (k in 0 until tcs.length()) {
                val c = tcs.getJSONObject(k)
                val f = c.getJSONObject("function")
                val input = try { JSONObject(f.optString("arguments", "{}")) } catch (e: Exception) { JSONObject() }
                blocks.put(JSONObject().put("type", "tool_use").put("id", c.optString("id")).put("name", f.optString("name")).put("input", input))
            }
            if (blocks.length() == 0) blocks.put(JSONObject().put("type", "text").put("text", "."))
            out.put(JSONObject().put("role", "assistant").put("content", blocks))
        } else {
            val c = m.opt("content")
            if (c is JSONArray) {
                val parts = JSONArray()
                for (k in 0 until c.length()) {
                    val b = c.getJSONObject(k)
                    if (b.optString("type") == "image_url") {
                        val url = b.optJSONObject("image_url")?.optString("url").orEmpty()
                        val mime = url.substringAfter("data:", "").substringBefore(";")
                        val data = url.substringAfter("base64,", "")
                        if (data.isNotEmpty()) parts.put(
                            JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", mime).put("data", data))
                        )
                    } else parts.put(b)
                }
                out.put(JSONObject().put("role", "user").put("content", parts))
            } else out.put(m)
        }
        i++
    }
    return out
}

/** Converte a resposta de um provedor para o formato que o resto do app espera (o do provedor principal). */
fun convertReply(r: Reply, target: String): Reply {
    if (target == "anthropic") {
        val blocks = JSONArray()
        if (r.text.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", r.text))
        r.calls.forEach { blocks.put(JSONObject().put("type", "tool_use").put("id", it.id).put("name", it.name).put("input", it.args)) }
        if (blocks.length() == 0) blocks.put(JSONObject().put("type", "text").put("text", "."))
        return Reply(r.text, r.calls, blocks)
    }
    val tcs = JSONArray()
    r.calls.forEach {
        tcs.put(JSONObject().put("id", it.id).put("type", "function")
            .put("function", JSONObject().put("name", it.name).put("arguments", it.args.toString())))
    }
    val msg = JSONObject().put("role", "assistant").put("content", if (r.text.isEmpty()) JSONObject.NULL else r.text)
    if (tcs.length() > 0) msg.put("tool_calls", tcs)
    return Reply(r.text, r.calls, msg)
}
