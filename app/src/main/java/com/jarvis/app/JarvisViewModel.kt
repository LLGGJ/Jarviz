package com.jarvis.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class State { IDLE, LISTENING, THINKING, SPEAKING }
data class Msg(val role: String, val text: String)
class Pending(val title: String, val detail: String, val result: CompletableDeferred<Boolean>)

private const val SYSTEM = "Você é o JARVIS, assistente pessoal de IA do usuário. Responda em português do Brasil, " +
    "de forma curta, direta e natural para ser falada em voz alta (sem markdown, sem listas longas). " +
    "Você controla o GitHub do usuário pela ferramenta github_api, que chama qualquer endpoint da API REST " +
    "(repositórios, arquivos via /contents, issues, pull requests, branches, workflows etc.). " +
    "Leituras são livres. Ações que escrevem (POST, PUT, PATCH, DELETE) pedem permissão ao usuário " +
    "automaticamente: apenas chame a ferramenta. Pense com cuidado antes de responder."

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val ai = AiClient()
    private val voice = Voice(app)
    var cfg by mutableStateOf(prefs.load()); private set
    var state by mutableStateOf(State.IDLE); private set
    var partial by mutableStateOf(""); private set
    var pending by mutableStateOf<Pending?>(null); private set
    var modelsMsg by mutableStateOf(""); private set
    val msgs = mutableStateListOf<Msg>()
    private var mic = false
    private val convo = mutableListOf<Pair<String, String>>()
    private val clap = ClapDetector { listenNow() }

    private val tools = JSONArray()
        .put(tool("github_api", "Chama qualquer endpoint da API REST do GitHub. method: GET/POST/PUT/PATCH/DELETE. " +
            "path: ex /user/repos ou /repos/dono/repo/contents/arquivo. body: JSON em texto (vazio se não tiver).", "method", "path", "body"))
        .put(tool("github_list_repos", "Lista os repositórios recentes do usuário no GitHub"))
        .put(tool("github_read_file", "Lê um arquivo de um repositório (repo = dono/nome)", "repo", "path"))
        .put(tool("github_list_issues", "Lista issues abertas de um repositório (dono/nome)", "repo"))
        .put(tool("github_create_issue", "Cria uma issue em um repositório", "repo", "title", "body"))

    private fun tool(name: String, desc: String, vararg p: String): JSONObject {
        val props = JSONObject()
        p.forEach { props.put(it, JSONObject().put("type", "string")) }
        return JSONObject().put("type", "function").put(
            "function", JSONObject().put("name", name).put("description", desc).put(
                "parameters", JSONObject().put("type", "object").put("properties", props)
                    .put("required", JSONArray(p.toList()))
            )
        )
    }

    fun updateCfg(c: Config) {
        cfg = c; prefs.save(c)
        if (c.clap) resumeClap() else clap.stop()
    }

    fun onMic(granted: Boolean) { mic = granted; resumeClap() }
    fun resumeClap() { if (cfg.clap && mic && state == State.IDLE) clap.start() }
    fun pause() { clap.stop() }

    fun decide(ok: Boolean) { pending?.result?.complete(ok); pending = null }

    fun availableModels(): List<String> =
        cfg.models.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { DEFAULT_MODELS }

    fun refreshModels() {
        modelsMsg = "Buscando..."
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { ai.listModels(cfg) } }
            r.onSuccess { ids ->
                if (ids.isEmpty()) modelsMsg = "Nenhum modelo retornado"
                else { updateCfg(cfg.copy(models = ids.joinToString(","))); modelsMsg = "${ids.size} modelos no seu plano" }
            }.onFailure { modelsMsg = "Não consegui listar (${it.message}). Ele aprende pelos erros ao conversar." }
        }
    }

    fun listenNow() {
        if (state == State.THINKING) return
        voice.stopSpeaking(); clap.stop()
        state = State.LISTENING; partial = ""
        voice.listen({ partial = it }) { r ->
            partial = ""
            if (r.isNullOrBlank()) { state = State.IDLE; resumeClap() } else send(r)
        }
    }

    fun send(text: String) {
        if (text.isBlank() || state == State.THINKING) return
        clap.stop()
        msgs.add(Msg("user", text)); state = State.THINKING
        viewModelScope.launch {
            val reply = withContext(Dispatchers.IO) { runAgent(text) }
            msgs.add(Msg("assistant", reply))
            if (cfg.speak) {
                state = State.SPEAKING
                voice.speak(reply.replace(Regex("[*#`_>]"), "")) { state = State.IDLE; resumeClap() }
            } else { state = State.IDLE; resumeClap() }
        }
    }

    private fun note(text: String) { viewModelScope.launch(Dispatchers.Main) { msgs.add(Msg("assistant", text)) } }

    private fun askApproval(detail: String): Boolean {
        val d = CompletableDeferred<Boolean>()
        viewModelScope.launch(Dispatchers.Main) { pending = Pending("PERMISSÃO NECESSÁRIA", detail, d) }
        return runBlocking { d.await() }
    }

    private fun execTool(gh: GitHub, c: Call): String {
        if (cfg.ghToken.isBlank()) return "Token do GitHub (ghp_...) não configurado."
        val write = c.name == "github_create_issue" ||
            (c.name == "github_api" && !c.args.optString("method", "GET").equals("GET", true))
        if (write) {
            val detail = if (c.name == "github_api")
                "${c.args.optString("method").uppercase()} ${c.args.optString("path")}\n${c.args.optString("body").take(500)}"
            else "Criar issue em ${c.args.optString("repo")}: ${c.args.optString("title")}"
            if (!askApproval(detail)) return "O usuário negou a permissão."
        }
        return gh.run(c.name, c.args)
    }

    /** Chama a IA; se o modelo estiver fora do ar (503) ou não existir no plano (404), tenta outro do plano. */
    private fun chatSmart(start: Config, native: JSONArray, plainLen: Int): Pair<Reply, Config> {
        var c = start
        val tried = mutableSetOf(c.model)
        var avail = availableModels()
        var last: Exception? = null
        repeat(4) {
            try {
                return ai.chat(c, SYSTEM, native, tools) to c
            } catch (e: IllegalStateException) {
                last = e
                val m = e.message.orEmpty()
                Regex("Models on your plan: ([^\"]+)").find(m)?.let { f ->
                    val ids = f.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (ids.isNotEmpty()) {
                        avail = ids
                        viewModelScope.launch(Dispatchers.Main) { updateCfg(cfg.copy(models = ids.joinToString(","))) }
                    }
                }
                val retry = (m.startsWith("HTTP 404") || m.startsWith("HTTP 503")) && native.length() == plainLen
                if (!retry) throw e
                val next = avail.firstOrNull { it !in tried } ?: throw e
                tried += next
                val api = apiFor(next)
                c = c.copy(model = next, api = api, baseUrl = baseFor(api))
                note("O modelo ${tried.first()} não está disponível agora. Usando $next.")
            }
        }
        throw last ?: IllegalStateException("Falha ao chamar a IA")
    }

    private fun runAgent(text: String): String {
        if (cfg.apiKey.isBlank() || cfg.model.isBlank()) return "Configure a API Key e o modelo no menu lateral."
        val native = JSONArray()
        convo.takeLast(20).forEach { (r, t) -> native.put(JSONObject().put("role", r).put("content", t)) }
        native.put(JSONObject().put("role", "user").put("content", text))
        val plainLen = native.length()
        var c = cfg
        val gh = GitHub(cfg.ghToken)
        return try {
            repeat(8) {
                val (r, used) = chatSmart(c, native, plainLen)
                c = used
                if (r.calls.isEmpty()) {
                    convo.add("user" to text); convo.add("assistant" to r.text)
                    return r.text.ifBlank { "Pronto." }
                }
                ai.appendAssistant(c.api, native, r)
                val outs = r.calls.map { call -> call to execTool(gh, call) }
                ai.appendResults(c.api, native, outs)
            }
            "Não consegui concluir a tarefa."
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }

    override fun onCleared() { clap.stop(); voice.release() }
}
