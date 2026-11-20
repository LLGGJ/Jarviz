package com.jarvis.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Bg = Color(0xFF121826)
private val Panel = Color(0xFF1B2438)
private val Field = Color(0xFF151C2D)
private val Cyan = Color(0xFF5AA9FF)
private val Amber = Color(0xFFFFB300)
private val R12 = RoundedCornerShape(12.dp)
private val R16 = RoundedCornerShape(16.dp)

fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
}

// ---------- ícones vetoriais (desenhados em SVG-style, sem emoji Unicode) ----------

enum class Ico { MENU, MIC, SEND, BOOK, REFRESH, CHEVRON, CHECK, CLOSE, OPEN, CLIP, FOLDER, SHIELD, DOWNLOAD, TRASH, SEARCH, TERMINAL, BOLT, CODE, PANEL_L, PANEL_R }

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
            Ico.CLIP -> drawPath(Path().apply {
                moveTo(20f * s, 11f * s); lineTo(12f * s, 19f * s); quadraticBezierTo(8.5f * s, 22f * s, 5.5f * s, 18.5f * s)
                quadraticBezierTo(2.8f * s, 15.5f * s, 6f * s, 12.5f * s); lineTo(14f * s, 4.5f * s)
                quadraticBezierTo(16.5f * s, 2.2f * s, 18.8f * s, 4.5f * s); quadraticBezierTo(21f * s, 6.8f * s, 18.6f * s, 9.2f * s)
                lineTo(10.6f * s, 17.2f * s); quadraticBezierTo(9.3f * s, 18.3f * s, 8.2f * s, 17.2f * s)
                quadraticBezierTo(7.1f * s, 16f * s, 8.2f * s, 14.8f * s); lineTo(15f * s, 8f * s)
            }, color, style = st)
            Ico.FOLDER -> drawPath(Path().apply {
                moveTo(3f * s, 6f * s); lineTo(10f * s, 6f * s); lineTo(12f * s, 8f * s); lineTo(21f * s, 8f * s)
                lineTo(21f * s, 19f * s); lineTo(3f * s, 19f * s); close()
            }, color, style = st)
            Ico.SHIELD -> {
                drawPath(Path().apply {
                    moveTo(12f * s, 3f * s); lineTo(20f * s, 6f * s); lineTo(20f * s, 12f * s)
                    quadraticBezierTo(20f * s, 18f * s, 12f * s, 21f * s); quadraticBezierTo(4f * s, 18f * s, 4f * s, 12f * s)
                    lineTo(4f * s, 6f * s); close()
                }, color, style = st)
                line(o(8.5f, 12f), o(11f, 14.5f)); line(o(11f, 14.5f), o(15.5f, 9.5f))
            }
            Ico.DOWNLOAD -> { line(o(12f, 4f), o(12f, 15f)); line(o(12f, 15f), o(7.5f, 10.5f)); line(o(12f, 15f), o(16.5f, 10.5f)); line(o(5f, 20f), o(19f, 20f)) }
            Ico.SEARCH -> { drawCircle(color, 6.5f * s, o(10.5f, 10.5f), style = st); line(o(15.5f, 15.5f), o(20f, 20f)) }
            Ico.TERMINAL -> {
                drawRoundRect(color, o(3f, 5f), Size(18f * s, 14f * s), CornerRadius(3f * s, 3f * s), style = st)
                line(o(7f, 10f), o(10f, 12f)); line(o(10f, 12f), o(7f, 14f)); line(o(12f, 15f), o(17f, 15f))
            }
            Ico.BOLT -> drawPath(Path().apply {
                moveTo(13f * s, 3f * s); lineTo(6f * s, 13f * s); lineTo(11f * s, 13f * s); lineTo(10f * s, 21f * s); lineTo(18f * s, 10f * s); lineTo(13f * s, 10f * s); close()
            }, color, style = st)
            Ico.CODE -> { line(o(9f, 7f), o(4f, 12f)); line(o(4f, 12f), o(9f, 17f)); line(o(15f, 7f), o(20f, 12f)); line(o(20f, 12f), o(15f, 17f)) }
            Ico.PANEL_L -> { drawRoundRect(color, o(3f, 5f), Size(18f * s, 14f * s), CornerRadius(3f * s, 3f * s), style = st); line(o(9f, 5f), o(9f, 19f)) }
            Ico.PANEL_R -> { drawRoundRect(color, o(3f, 5f), Size(18f * s, 14f * s), CornerRadius(3f * s, 3f * s), style = st); line(o(15f, 5f), o(15f, 19f)) }
            Ico.TRASH -> {
                drawPath(Path().apply { moveTo(6f * s, 7f * s); lineTo(7f * s, 20f * s); lineTo(17f * s, 20f * s); lineTo(18f * s, 7f * s) }, color, style = st)
                line(o(4f, 7f), o(20f, 7f)); line(o(9f, 7f), o(9f, 4f)); line(o(9f, 4f), o(15f, 4f)); line(o(15f, 4f), o(15f, 7f))
            }
            Ico.OPEN -> {
                drawPath(Path().apply { moveTo(18f * s, 13f * s); lineTo(18f * s, 19f * s); lineTo(5f * s, 19f * s); lineTo(5f * s, 6f * s); lineTo(11f * s, 6f * s) }, color, style = st)
                line(o(14f, 3f), o(21f, 3f)); line(o(21f, 3f), o(21f, 10f)); line(o(21f, 3f), o(10f, 14f))
            }
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
        ) { if (VideoEditor.open) VideoEditorScreen(vm) else if (TermUi.open) TerminalScreen() else MainScreen(vm) { scope.launch { drawer.open() } } }
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
            .background(Brush.horizontalGradient(listOf(Color(0xFF22314F), if (active) Color(0xFF1F7A5C) else Color(0xFF2B4A7A))))
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
    placeholder: String = "", secret: Boolean = false, singleLine: Boolean = true, onSend: (() -> Unit)? = null,
    onFocus: ((Boolean) -> Unit)? = null
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
                .onFocusChanged { focused = it.isFocused; onFocus?.invoke(it.isFocused) }.padding(12.dp),
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
            Column(Modifier.padding(top = 6.dp).heightIn(max = 320.dp).verticalScroll(rememberScrollState()).clip(R12).background(Field).border(1.dp, Cyan.copy(alpha = 0.3f), R12)) {
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
            Text("O Jarvis quer executar:", color = Color.White, fontSize = 13.sp)
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

private val R20 = RoundedCornerShape(20.dp)
private val Muted = Color(0xFF8A97B3)
private val Glass = Color.White.copy(alpha = 0.05f)
private val GlassLine = Color.White.copy(alpha = 0.08f)

/** O que o robô está fazendo, a partir da etapa atual, para virar expressão no rosto (não aparece texto de status). */
private fun activityOf(vm: JarvisViewModel): String {
    if (vm.state != State.THINKING) return ""
    val s = vm.stage.lowercase()
    return when {
        s.contains("tentando") || s.contains("sem resposta") -> "retry"
        s.contains("screen_") -> "screen"
        s.contains("web") || s.contains("search") || s.contains("fetch") || s.contains("pesquis") -> "web"
        s.contains("github") || s.contains("git_") -> "code"
        s.contains("fs_") || s.contains("file") -> "file"
        s.contains("terminal") -> "term"
        s.contains("phone_") -> "phone"
        s.contains("executando") -> "work"
        else -> ""
    }
}

@Composable
fun MainScreen(vm: JarvisViewModel, openMenu: () -> Unit) {
    val ctx = LocalContext.current
    var typing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var capVisible by remember { mutableStateOf(false) }
    LaunchedEffect(AppActions.pendingKeys.size) {
        while (AppActions.pendingKeys.isNotEmpty()) {
            val k = AppActions.pendingKeys.removeAt(0)
            vm.updateCfg(vm.cfg.withKey(k.provider, k.key))
        }
    }
    val last = vm.msgs.lastOrNull { it.role != "user" }
    LaunchedEffect(last?.text?.length, vm.msgs.size) {
        if (last != null) { capVisible = true; delay(12000); capVisible = false }
    }
    LaunchedEffect(Unit) { while (true) { delay(1000); Mood.tick(vm.state == State.IDLE) } }
    val failed = capVisible && last?.text?.startsWith("Erro") == true
    val p = vm.pending
    val openLink: (String) -> Unit = { u -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bg, Color(0xFF182036)))).statusBarsPadding().navigationBarsPadding()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); vm.noteTouch(); Mood.touch() } } }
    ) {
        // O robô é a tela inteira: toque = falar (ou cancelar/interromper se ele está ocupado); segurar = digitar.
        RobotFace(vm.shown, { vm.level }, activityOf(vm), failed, Modifier.fillMaxSize(),
            onTap = {
                Mood.tapped()
                if (typing) typing = false
                else if (vm.state == State.THINKING) vm.cancel()
                else if (vm.state == State.SPEAKING) vm.interrupt()
                else { BeepGuard.pre(); vm.listenNow() }
            },
            onLong = { typing = !typing }, mood = Mood.current)
        Box(Modifier.align(Alignment.TopStart).padding(14.dp).size(38.dp).clip(CircleShape).background(Glass).tap(openMenu), contentAlignment = Alignment.Center) {
            NeonIcon(Ico.MENU, Modifier.size(18.dp), Muted)
        }
        if (typing) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                MessageList(vm, Modifier.fillMaxWidth().heightIn(max = 260.dp))
                InputBar(vm, input) { input = it }
            }
        } else if (capVisible && last != null) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(16.dp).widthIn(max = 560.dp).clip(R20).background(Panel.copy(alpha = 0.92f))
                    .border(1.dp, GlassLine, R20).tap { capVisible = false }.padding(14.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState())
            ) { MarkdownText(last.text.take(600), openLink) }
        }
        AnimatedVisibility(p != null, enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut()) {
            if (p != null) PermissionDialog(p) { vm.decide(it) }
        }
    }
}

@Composable
private fun InputBar(vm: JarvisViewModel, input: String, onInput: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(R20).background(Panel).border(1.dp, GlassLine, R20).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        NeonField(input, onInput, modifier = Modifier.weight(1f), placeholder = "Digite para o Jarvis...",
            singleLine = false, onSend = { vm.send(input); onInput("") }, onFocus = { vm.setTyping(it) })
        Spacer(Modifier.width(8.dp))
        NeonButton(modifier = Modifier.size(46.dp), shape = CircleShape, pad = 0.dp, icon = Ico.SEND) { vm.send(input); onInput("") }
    }
}

@Composable
private fun MessageList(vm: JarvisViewModel, modifier: Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(vm.msgs.size, vm.msgs.lastOrNull()?.text?.length) {
        if (vm.msgs.isNotEmpty()) list.scrollToItem(vm.msgs.size - 1, 100000)
    }
    LazyColumn(modifier.padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(vm.msgs) { m -> Bubble(m) { a -> if (a.startsWith("install:")) vm.installRun(a.removePrefix("install:")) } }
    }
}

// ---------- mensagens com formatação (listas, negrito, código, links) ----------

private sealed class Blk {
    class P(val t: String) : Blk()
    class H(val t: String) : Blk()
    class Li(val bullet: String, val t: String) : Blk()
    class Code(val t: String) : Blk()
    object Gap : Blk()
}

private val RE_BULLET = Regex("^[-*•]\\s+")
private val RE_NUM = Regex("^\\d+[.)]\\s+")

private fun parseMd(text: String): List<Blk> {
    val out = mutableListOf<Blk>()
    var inCode = false
    val code = StringBuilder()
    for (raw in text.split("\n")) {
        val t = raw.trim()
        if (t.startsWith("```")) {
            if (inCode) { out.add(Blk.Code(code.toString().trimEnd())); code.setLength(0); inCode = false } else inCode = true
            continue
        }
        if (inCode) { code.append(raw).append('\n'); continue }
        when {
            t.isEmpty() -> if (out.lastOrNull() !is Blk.Gap && out.isNotEmpty()) out.add(Blk.Gap)
            t.startsWith("#") -> out.add(Blk.H(t.trimStart('#').trim()))
            RE_BULLET.containsMatchIn(t) -> out.add(Blk.Li("•", t.replace(RE_BULLET, "")))
            RE_NUM.containsMatchIn(t) -> out.add(Blk.Li(t.substringBefore(' '), t.substringAfter(' ')))
            t.startsWith("|") -> {
                val last = out.lastOrNull()
                if (last is Blk.Code) { out[out.size - 1] = Blk.Code(last.t + "\n" + t) } else out.add(Blk.Code(t))
            }
            else -> out.add(Blk.P(t))
        }
    }
    if (inCode) out.add(Blk.Code(code.toString().trimEnd()))
    return out
}

private val INLINE = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)|(https?://[^\\s)]+)")

private fun inlineMd(s: String): AnnotatedString = buildAnnotatedString {
    val link = SpanStyle(color = Color(0xFF8FC2FF), textDecoration = TextDecoration.Underline)
    var last = 0
    for (m in INLINE.findAll(s)) {
        append(s.substring(last, m.range.first))
        when {
            m.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            m.groups[2] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x33FFFFFF), color = Color(0xFFBFE0FF))) { append(m.groupValues[2]) }
            m.groups[3] != null -> { pushStringAnnotation("URL", m.groupValues[4]); withStyle(link) { append(m.groupValues[3]) }; pop() }
            else -> { pushStringAnnotation("URL", m.groupValues[5]); withStyle(link) { append(m.groupValues[5]) }; pop() }
        }
        last = m.range.last + 1
    }
    append(s.substring(last))
}

@Composable
private fun MdLine(text: String, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val a = remember(text) { inlineMd(text) }
    ClickableText(a, modifier, style = TextStyle(color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)) { off ->
        a.getStringAnnotations("URL", off, off).firstOrNull()?.let { onLink(it.item) }
    }
}

@Composable
fun MarkdownText(text: String, onLink: (String) -> Unit) {
    val blocks = remember(text) { parseMd(text) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Blk.P -> MdLine(b.t, onLink)
                is Blk.H -> Text(b.t, color = Color(0xFFBFD9FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                is Blk.Li -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(b.bullet, color = Cyan, fontSize = 14.sp, modifier = Modifier.widthIn(min = 16.dp))
                    MdLine(b.t, onLink, Modifier.weight(1f))
                }
                is Blk.Code -> Box(Modifier.fillMaxWidth().clip(R12).background(Color(0xFF0E1422)).horizontalScroll(rememberScrollState()).padding(10.dp)) {
                    Text(b.t, color = Color(0xFFCFE0FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                is Blk.Gap -> Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
fun Bubble(m: Msg, onAction: (String) -> Unit = {}) {
    val user = m.role == "user"
    val ctx = LocalContext.current
    val open: (String) -> Unit = { u -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(16.dp))
                .background(if (user) Color(0xFF24406B) else Glass).border(1.dp, GlassLine, RoundedCornerShape(16.dp)).padding(12.dp)
        ) { if (user) Text(m.text, color = Color.White, fontSize = 14.sp) else MarkdownText(m.text, open) }
        if (m.action?.startsWith("install:") == true) NeonButton("Baixar e instalar", pad = 12.dp, icon = Ico.DOWNLOAD, active = true) { onAction(m.action) }
    }
}

// ---------- configurações ----------

@Composable
private fun Section(title: String, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "sec")
    Column(Modifier.fillMaxWidth().clip(R16).background(Glass).border(1.dp, GlassLine, R16)) {
        Row(Modifier.fillMaxWidth().tap { open = !open }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            NeonIcon(Ico.CHEVRON, Modifier.size(20.dp).rotate(rot), Cyan)
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
fun SettingsPanel(vm: JarvisViewModel) {
    val c = vm.cfg
    val ctx = LocalContext.current
    fun open(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    var taps by remember { mutableIntStateOf(0) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Configurações", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.tap { taps++ })
        Section("Chaves de IA", startOpen = true) {
            Text(
                "Cole a chave de um ou mais provedores. O Jarvis usa modelo pequeno para coisa simples, grande para coisa difícil, e troca de provedor sozinho se um falhar.",
                color = Muted, fontSize = 12.sp
            )
            PROVIDER_DEFS.forEach { d ->
                NeonField(c.keyOf(d.id), { vm.updateCfg(vm.cfg.withKey(d.id, it.trim())) }, d.label.uppercase(), placeholder = "cole a chave", secret = true)
            }
        }
        Section("Modelo de IA") {
            var msg by remember { mutableStateOf("") }
            NeonToggle("Escolher o modelo à mão", !c.auto) { vm.updateCfg(vm.cfg.copy(auto = !it)) }
            if (c.auto) {
                Text("Automático: o Jarvis usa um modelo pequeno para coisa simples e um grande para coisa difícil.", color = Muted, fontSize = 12.sp)
            } else {
                NeonSelect("PROVEDOR", PROVIDERS.map { it.second }, providerLabel(c.provider)) { n ->
                    vm.updateCfg(vm.cfg.switchedTo(PROVIDERS.first { it.second == n }.first)); msg = ""
                }
                NeonSelect("MODELO", c.modelsOf(c.provider), c.model) { n ->
                    vm.updateCfg(vm.cfg.copy(model = n, preset = n, api = apiFor(n, c.provider), baseUrl = baseFor(apiFor(n, c.provider), c.provider)))
                }
                NeonButton("Buscar os modelos da minha chave", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.REFRESH) { vm.refreshProviderModels { msg = it } }
                if (msg.isNotBlank()) Text(msg, color = Muted, fontSize = 12.sp)
            }
            NeonToggle("Se falhar, tentar outros (fallback)", c.fallback) { vm.updateCfg(vm.cfg.copy(fallback = it)) }
        }
        Section("Permissões", startOpen = true) {
            Text(
                when {
                    ScreenControl.svc != null -> "Controle do celular: ligado e conectado."
                    ScreenControl.ready() -> "Ligado no Android, mas o serviço ainda não conectou. Desligue e ligue o Jarvis em Acessibilidade."
                    else -> "Controle do celular: desligado. Ligue o Jarvis em Acessibilidade."
                },
                color = Muted, fontSize = 12.sp
            )
            NeonButton("Acessibilidade (tocar na tela)", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.BOLT) { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            NeonButton("Exibir sobre outros apps", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.OPEN) {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
            }
            NeonButton("Não deixar o Android me desligar", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.SHIELD) { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            NeonButton("Acesso aos arquivos", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.FOLDER) { vm.requestFiles() }
        }
        Section("Jarvis") {
            NeonToggle("Responder por voz", c.speak) { vm.updateCfg(vm.cfg.copy(speak = it)) }
            NeonToggle("Ativar falando \"Jarvis\"", c.wake) { vm.updateCfg(vm.cfg.copy(wake = it)) }
            if (c.wake) Text(WakeWord.status.ifBlank { "Iniciando..." }, color = Muted, fontSize = 12.sp)
            NeonToggle("Mexer no celular", c.screen) { vm.updateCfg(vm.cfg.copy(screen = it)) }
            NeonToggle("Ficar ligado em segundo plano", c.background) { vm.updateCfg(vm.cfg.copy(background = it)) }
            NeonToggle("Robozinho sobre outros apps", c.overlay) { vm.updateCfg(vm.cfg.copy(overlay = it)) }
            Text("Toque no robô para falar com ele e segure para digitar.", color = Muted, fontSize = 12.sp)
        }
        Section("Avançado") {
            NeonField(c.ghToken, { vm.updateCfg(vm.cfg.copy(ghToken = it)) }, "TOKEN DO GITHUB", placeholder = "ghp_xxxxxxxxxxxx", secret = true)
            NeonField(c.repo, { vm.updateCfg(vm.cfg.copy(repo = it)) }, "REPOSITÓRIO DO APP", placeholder = "dono/repositorio")
            NeonField(c.firecrawl, { vm.updateCfg(vm.cfg.copy(firecrawl = it)) }, "CHAVE FIRECRAWL (PESQUISA NA WEB)", secret = true)
            NeonField(c.jamendoKey, { vm.updateCfg(vm.cfg.copy(jamendoKey = it.trim())) }, "JAMENDO CLIENT ID (MÚSICA LIVRE)", secret = true)
            NeonField(c.haUrl, { vm.updateCfg(vm.cfg.copy(haUrl = it.trim())) }, "HOME ASSISTANT - URL", placeholder = "http://192.168.0.10:8123")
            NeonField(c.haToken, { vm.updateCfg(vm.cfg.copy(haToken = it.trim())) }, "HOME ASSISTANT - TOKEN", secret = true)
            NeonField(c.pcUrl, { vm.updateCfg(vm.cfg.copy(pcUrl = it.trim())) }, "PC - URL DO AGENTE", placeholder = "http://192.168.0.20:8765")
            NeonField(c.pcToken, { vm.updateCfg(vm.cfg.copy(pcToken = it.trim())) }, "PC - TOKEN", secret = true)
            NeonButton("Terminal do Jarvis", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.TERMINAL) { TermUi.open = true }
        }
        if (taps >= 5) {
            Section("Diagnóstico", startOpen = true) {
                NeonButton("Copiar o registro", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.CLIP) {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("jarvis", DebugLog.lines.joinToString("\n")))
                }
                NeonButton("Limpar o registro", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.TRASH) { DebugLog.lines.clear() }
                Text(
                    if (DebugLog.lines.isEmpty()) "Vazio." else DebugLog.lines.takeLast(40).joinToString("\n"),
                    color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
