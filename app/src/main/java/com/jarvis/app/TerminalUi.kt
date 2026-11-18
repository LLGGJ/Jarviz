package com.jarvis.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object TermUi {
    var open by mutableStateOf(false)
}

/** Terminal do Jarvis: usa o Termux quando está pronto e, senão, o shell do Android. Lembra a pasta atual entre os comandos. */
@Composable
fun TerminalScreen() {
    val ctx = LocalContext.current
    BackHandler(enabled = true) { TermUi.open = false }
    val lines = remember { mutableStateListOf("Terminal do Jarvis. Usa o Termux se estiver pronto, senão o shell do Android.") }
    var input by remember { mutableStateOf("") }
    var cwd by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.size - 1) }

    fun runCmd() {
        val cmd = input.trim()
        if (cmd.isEmpty() || busy) return
        input = ""
        lines.add("$ $cmd")
        busy = true
        Thread {
            val dir = cwd.ifBlank { ctx.filesDir.absolutePath }
            val raw = TerminalTools(ctx).run("cd '$dir' 2>/dev/null; $cmd\necho __CWD__$(pwd)")
            val idx = raw.lastIndexOf("__CWD__")
            val out = if (idx >= 0) raw.substring(0, idx).trim() else raw.trim()
            if (idx >= 0) cwd = raw.substring(idx + 7).lineSequence().firstOrNull().orEmpty().trim()
            lines.add(out.ifBlank { "(sem saída)" })
            busy = false
        }.start()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B1020)).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonButton("Voltar", pad = 12.dp, icon = Ico.CLOSE) { TermUi.open = false }
            Spacer(Modifier.width(12.dp))
            Text(if (cwd.isBlank()) "terminal" else cwd, color = Color(0xFF8A97B3), fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(lines) { l ->
                Text(l, color = if (l.startsWith("$ ")) Color(0xFF5AA9FF) else Color(0xFFCFE0FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonField(input, { input = it }, modifier = Modifier.weight(1f), placeholder = "comando", onSend = { runCmd() })
            Spacer(Modifier.width(8.dp))
            NeonButton(modifier = Modifier.size(46.dp), shape = CircleShape, active = busy, pad = 0.dp, icon = Ico.SEND) { runCmd() }
        }
    }
}
