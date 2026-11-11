package com.jarvis.app

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Bg = Color(0xFF020A14)
private val Panel = Color(0xFF07182B)
private val Field = Color(0xFF04101C)
private val Cyan = Color(0xFF00E5FF)
private val R12 = RoundedCornerShape(12.dp)

/** Clique sem ripple nativo. */
fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
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
    text: String, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(14.dp),
    active: Boolean = false, pad: Dp = 18.dp, onClick: () -> Unit
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
    ) { Text(text, color = Color.White, fontSize = 15.sp) }
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

/** Select menu animado (expande na própria tela, sem DropdownMenu nativo). */
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
            Text("▾", color = Cyan, fontSize = 18.sp, modifier = Modifier.rotate(rot))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 6.dp).clip(R12).background(Field).border(1.dp, Cyan.copy(alpha = 0.3f), R12)) {
                options.forEach { o ->
                    val sel = o == selected
                    val bg by animateColorAsState(if (sel) Cyan.copy(alpha = 0.18f) else Color.Transparent, label = "o")
                    Row(Modifier.fillMaxWidth().background(bg).tap { onSelect(o); open = false }.padding(12.dp)) {
                        Text(if (sel) "◆ $o" else o, color = if (sel) Cyan else Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** Seletor segmentado com indicador deslizante. */
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

// ---------- telas ----------

@Composable
fun MainScreen(vm: JarvisViewModel, openMenu: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(vm.msgs.size) { if (vm.msgs.isNotEmpty()) list.animateScrollToItem(vm.msgs.size - 1) }
    val label = when (vm.state) {
        State.IDLE -> if (vm.cfg.clap) "Bata 2 palmas ou toque no holograma" else "Toque no holograma para falar"
        State.LISTENING -> vm.partial.ifBlank { "Ouvindo..." }
        State.THINKING -> "Pensando..."
        State.SPEAKING -> "Falando..."
    }
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonButton("☰", pad = 12.dp, onClick = openMenu)
            Spacer(Modifier.width(12.dp))
            Text("J.A.R.V.I.S", color = Cyan, fontSize = 18.sp, letterSpacing = 4.sp)
        }
        Hologram(vm.state, Modifier.fillMaxWidth().weight(0.9f).tap { vm.listenNow() })
        Text(label, color = Cyan.copy(alpha = 0.8f), fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.msgs) { Bubble(it) }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonField(input, { input = it }, modifier = Modifier.weight(1f), placeholder = "Digite para o Jarvis...",
                singleLine = false, onSend = { vm.send(input); input = "" })
            Spacer(Modifier.width(8.dp))
            NeonButton("🎙", Modifier.size(48.dp), CircleShape, vm.state == State.LISTENING, 0.dp) { vm.listenNow() }
            Spacer(Modifier.width(6.dp))
            NeonButton("➤", pad = 14.dp) { vm.send(input); input = "" }
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
    val names = PRESETS.map { it.name } + CUSTOM
    val efforts = listOf("low", "medium", "high")
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("CONFIGURAÇÕES", color = Cyan, fontSize = 18.sp, letterSpacing = 3.sp)
        NeonField(c.apiKey, { vm.updateCfg(c.copy(apiKey = it)) }, "API KEY (FLATAI.PRO)", secret = true)
        NeonSelect("MODELO DE IA", names, c.preset) { n ->
            val p = PRESETS.find { it.name == n }
            vm.updateCfg(if (p != null) c.copy(preset = n, model = p.id, baseUrl = p.baseUrl, api = p.api) else c.copy(preset = n))
        }
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
        if (c.preset != CUSTOM) Text("URL: ${c.baseUrl}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        Text("ESFORÇO DE RACIOCÍNIO", color = Cyan.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 1.sp)
        NeonSegmented(listOf("Baixo", "Médio", "Alto"), efforts.indexOf(c.effort).coerceAtLeast(0)) {
            vm.updateCfg(c.copy(effort = efforts[it]))
        }
        NeonField(c.ghToken, { vm.updateCfg(c.copy(ghToken = it)) }, "TOKEN DO GITHUB (PAT)", secret = true)
        NeonToggle("Responder por voz", c.speak) { vm.updateCfg(c.copy(speak = it)) }
        NeonToggle("Ativar com 2 palmas", c.clap) { vm.updateCfg(c.copy(clap = it)) }
        NeonButton("📖  Documentação flatai.pro", Modifier.fillMaxWidth()) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://flatai.pro")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Holograma 3D: esfera wireframe girando com perspectiva, anéis orbitais, brilho e scanlines. */
@Composable
fun Hologram(state: State, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "holo")
    val spinMs = if (state == State.THINKING) 5000 else 14000
    val spin by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(spinMs, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(0f, 1f,
        infiniteRepeatable(tween(if (state == State.THINKING) 600 else 1500), RepeatMode.Reverse), label = "pulse")
    val col = when (state) {
        State.IDLE -> Cyan
        State.LISTENING -> Color(0xFF00FF9C)
        State.THINKING -> Color(0xFFFFB300)
        State.SPEAKING -> Color(0xFF7DF9FF)
    }
    val boost = when (state) { State.IDLE -> 0.03f; State.LISTENING -> 0.10f; State.THINKING -> 0.08f; State.SPEAKING -> 0.15f }

    Canvas(modifier) {
        val cx = size.width / 2; val cy = size.height / 2
        val rr = min(size.width, size.height) * 0.30f * (1f + boost * pulse)
        val ca = cos(spin); val sa = sin(spin)
        val tilt = 0.38f; val ct = cos(tilt); val st = sin(tilt)

        fun p(x: Float, y: Float, z: Float): FloatArray {
            val x1 = x * ca + z * sa; val z1 = -x * sa + z * ca
            val y2 = y * ct - z1 * st; val z2 = y * st + z1 * ct
            val f = 3f / (3f - z2 * 0.6f)
            return floatArrayOf(cx + x1 * rr * f, cy - y2 * rr * f, z2)
        }
        fun poly(pts: List<FloatArray>, w: Float) {
            for (i in 0 until pts.size - 1) {
                val a = pts[i]; val b = pts[i + 1]
                val d = (a[2] + b[2]) / 2
                drawLine(col.copy(alpha = ((0.55f + 0.45f * d) * 0.9f).coerceIn(0.07f, 1f)),
                    Offset(a[0], a[1]), Offset(b[0], b[1]), w)
            }
        }
        val tau = (2 * PI).toFloat()
        // brilho central
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.30f + 0.20f * pulse), Color.Transparent),
            Offset(cx, cy), rr * 1.7f), rr * 1.7f, Offset(cx, cy))
        // paralelos
        for (lat in listOf(-60, -30, 0, 30, 60)) {
            val ph = lat * PI.toFloat() / 180f
            poly((0..48).map { val th = it * tau / 48; p(cos(ph) * cos(th), sin(ph), cos(ph) * sin(th)) }, 1.4f)
        }
        // meridianos
        for (k in 0 until 8) {
            val la = k * PI.toFloat() / 8f
            poly((0..48).map { val ph = it * tau / 48; p(cos(ph) * cos(la), sin(ph), cos(ph) * sin(la)) }, 1.2f)
        }
        // anéis orbitais
        poly((0..64).map { val th = it * tau / 64; p(1.45f * cos(th), 0.58f * sin(th), 1.3f * sin(th)) }, 2.2f)
        poly((0..64).map { val th = it * tau / 64; p(1.3f * cos(th), -0.65f * sin(th), 1.1f * sin(th)) }, 1.6f)
        // base do projetor
        val by = cy + rr * 1.55f
        for (i in 0..2) {
            val w = rr * (1.2f + i * 0.35f)
            drawOval(col.copy(alpha = 0.5f - i * 0.15f), Offset(cx - w, by - w * 0.18f), Size(w * 2, w * 0.36f), style = Stroke(2f))
        }
        // scanlines
        var y = 0f
        while (y < size.height) { drawLine(Color.White.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), 1f); y += 6f }
    }
}
