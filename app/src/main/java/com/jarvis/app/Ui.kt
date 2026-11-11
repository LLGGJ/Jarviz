package com.jarvis.app

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val Bg = Color(0xFF020A14)
private val Panel = Color(0xFF07182B)
private val Field = Color(0xFF04101C)
private val Cyan = Color(0xFF00E5FF)
private val Amber = Color(0xFFFFB300)
private val R12 = RoundedCornerShape(12.dp)
private val R16 = RoundedCornerShape(16.dp)

fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
}

// ---------- ícones vetoriais (desenhados em SVG-style, sem emoji Unicode) ----------

enum class Ico { MENU, MIC, SEND, BOOK, REFRESH, CHEVRON, CHECK, CLOSE }

@Composable
fun NeonIcon(ico: Ico, modifier: Modifier = Modifier.size(22.dp), color: Color = Color.White) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val w = 2f * s
        val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(a: Offset, b: Offset) = drawLine(color, a, b, w, StrokeCap.Round)
        when (ico) {
            Ico.MENU -> { line(o(4f, 7f), o(20f, 7f)); line(o(4f, 12f), o(20f, 12f)); line(o(4f, 17f), o(14f, 17f)) }
            Ico.MIC -> {
                drawRoundRect(color, o(9f, 3f), Size(6f * s, 11f * s), CornerRadius(3f * s, 3f * s), style = st)
                drawPath(Path().apply { arcTo(Rect(o(6f, 5f), Size(12f * s, 12f * s)), 0f, 180f, true) }, color, style = st)
                line(o(12f, 17f), o(12f, 21f)); line(o(9f, 21f), o(15f, 21f))
            }
            Ico.SEND -> {
                drawPath(Path().apply { moveTo(3f * s, 11f * s); lineTo(21f * s, 3f * s); lineTo(14f * s, 21f * s); lineTo(11f * s, 13f * s); close() }, color, style = st)
                line(o(11f, 13f), o(21f, 3f))
            }
            Ico.BOOK -> {
                drawPath(Path().apply {
                    moveTo(3f * s, 5f * s); lineTo(9f * s, 5f * s); quadraticBezierTo(12f * s, 5f * s, 12f * s, 8f * s)
                    lineTo(12f * s, 20f * s); quadraticBezierTo(12f * s, 17f * s, 9f * s, 17f * s); lineTo(3f * s, 17f * s); close()
                }, color, style = st)
                drawPath(Path().apply {
                    moveTo(21f * s, 5f * s); lineTo(15f * s, 5f * s); quadraticBezierTo(12f * s, 5f * s, 12f * s, 8f * s)
                    lineTo(12f * s, 20f * s); quadraticBezierTo(12f * s, 17f * s, 15f * s, 17f * s); lineTo(21f * s, 17f * s); close()
                }, color, style = st)
            }
            Ico.REFRESH -> {
                drawPath(Path().apply { arcTo(Rect(o(4f, 4f), Size(16f * s, 16f * s)), -40f, 300f, true) }, color, style = st)
                line(o(10.6f, 4.1f), o(7.6f, 1.8f)); line(o(10.6f, 4.1f), o(7.8f, 6.9f))
            }
            Ico.CHEVRON -> { line(o(6f, 9f), o(12f, 15f)); line(o(12f, 15f), o(18f, 9f)) }
            Ico.CHECK -> { line(o(5f, 12.5f), o(10f, 17.5f)); line(o(10f, 17.5f), o(19f, 7f)) }
            Ico.CLOSE -> { line(o(6f, 6f), o(18f, 18f)); line(o(18f, 6f), o(6f, 18f)) }
        }
    }
}

@Composable
fun JarvisApp(vm: JarvisViewModel) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Bg, surface = Panel)) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = { ModalDrawerSheet(drawerContainerColor = Panel) { SettingsPanel(vm) } }
        ) { MainScreen(vm) { scope.launch { drawer.open() } } }
    }
}

// ---------- componentes neon personalizados ----------

@Composable
fun NeonButton(
    text: String = "", modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(14.dp),
    active: Boolean = false, pad: Dp = 18.dp, icon: Ico? = null, onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "scale")
    val t = rememberInfiniteTransition(label = "glow")
    val glow by t.animateFloat(0.35f, 0.95f, infiniteRepeatable(tween(if (active) 500 else 1400), RepeatMode.Reverse), label = "g")
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF0B3A52), if (active) Color(0xFF0E8A6E) else Color(0xFF0A5C7A))))
            .border(1.5.dp, Cyan.copy(alpha = if (pressed) 1f else glow), shape)
            .clickable(src, null, onClick = onClick)
            .padding(horizontal = pad, vertical = pad * 0.66f),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) NeonIcon(icon, Modifier.size(20.dp), if (active) Color(0xFF9CFFD9) else Color.White)
            if (text.isNotEmpty()) Text(text, color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
fun NeonField(
    value: String, onChange: (String) -> Unit, label: String = "", modifier: Modifier = Modifier,
    placeholder: String = "", secret: Boolean = false, singleLine: Boolean = true, onSend: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val bc by animateColorAsState(if (focused) Cyan else Cyan.copy(alpha = 0.25f), label = "bc")
    Column(modifier) {
        if (label.isNotBlank()) {
            Text(label, color = Cyan.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
        }
        BasicTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().clip(R12).background(Field).border(1.5.dp, bc, R12)
                .onFocusChanged { focused = it.isFocused }.padding(12.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Cyan), singleLine = singleLine, maxLines = if (singleLine) 1 else 3,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(imeAction = if (onSend != null) ImeAction.Send else ImeAction.Default),
            keyboardActions = KeyboardActions(onSend = { onSend?.invoke() }),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty())
                        Text(placeholder, color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp)
                    inner()
                }
            }
        )
    }
}

@Composable
fun NeonSelect(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "rot")
    val bc by animateColorAsState(if (open) Cyan else Cyan.copy(alpha = 0.3f), label = "bc")
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Cyan.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().clip(R12).background(Field).border(1.5.dp, bc, R12)
                .tap { open = !open }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(selected.ifBlank { "Selecionar..." }, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            NeonIcon(Ico.CHEVRON, Modifier.size(20.dp).rotate(rot), Cyan)
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 6.dp).clip(R12).background(Field).border(1.dp, Cyan.copy(alpha = 0.3f), R12)) {
                options.forEach { o ->
                    val sel = o == selected
                    val bg by animateColorAsState(if (sel) Cyan.copy(alpha = 0.18f) else Color.Transparent, label = "o")
                    Row(Modifier.fillMaxWidth().background(bg).tap { onSelect(o); open = false }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(o, color = if (sel) Cyan else Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (sel) NeonIcon(Ico.CHECK, Modifier.size(18.dp), Cyan)
                    }
                }
            }
        }
    }
}

@Composable
fun NeonSegmented(options: List<String>, index: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(42.dp).clip(R12).background(Field).border(1.dp, Cyan.copy(alpha = 0.3f), R12)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * index, spring(stiffness = Spring.StiffnessMediumLow), label = "x")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight().padding(3.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.40f), Cyan.copy(alpha = 0.15f))))
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                Box(Modifier.weight(1f).fillMaxHeight().tap { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(o, color = if (i == index) Cyan else Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun NeonToggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (on) 22.dp else 0.dp, spring(Spring.DampingRatioMediumBouncy), label = "x")
    val tc by animateColorAsState(if (on) Cyan.copy(alpha = 0.4f) else Color(0xFF123047), label = "tc")
    Row(Modifier.fillMaxWidth().tap { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(Modifier.width(48.dp).height(26.dp).clip(CircleShape).background(tc).border(1.dp, Cyan.copy(alpha = 0.6f), CircleShape).padding(3.dp)) {
            Box(Modifier.offset(x = x).size(20.dp).clip(CircleShape).background(if (on) Cyan else Color.Gray))
        }
    }
}

@Composable
fun PermissionDialog(p: Pending, onDecide: (Boolean) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).tap {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).clip(R16).background(Panel).border(1.5.dp, Amber, R16).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(p.title, color = Amber, fontSize = 14.sp, letterSpacing = 2.sp)
            Text("O Jarvis quer executar no GitHub:", color = Color.White, fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(R12).background(Field).padding(10.dp).verticalScroll(rememberScrollState())) {
                Text(p.detail, color = Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonButton("Negar", Modifier.weight(1f), icon = Ico.CLOSE) { onDecide(false) }
                NeonButton("Permitir", Modifier.weight(1f), active = true, icon = Ico.CHECK) { onDecide(true) }
            }
        }
    }
}

// ---------- telas ----------

@Composable
fun MainScreen(vm: JarvisViewModel, openMenu: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(vm.msgs.size) { if (vm.msgs.isNotEmpty()) list.animateScrollToItem(vm.msgs.size - 1) }
    val label = when (vm.state) {
        State.IDLE -> if (vm.cfg.clap) "Bata 2 palmas, toque ou arraste o holograma" else "Toque no holograma para falar"
        State.LISTENING -> vm.partial.ifBlank { "Ouvindo..." }
        State.THINKING -> "Pensando..."
        State.SPEAKING -> "Falando..."
    }
    val p = vm.pending
    Box(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                NeonButton(pad = 12.dp, icon = Ico.MENU, onClick = openMenu)
                Spacer(Modifier.width(12.dp))
                Text("J.A.R.V.I.S", color = Cyan, fontSize = 18.sp, letterSpacing = 4.sp)
            }
            Hologram(vm.state, Modifier.fillMaxWidth().weight(1f).tap { vm.listenNow() })
            Text(label, color = Cyan.copy(alpha = 0.8f), fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            LazyColumn(Modifier.weight(0.8f).padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vm.msgs) { Bubble(it) }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NeonField(input, { input = it }, modifier = Modifier.weight(1f), placeholder = "Digite para o Jarvis...",
                    singleLine = false, onSend = { vm.send(input); input = "" })
                Spacer(Modifier.width(8.dp))
                NeonButton(modifier = Modifier.size(48.dp), shape = CircleShape, active = vm.state == State.LISTENING,
                    pad = 0.dp, icon = Ico.MIC) { vm.listenNow() }
                Spacer(Modifier.width(6.dp))
                NeonButton(pad = 14.dp, icon = Ico.SEND) { vm.send(input); input = "" }
            }
        }
        AnimatedVisibility(p != null, enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut()) {
            if (p != null) PermissionDialog(p) { vm.decide(it) }
        }
    }
}

@Composable
fun Bubble(m: Msg) {
    val user = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (user) Color(0xFF0B3A52) else Panel, shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.4f)), modifier = Modifier.widthIn(max = 300.dp)
        ) { Text(m.text, Modifier.padding(10.dp), color = Color.White, fontSize = 14.sp) }
    }
}

@Composable
fun SettingsPanel(vm: JarvisViewModel) {
    val c = vm.cfg
    val ctx = LocalContext.current
    val options = vm.availableModels() + CUSTOM
    val efforts = listOf("low", "medium", "high")
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("CONFIGURAÇÕES", color = Cyan, fontSize = 18.sp, letterSpacing = 3.sp)
        NeonField(c.apiKey, { vm.updateCfg(c.copy(apiKey = it)) }, "API KEY (FLATAI.PRO)", secret = true)
        NeonSelect("MODELO DE IA", options, c.preset) { n ->
            vm.updateCfg(
                if (n == CUSTOM) c.copy(preset = n)
                else c.copy(preset = n, model = n, api = apiFor(n), baseUrl = baseFor(apiFor(n)))
            )
        }
        NeonButton("Atualizar modelos do plano", Modifier.fillMaxWidth(), icon = Ico.REFRESH) { vm.refreshModels() }
        if (vm.modelsMsg.isNotBlank()) Text(vm.modelsMsg, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        AnimatedVisibility(c.preset == CUSTOM, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NeonField(c.model, { vm.updateCfg(c.copy(model = it)) }, "ID DO MODELO")
                NeonField(c.baseUrl, { vm.updateCfg(c.copy(baseUrl = it)) }, "URL BASE")
                Text("PROTOCOLO", color = Cyan.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 1.sp)
                NeonSegmented(listOf("Anthropic", "OpenAI"), if (c.api == "anthropic") 0 else 1) {
                    vm.updateCfg(c.copy(api = if (it == 0) "anthropic" else "openai"))
                }
            }
        }
        Text("ESFORÇO DE RACIOCÍNIO", color = Cyan.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 1.sp)
        NeonSegmented(listOf("Baixo", "Médio", "Alto"), efforts.indexOf(c.effort).coerceAtLeast(0)) {
            vm.updateCfg(c.copy(effort = efforts[it]))
        }
        NeonField(c.ghToken, { vm.updateCfg(c.copy(ghToken = it)) }, "TOKEN DO GITHUB (ghp_...)", placeholder = "ghp_xxxxxxxxxxxx", secret = true)
        Text("Com o token o Jarvis lê e gerencia seu GitHub. Toda ação que escreve pede sua permissão.",
            color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        NeonToggle("Responder por voz", c.speak) { vm.updateCfg(c.copy(speak = it)) }
        NeonToggle("Ativar com 2 palmas", c.clap) { vm.updateCfg(c.copy(clap = it)) }
        NeonButton("Documentação flatai.pro", Modifier.fillMaxWidth(), icon = Ico.BOOK) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://flatai.pro")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

// ---------- holograma 3D ----------

private class Particle(val th: Float, val ph: Float, val rad: Float, val sp: Float, val sz: Float)

private val ICO_V: List<FloatArray> = run {
    val p = (1f + sqrt(5f)) / 2f
    val n = sqrt(1f + p * p)
    listOf(
        floatArrayOf(0f, 1f, p), floatArrayOf(0f, 1f, -p), floatArrayOf(0f, -1f, p), floatArrayOf(0f, -1f, -p),
        floatArrayOf(1f, p, 0f), floatArrayOf(1f, -p, 0f), floatArrayOf(-1f, p, 0f), floatArrayOf(-1f, -p, 0f),
        floatArrayOf(p, 0f, 1f), floatArrayOf(p, 0f, -1f), floatArrayOf(-p, 0f, 1f), floatArrayOf(-p, 0f, -1f),
    ).map { floatArrayOf(it[0] / n, it[1] / n, it[2] / n) }
}
private val ICO_E: List<IntArray> = run {
    val e = mutableListOf<IntArray>()
    for (i in 0 until 12) for (j in i + 1 until 12) {
        val a = ICO_V[i]; val b = ICO_V[j]
        val d = (a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])
        if (d < 1.2f) e.add(intArrayOf(i, j))
    }
    e
}

/** Holograma 3D: esfera de rede com nós, núcleo icosaedro, anéis giroscópicos com satélites,
 *  partículas, feixe de varredura, projetor com luz. Arraste para girar em 3D. */
@Composable
fun Hologram(state: State, modifier: Modifier = Modifier) {
    val target = when (state) { State.IDLE -> Cyan; State.LISTENING -> Color(0xFF00FF9C); State.THINKING -> Color(0xFFFFB300); State.SPEAKING -> Color(0xFF7DF9FF) }
    val col by animateColorAsState(target, tween(500), label = "hc")
    val speed by animateFloatAsState(when (state) { State.THINKING -> 2.8f; State.SPEAKING -> 1.6f; State.LISTENING -> 1.3f; else -> 1f }, tween(700), label = "hs")
    val speedNow by rememberUpdatedState(speed)
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { n -> if (last != 0L) phase += (n - last) / 1_000_000_000f * speedNow; last = n }
    }
    var dragYaw by remember { mutableFloatStateOf(0f) }
    var dragPitch by remember { mutableFloatStateOf(0f) }
    val particles = remember {
        val r = Random(7)
        List(56) { Particle(r.nextFloat() * 6.283f, (r.nextFloat() - 0.5f) * 2.4f, 1.15f + r.nextFloat(), (0.15f + r.nextFloat() * 0.35f) * (if (r.nextBoolean()) 1f else -1f), 1.2f + r.nextFloat() * 1.8f) }
    }

    Canvas(modifier.pointerInput(Unit) {
        detectDragGestures { _, d -> dragYaw += d.x * 0.01f; dragPitch = (dragPitch + d.y * 0.008f).coerceIn(-0.6f, 0.6f) }
    }) {
        val tau = 6.2831855f
        val ph = phase
        val c0 = col
        val bright = lerp(c0, Color.White, 0.6f)
        val cx = size.width / 2f; val cy = size.height * 0.46f
        val pulse = 0.5f + 0.5f * sin(ph * (if (state == State.THINKING) 7f else 3.5f))
        val amp = when (state) { State.SPEAKING -> 0.07f; State.LISTENING -> 0.05f; State.THINKING -> 0.04f; else -> 0.015f }
        val rr = min(size.width, size.height * 1.15f) * 0.27f * (1f + amp * pulse)
        val cv = cos(dragYaw); val sv = sin(dragYaw)
        val pit = 0.32f + dragPitch
        val cpt = cos(pit); val spt = sin(pit)
        var px = 0f; var py = 0f; var pz = 0f

        fun pr(x: Float, y: Float, z: Float, own: Float) {
            val co = cos(own); val so = sin(own)
            val x0 = x * co + z * so; val z0 = -x * so + z * co
            val x1 = x0 * cv + z0 * sv; val z1 = -x0 * sv + z0 * cv
            val y2 = y * cpt - z1 * spt; val z2 = y * spt + z1 * cpt
            val f = 3.2f / (3.2f - z2 * 0.7f)
            px = cx + x1 * rr * f; py = cy - y2 * rr * f; pz = z2
        }
        fun curve(n: Int, w: Float, c: Color, a: Float, gen: (Int) -> Unit) {
            gen(0)
            var x0 = px; var y0 = py; var z0 = pz
            for (i in 1..n) {
                gen(i)
                val d = ((z0 + pz) / 2f).coerceIn(-1.3f, 1.3f)
                drawLine(c.copy(alpha = ((0.5f + 0.5f * d) * a).coerceIn(0.05f, 1f)), Offset(x0, y0), Offset(px, py), (w * (0.85f + 0.35f * d)).coerceAtLeast(0.6f))
                x0 = px; y0 = py; z0 = pz
            }
        }
        fun dot(r: Float, c: Color, a: Float) {
            val k = (0.55f + 0.45f * pz).coerceIn(0.2f, 1f)
            drawCircle(c.copy(alpha = 0.22f * a * k), r * 3f, Offset(px, py))
            drawCircle(c.copy(alpha = a * k), r * (1f + 0.35f * pz), Offset(px, py))
        }
        fun ring(rad: Float, ax: Float, az: Float, own: Float, w: Float, c: Color, sat: Float) {
            val cxa = cos(ax); val sxa = sin(ax); val cza = cos(az); val sza = sin(az)
            fun rp(th: Float) {
                val x = rad * cos(th); val z = rad * sin(th)
                val y1 = -z * sxa; val z1 = z * cxa
                pr(x * cza - y1 * sza, x * sza + y1 * cza, z1, own)
            }
            curve(72, w, c, 0.9f) { rp(it * tau / 72f) }
            rp(sat); dot(4f, bright, 1f)
        }

        // brilho de fundo
        drawCircle(Brush.radialGradient(listOf(c0.copy(alpha = 0.32f + 0.15f * pulse), Color.Transparent), Offset(cx, cy), rr * 2.3f), rr * 2.3f, Offset(cx, cy))

        // projetor: base, feixe de luz e ondas
        val by = cy + rr * 1.85f
        val beam = Path().apply {
            moveTo(cx - rr * 1.15f, by); lineTo(cx + rr * 1.15f, by)
            lineTo(cx + rr * 0.55f, cy + rr * 0.9f); lineTo(cx - rr * 0.55f, cy + rr * 0.9f); close()
        }
        drawPath(beam, Brush.verticalGradient(listOf(Color.Transparent, c0.copy(alpha = 0.22f)), by - rr * 1.4f, by))
        for (i in 0..2) {
            val w = rr * (1.15f + i * 0.4f)
            drawOval(c0.copy(alpha = 0.5f - i * 0.13f), Offset(cx - w, by - w * 0.18f), Size(w * 2f, w * 0.36f), style = Stroke(2f))
        }
        val bw = rr * 1.5f
        for (k in 0..3) drawArc(bright.copy(alpha = 0.9f), ph * 45f * (if (state == State.THINKING) 2.5f else 1f) + k * 90f, 38f, false,
            Offset(cx - bw, by - bw * 0.18f), Size(bw * 2f, bw * 0.36f), style = Stroke(3.5f, cap = StrokeCap.Round))
        if (state == State.LISTENING || state == State.SPEAKING) {
            for (k in 0..2) {
                val p = (ph * 0.45f + k / 3f) % 1f
                val w = rr * (1.1f + 1.3f * p)
                drawOval(c0.copy(alpha = (1f - p) * 0.55f), Offset(cx - w, by - w * 0.18f), Size(w * 2f, w * 0.36f), style = Stroke(2.5f))
            }
        }

        // esfera de rede (paralelos + meridianos) e nós
        val own = ph * 0.35f
        for (lat in listOf(-60, -30, 0, 30, 60)) {
            val a = lat * 0.017453292f
            curve(48, 1.5f, c0, 0.85f) { val th = it * tau / 48f; pr(cos(a) * cos(th), sin(a), cos(a) * sin(th), own) }
        }
        for (k in 0 until 6) {
            val la = k * 3.1415927f / 6f
            curve(48, 1.2f, c0, 0.8f) { val a = it * tau / 48f; pr(cos(a) * cos(la), sin(a), cos(a) * sin(la), own) }
        }
        for (lat in listOf(-60, -30, 0, 30, 60)) {
            val a = lat * 0.017453292f
            for (j in 0 until 12) {
                val la = j * tau / 12f
                pr(cos(a) * cos(la), sin(a), cos(a) * sin(la), own); dot(2.2f, bright, 0.95f)
            }
        }

        // varredura (anel de luz subindo e descendo)
        val sy = sin(ph * 0.8f) * 0.92f
        val sr = sqrt(1f - sy * sy)
        curve(64, 7f, bright, 0.22f) { val th = it * tau / 64f; pr(sr * cos(th), sy, sr * sin(th), 0f) }
        curve(64, 2.6f, bright, 0.95f) { val th = it * tau / 64f; pr(sr * cos(th), sy, sr * sin(th), 0f) }

        // núcleo icosaedro girando ao contrário
        val coreOwn = -ph * 0.9f
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f + 0.3f * pulse), c0.copy(alpha = 0.25f), Color.Transparent), Offset(cx, cy), rr * 0.55f), rr * 0.55f, Offset(cx, cy))
        for (e in ICO_E) {
            val a = ICO_V[e[0]]; val b = ICO_V[e[1]]
            curve(1, 1.6f, bright, 0.95f) { val v = if (it == 0) a else b; pr(v[0] * 0.42f, v[1] * 0.42f, v[2] * 0.42f, coreOwn) }
        }
        for (v in ICO_V) { pr(v[0] * 0.42f, v[1] * 0.42f, v[2] * 0.42f, coreOwn); dot(2.6f, Color.White, 1f) }

        // anéis giroscópicos com satélites
        ring(1.5f, 1.2f, 0.0f, ph * 0.5f, 2.2f, c0, ph * 1.6f)
        ring(1.32f, 0.5f, 1.0f, -ph * 0.7f, 1.8f, c0, -ph * 1.2f)
        ring(1.68f, 0.15f, -0.6f, ph * 0.3f, 1.4f, c0, ph * 0.9f)

        // partículas orbitais
        for (p in particles) {
            val th = p.th + ph * p.sp
            val cp = cos(p.ph)
            pr(p.rad * cp * cos(th), p.rad * sin(p.ph), p.rad * cp * sin(th), 0f)
            dot(p.sz, bright, 0.8f)
        }

        // borda de luz (fresnel)
        drawCircle(Brush.radialGradient(0.0f to Color.Transparent, 0.8f to Color.Transparent, 0.96f to c0.copy(alpha = 0.4f), 1f to Color.Transparent,
            center = Offset(cx, cy), radius = rr * 1.18f), rr * 1.18f, Offset(cx, cy))

        // scanlines
        var y = 0f
        while (y < size.height) { drawLine(Color.White.copy(alpha = 0.03f), Offset(0f, y), Offset(size.width, y), 1f); y += 6f }
    }
}
