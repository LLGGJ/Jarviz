package com.jarvis.app

import android.os.SystemClock
import okhttp3.ConnectionPool
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.TreeMap
import java.util.concurrent.TimeUnit

data class Call(val id: String, val name: String, val args: JSONObject)
data class Reply(val text: String, val calls: List<Call>, val raw: Any)

/** Quanto esperar o 1º byte do servidor. Com fallback ligado espera menos, para passar logo ao próximo provedor. */
@Volatile private var headerMs = 45_000L

/** Linha do tempo de uma chamada + vigia: se o servidor não responde a tempo, cancela para tentar de novo / trocar de provedor. */
class Trace : EventListener() {
    private var t0 = 0L
    private val sb = StringBuilder()
    @Volatile var gotHeaders = false
    @Volatile var watchdogFired = false
    @Volatile private var ended = false
    private var wd: Thread? = null
    private fun mark(s: String) { sb.append(s).append(' ').append(SystemClock.elapsedRealtime() - t0).append("ms; ") }
    fun text() = sb.toString().trim()
    override fun callStart(call: okhttp3.Call) {
        t0 = SystemClock.elapsedRealtime()
        val wait = headerMs
        wd = Thread {
            try { Thread.sleep(wait) } catch (e: InterruptedException) { return@Thread }
            if (!gotHeaders && !ended) { watchdogFired = true; call.cancel() }
        }.also { it.isDaemon = true; it.start() }
    }
    override fun dnsEnd(call: okhttp3.Call, domainName: String, inetAddressList: List<InetAddress>) { mark("dns") }
    override fun connectEnd(call: okhttp3.Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) { mark("conexão") }
    override fun secureConnectEnd(call: okhttp3.Call, handshake: Handshake?) { mark("tls") }
    override fun requestBodyEnd(call: okhttp3.Call, byteCount: Long) { mark("enviado") }
    override fun responseHeadersStart(call: okhttp3.Call) { gotHeaders = true; mark("1º byte"); wd?.interrupt() }
    override fun callEnd(call: okhttp3.Call) { ended = true; mark("fim"); wd?.interrupt() }
    override fun callFailed(call: okhttp3.Call, ioe: IOException) { ended = true; mark("falhou(${ioe.javaClass.simpleName})"); wd?.interrupt() }
}

private class Acc(var id: String, var name: String, val args: StringBuilder, var extra: JSONObject?)

/**
 * Cliente de IA com streaming (SSE): Anthropic Messages (Claude) ou OpenAI chat/completions (GPT, Gemini, Groq, DeepSeek...).
 * Fallback automático: se o modelo escolhido não responde, tenta outros modelos do mesmo provedor e depois os outros
 * provedores que tenham chave cadastrada.
 */
class AiClient {
    @Volatile private var trace = Trace()
    @Volatile private var cancelled = false
    @Volatile private var singleShot = false
    var onStage: (String) -> Unit = {}
    private val json = "application/json".toMediaType()
    // provedor ou "provedor/modelo" -> instante (elapsedRealtime) até o qual ele fica de molho depois de falhar
    private val down = HashMap<String, Long>()
    // Sem reaproveitar conexões: conexões "paradas" que a rede derrubou em silêncio causavam esperas de minutos.
    private val http = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .eventListenerFactory { Trace().also { trace = it } }
        .build()

    fun cancelAll() { cancelled = true; http.dispatcher.cancelAll() }
    fun traceText(): String = trace.text()

    private fun httpCode(m: String): Int? = Regex("^HTTP (\\d{3})").find(m)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Uma conversa com a IA. Escolhe modelo pequeno ou grande conforme o pedido e, se a IA pedir ferramentas de tela
     * (screen_*), executa no próprio celular e continua sozinha até terminar.
     */
    fun chat(cfg: Config, system: String, messages: JSONArray, tools: JSONArray, onDelta: (String) -> Unit): Reply {
        cancelled = false
        val heavy = isHeavy(messages, tools)
        HomeControl.sync(cfg)
        VideoEditor.jamendoId = cfg.jamendoKey.trim()
        val userText = lastUserText(messages)
        var preNote = ""
        if (Regex("(?i)((editar|edita|editando|montar|monta|fazer|faz|criar|cria).{0,24}v[ií]deo|editor de v[ií]deo|timeline)").containsMatchIn(userText) && !VideoEditor.open) {
            VideoEditor.run("video_open", JSONObject())
            preNote = "\n\nO editor de vídeo JÁ FOI ABERTO na tela do usuário agora; não chame video_open de novo. Diga que ele pode tocar em \"+ Mídia\" para escolher vídeos, fotos e música, e pergunte o estilo que ele quer."
        }
        val useScreen = cfg.screen && ScreenControl.ready()
        val useVideo = VideoEditor.wanted(userText)
        val useHome = HomeControl.homeReady()
        val usePc = HomeControl.pcReady()
        val useApp = AppActions.wanted(userText)
        val useSess = Sessions.wanted(userText) && cfg.saveChats
        val extra = JSONArray()
        if (useScreen) ScreenControl.toolDefs().forEach { extra.put(it) }
        if (useVideo) VideoEditor.toolDefs().forEach { extra.put(it) }
        if (useHome) HomeControl.homeDefs().forEach { extra.put(it) }
        if (usePc) HomeControl.pcDefs().forEach { extra.put(it) }
        if (useApp) AppActions.defs().forEach { extra.put(it) }
        if (useSess) Sessions.defs().forEach { extra.put(it) }
        val augmented = extra.length() > 0
        DebugLog.add("pedido: \"${userText.take(70)}\" | grande=$heavy | ferramentas: tela=$useScreen video=$useVideo casa=$useHome pc=$usePc app=$useApp | total=${extra.length()}")
        val allTools = if (augmented) JSONArray(tools.toString()).also { arr -> for (k in 0 until extra.length()) arr.put(extra.get(k)) } else tools
        val sys = system + (if (useScreen) ScreenControl.PROMPT else "") + (if (useVideo) VideoEditor.PROMPT + MotionStudio.PROMPT else "") +
            (if (useHome) HomeControl.HOME_PROMPT else "") + (if (usePc) HomeControl.PC_PROMPT else "") + Skills.forRequest(userText) +
            Skills.VOICE_NOTE + Skills.RULES + (if (useApp) Skills.keysSkill() else "") + preNote
        val work = if (augmented) JSONArray(messages.toString()) else messages
        val owns: (String) -> Boolean = { n ->
            (useScreen && n.startsWith("screen_")) || (useVideo && n.startsWith("video_")) ||
                (useHome && n.startsWith("home_")) || (usePc && n.startsWith("pc_")) || (useApp && n.startsWith("app_")) || (useSess && n.startsWith("session_"))
        }
        var steps = 0
        while (true) {
            if (cancelled) throw IOException("Cancelado")
            val r = chatStep(cfg, heavy, sys, work, allTools, onDelta)
            DebugLog.add("IA: ${r.calls.size} chamadas [${r.calls.joinToString { it.name }}] texto=\"${r.text.take(90)}\"")
            val own = augmented && r.calls.isNotEmpty() && steps < 40 && r.calls.all { owns(it.name) }
            if (!own) return r
            steps++
            appendAssistant(cfg.api, work, r)
            val outs = r.calls.map { c ->
                onStage("Executando ${c.name}...")
                val res = when {
                    c.name.startsWith("video_") -> VideoEditor.run(c.name, c.args)
                    c.name.startsWith("home_") -> HomeControl.runHome(c.name, c.args)
                    c.name.startsWith("pc_") -> HomeControl.runPc(c.name, c.args)
                    c.name.startsWith("app_") -> AppActions.run(c.name, c.args)
                    c.name.startsWith("session_") -> Sessions.run(c.name, c.args)
                    else -> ScreenControl.run(c.name, c.args)
                }
                DebugLog.add("${c.name} -> ${res.take(110)}")
                c to res
            }
            appendResults(cfg.api, work, outs)
        }
    }

    private fun chatStep(cfg: Config, heavy: Boolean, system: String, messages: JSONArray, tools: JSONArray, onDelta: (String) -> Unit): Reply {
        val now0 = SystemClock.elapsedRealtime()
        val all = cfg.routeChain(heavy)
        val ready = all.filter { (down[it.provider] ?: 0L) <= now0 && (down[it.provider + "/" + it.model] ?: 0L) <= now0 }
        val chain = if (ready.isEmpty()) all else ready
        singleShot = chain.size > 1
        headerMs = if (chain.size > 1) 30_000L else 45_000L
        val errors = mutableListOf<String>()
        val bad = mutableSetOf<String>()
        var first: Exception? = null
        var emitted = false
        val dl: (String) -> Unit = { emitted = true; onDelta(it) }
        for (cand in chain) {
            if (cand.provider in bad) continue
            onStage(if (errors.isEmpty()) "Enviando para ${cand.model}..." else "Tentando ${providerLabel(cand.provider)} · ${cand.model}...")
            DebugLog.add("tentando ${cand.provider} · ${cand.model}")
            try {
                val msgs = if (cand.api == cfg.api) messages else convertMessages(messages, cfg.api, cand.api)
                val r = chatOnce(cand, system, msgs, tools, dl)
                return if (cand.api == cfg.api) r else convertReply(r, cfg.api)
            } catch (e: Exception) {
                if (cancelled || emitted) throw e
                if (first == null) first = e
                DebugLog.add("falhou: ${e.message?.take(140)}")
                val m = e.message.orEmpty()
                errors.add("${providerLabel(cand.provider)} · ${cand.model}: ${m.take(140)}")
                val code = httpCode(m)
                val until = SystemClock.elapsedRealtime()
                if (code == 404) down[cand.provider + "/" + cand.model] = until + 600_000L
                else if (code == 400) { /* pedido recusado: tenta o próximo modelo sem castigar o provedor */ }
                else if (code == null || code == 401 || code == 402 || code == 403) { down[cand.provider] = until + 120_000L; bad.add(cand.provider) }
                else down[cand.provider + "/" + cand.model] = until + 60_000L
            }
        }
        if (errors.size <= 1) throw first ?: IllegalStateException("Falha ao chamar a IA")
        throw IllegalStateException("Nenhum modelo respondeu:\n" + errors.joinToString("\n"))
    }

    private fun chatOnce(cfg: Config, system: String, messages: JSONArray, tools: JSONArray, onDelta: (String) -> Unit): Reply =
        if (cfg.api == "anthropic") anthropic(cfg, system, messages, tools, onDelta) else openai(cfg, system, messages, tools, onDelta)

    // ---------- HTTP ----------

    /** POST. Se vier SSE, entrega cada linha em onLine e devolve null; se vier JSON comum, devolve o objeto. */
    private fun request(url: String, headers: Map<String, String>, body: JSONObject, onLine: (String) -> Unit): JSONObject? {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return requestOnce(url, headers, body, onLine)
            } catch (e: IOException) {
                val retry = attempt == 1 && !singleShot && !cancelled && !trace.gotHeaders && e !is UnknownHostException
                if (!retry) throw if (trace.watchdogFired) IOException("O servidor não respondeu em ${headerMs / 1000}s (tentei $attempt vez(es))") else e
                onStage("Sem resposta; tentando de novo numa conexão nova...")
            }
        }
    }

    private fun requestOnce(url: String, headers: Map<String, String>, body: JSONObject, onLine: (String) -> Unit): JSONObject? {
        val b = Request.Builder().url(url).post(body.toString().toRequestBody(json)).header("Accept", "application/json, text/event-stream")
        headers.forEach { (k, v) -> b.header(k, v) }
        http.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}: ${r.body?.string().orEmpty().take(300)}")
            if (r.header("Content-Type").orEmpty().contains("text/event-stream")) {
                val src = r.body!!.source()
                while (true) { val line = src.readUtf8Line() ?: break; onLine(line) }
                return null
            }
            return JSONObject(r.body!!.string())
        }
    }

    // ---------- Anthropic (Claude) ----------

    private fun anthropic(cfg: Config, system: String, messages: JSONArray, tools: JSONArray, onDelta: (String) -> Unit): Reply {
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
            .put("max_tokens", if (think) budget + 4096 else 4096).put("stream", true)
            .put("system", system).put("messages", messages)
            .also {
                if (at.length() > 0) it.put("tools", at)
                if (think) it.put("thinking", JSONObject().put("type", "enabled").put("budget_tokens", budget))
            }
        val h = anthropicHeaders(cfg)
        val think0 = cfg.effort != "none"
        return try {
            runAnthropic(url, h, body(think0), onDelta)
        } catch (e: IllegalStateException) {
            if (think0 && e.message?.startsWith("HTTP 400") == true) runAnthropic(url, h, body(false), onDelta) else throw e
        }
    }

    private fun runAnthropic(url: String, h: Map<String, String>, body: JSONObject, onDelta: (String) -> Unit): Reply {
        val tb = HashMap<Int, StringBuilder>(); val thb = HashMap<Int, StringBuilder>()
        val sg = HashMap<Int, StringBuilder>(); val jb = HashMap<Int, StringBuilder>()
        val blocks = TreeMap<Int, JSONObject>()
        fun finish(i: Int) {
            val b = blocks[i] ?: return
            when (b.optString("type")) {
                "text" -> b.put("text", tb[i]?.toString().orEmpty())
                "thinking" -> { b.put("thinking", thb[i]?.toString().orEmpty()); sg[i]?.let { b.put("signature", it.toString()) } }
                "tool_use" -> {
                    val s = jb[i]?.toString().orEmpty()
                    b.put("input", if (s.isBlank()) JSONObject() else try { JSONObject(s) } catch (e: Exception) { JSONObject() })
                }
            }
        }
        val full = request(url, h, body) { line ->
            if (!line.startsWith("data:")) return@request
            val d = line.substring(5).trim()
            if (d.isEmpty()) return@request
            val ev = JSONObject(d)
            when (ev.optString("type")) {
                "content_block_start" -> {
                    val i = ev.getInt("index")
                    val b = JSONObject(ev.getJSONObject("content_block").toString())
                    blocks[i] = b
                    when (b.optString("type")) {
                        "text" -> tb[i] = StringBuilder(b.optString("text"))
                        "thinking" -> { thb[i] = StringBuilder(b.optString("thinking")); sg[i] = StringBuilder(b.optString("signature")) }
                        "tool_use" -> jb[i] = StringBuilder()
                    }
                }
                "content_block_delta" -> {
                    val i = ev.getInt("index"); val dl = ev.getJSONObject("delta")
                    when (dl.optString("type")) {
                        "text_delta" -> { val s = dl.optString("text"); tb.getOrPut(i) { StringBuilder() }.append(s); onDelta(s) }
                        "thinking_delta" -> thb.getOrPut(i) { StringBuilder() }.append(dl.optString("thinking"))
                        "signature_delta" -> sg.getOrPut(i) { StringBuilder() }.append(dl.optString("signature"))
                        "input_json_delta" -> jb.getOrPut(i) { StringBuilder() }.append(dl.optString("partial_json"))
                    }
                }
                "content_block_stop" -> finish(ev.getInt("index"))
                "error" -> {
                    val m = ev.optJSONObject("error")
                    error("HTTP ${if (m?.optString("type") == "overloaded_error") 503 else 500}: ${m?.optString("message")}")
                }
            }
        }
        val content: JSONArray
        if (full != null) {
            content = full.getJSONArray("content")
            for (i in 0 until content.length()) if (content.getJSONObject(i).optString("type") == "text") onDelta(content.getJSONObject(i).optString("text"))
        } else {
            blocks.keys.forEach { finish(it) }
            content = JSONArray(blocks.values.toList())
        }
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

    // ---------- OpenAI-compatível (GPT, Gemini, Groq, DeepSeek, OpenRouter, Mistral, xAI...) ----------

    private fun openai(cfg: Config, system: String, messages: JSONArray, tools: JSONArray, onDelta: (String) -> Unit): Reply {
        val base = cfg.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/v1") || base.endsWith("/openai")) "$base/chat/completions" else "$base/v1/chat/completions"
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (i in 0 until messages.length()) msgs.put(messages.get(i))
        fun body(r: Boolean) = JSONObject().put("model", cfg.model).put("messages", msgs).put("stream", true).also {
            if (tools.length() > 0) it.put("tools", tools)
            if (r) it.put("reasoning_effort", if (cfg.effort == "none") "low" else cfg.effort)
        }
        val h = mapOf("Authorization" to "Bearer ${cfg.key()}")
        // reasoning_effort só nos provedores que aceitam; nos outros ele causaria um erro 400 e uma chamada repetida
        val useR = cfg.provider == "flat" || cfg.provider == "gemini" || cfg.provider == "openai"
        return try {
            runOpenAi(url, h, body(useR), onDelta)
        } catch (e: IllegalStateException) {
            if (useR && e.message?.startsWith("HTTP 400") == true) runOpenAi(url, h, body(false), onDelta) else throw e
        }
    }

    private fun runOpenAi(url: String, h: Map<String, String>, body: JSONObject, onDelta: (String) -> Unit): Reply {
        val content = StringBuilder()
        val accs = TreeMap<Int, Acc>()
        val full = request(url, h, body) { line ->
            if (!line.startsWith("data:")) return@request
            val d = line.substring(5).trim()
            if (d.isEmpty() || d == "[DONE]") return@request
            val ev = JSONObject(d)
            if (ev.has("error") && !ev.isNull("error")) error("HTTP 500: ${ev.get("error")}".take(300))
            val delta = ev.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: return@request
            if (!delta.isNull("content")) { val s = delta.optString("content"); if (s.isNotEmpty()) { content.append(s); onDelta(s) } }
            val tcs = delta.optJSONArray("tool_calls")
            if (tcs != null) for (k in 0 until tcs.length()) {
                val t = tcs.getJSONObject(k)
                val f = t.optJSONObject("function")
                val nm = f?.optString("name").orEmpty()
                val idx = if (t.has("index")) t.getInt("index") else if (nm.isEmpty()) (accs.size - 1).coerceAtLeast(0) else accs.size
                val a = accs.getOrPut(idx) { Acc("", "", StringBuilder(), null) }
                if (t.optString("id").isNotEmpty()) a.id = t.optString("id")
                if (nm.isNotEmpty()) a.name = nm
                a.args.append(f?.optString("arguments").orEmpty())
                t.optJSONObject("extra_content")?.let { a.extra = it }
            }
        }
        if (full != null) {
            val m = full.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val text = if (m.isNull("content")) "" else m.getString("content")
            if (text.isNotEmpty()) onDelta(text)
            val calls = mutableListOf<Call>()
            val tc = m.optJSONArray("tool_calls")
            if (tc != null) for (i in 0 until tc.length()) {
                val c = tc.getJSONObject(i); val f = c.getJSONObject("function")
                val args = try { JSONObject(f.optString("arguments", "{}")) } catch (e: Exception) { JSONObject() }
                calls.add(Call(c.optString("id").ifBlank { "call_$i" }, f.getString("name"), args))
            }
            return Reply(text, calls, m)
        }
        val calls = mutableListOf<Call>()
        val tcArr = JSONArray()
        accs.values.forEachIndexed { i, a ->
            val id = a.id.ifBlank { "call_$i" }
            val argsText = a.args.toString().ifBlank { "{}" }
            calls.add(Call(id, a.name, try { JSONObject(argsText) } catch (e: Exception) { JSONObject() }))
            val o = JSONObject().put("id", id).put("type", "function")
                .put("function", JSONObject().put("name", a.name).put("arguments", argsText))
            a.extra?.let { o.put("extra_content", it) }
            tcArr.put(o)
        }
        val msg = JSONObject().put("role", "assistant").put("content", if (content.isEmpty()) JSONObject.NULL else content.toString())
        if (tcArr.length() > 0) msg.put("tool_calls", tcArr)
        return Reply(content.toString(), calls, msg)
    }

    // ---------- teste, modelos e histórico ----------

    /** Pedido mínimo, sem ferramentas e sem streaming, para testar chave, modelo e rede (sem fallback: testa só o que está escolhido). */
    fun testConnection(cfg: Config): String {
        val t0 = SystemClock.elapsedRealtime()
        cancelled = false
        singleShot = false
        headerMs = 45_000L
        return try {
            val base = cfg.baseUrl.trimEnd('/')
            val ask = JSONArray().put(JSONObject().put("role", "user").put("content", "Responda apenas: ok"))
            val text: String
            if (cfg.api == "anthropic") {
                val url = if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages"
                val res = request(url, anthropicHeaders(cfg),
                    JSONObject().put("model", cfg.model).put("max_tokens", 16).put("messages", ask)) { } ?: error("Resposta inesperada")
                text = res.getJSONArray("content").getJSONObject(0).optString("text")
            } else {
                val url = if (base.endsWith("/v1") || base.endsWith("/openai")) "$base/chat/completions" else "$base/v1/chat/completions"
                val res = request(url, mapOf("Authorization" to "Bearer ${cfg.key()}"),
                    JSONObject().put("model", cfg.model).put("messages", ask)) { } ?: error("Resposta inesperada")
                text = res.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
            }
            "Conexão OK com ${providerLabel(cfg.provider)} · ${cfg.model} em ${SystemClock.elapsedRealtime() - t0} ms. Resposta: ${text.take(40)}\nTempos: ${traceText()}"
        } catch (e: Exception) {
            "Falhou com ${providerLabel(cfg.provider)} · ${cfg.model} após ${SystemClock.elapsedRealtime() - t0} ms: ${e.message}\nTempos: ${traceText()}"
        }
    }

    /** Lista os modelos liberados na chave (Gemini: models.list do AI Studio; os demais: GET /models no padrão OpenAI/Anthropic). */
    fun listModels(cfg: Config): List<String> {
        if (cfg.provider == "gemini") {
            val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000")
                .header("x-goog-api-key", cfg.key()).get().build()
            http.newCall(req).execute().use { r ->
                val t = r.body?.string().orEmpty()
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val arr = JSONObject(t).getJSONArray("models")
                val skip = listOf("tts", "image", "live", "embedding", "audio", "transcribe", "robotics", "computer-use", "omni", "imagen", "aqa", "banana", "nano", "veo", "lyria", "deep-research", "customtools")
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
        val rb = Request.Builder().url(url).get()
        if (cfg.provider == "anthropic") rb.header("x-api-key", cfg.key()).header("anthropic-version", "2023-06-01")
        else {
            rb.header("Authorization", "Bearer ${cfg.key()}")
            if (cfg.provider == "flat") rb.header("x-api-key", cfg.key())
        }
        http.newCall(rb.build()).execute().use { r ->
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
