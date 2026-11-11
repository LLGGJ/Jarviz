package com.jarvis.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class State { IDLE, LISTENING, THINKING, SPEAKING }
data class Msg(val role: String, val text: String)

private const val SYSTEM = "Você é o JARVIS, assistente pessoal de IA do usuário. Responda em português do Brasil, " +
    "de forma curta, direta e natural para ser falada em voz alta (sem markdown, sem listas longas). " +
    "Você tem ferramentas do GitHub do usuário: use-as quando ele pedir sobre repositórios, arquivos ou issues. " +
    "Pense com cuidado antes de responder."

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val ai = AiClient()
    private val voice = Voice(app)
    var cfg by mutableStateOf(prefs.load()); private set
    var state by mutableStateOf(State.IDLE); private set
    var partial by mutableStateOf(""); private set
    val msgs = mutableStateListOf<Msg>()
    private var mic = false
    private val convo = mutableListOf<Pair<String, String>>()
    private val clap = ClapDetector { listenNow() }

    private val tools = JSONArray()
        .put(tool("github_list_repos", "Lista os repositórios recentes do usuário no GitHub"))
        .put(tool("github_read_file", "Lê um arquivo de um repositório", "repo", "path"))
        .put(tool("github_list_issues", "Lista issues abertas de um repositório (owner/nome)", "repo"))
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

    /** Loop de agente: a IA pode chamar ferramentas do GitHub até 6 vezes antes de responder. */
    private fun runAgent(text: String): String {
        if (cfg.apiKey.isBlank() || cfg.model.isBlank()) return "Configure a API Key e o modelo no menu lateral."
        val native = JSONArray()
        convo.takeLast(20).forEach { (r, t) -> native.put(JSONObject().put("role", r).put("content", t)) }
        native.put(JSONObject().put("role", "user").put("content", text))
        val gh = GitHub(cfg.ghToken)
        return try {
            repeat(6) {
                val r = ai.chat(cfg, SYSTEM, native, tools)
                if (r.calls.isEmpty()) {
                    convo.add("user" to text); convo.add("assistant" to r.text)
                    return r.text
                }
                ai.appendAssistant(cfg.api, native, r)
                val outs = r.calls.map { c ->
                    c to (if (cfg.ghToken.isBlank()) "Token do GitHub não configurado." else gh.run(c.name, c.args))
                }
                ai.appendResults(cfg.api, native, outs)
            }
            "Não consegui concluir a tarefa."
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }

    override fun onCleared() { clap.stop(); voice.release() }
}
