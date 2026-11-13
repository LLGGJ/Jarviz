package com.jarvis.app

import android.content.Intent
import android.net.Uri
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

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
private val Orange = Color(0xFFFFA63D)
private val OrangeHot = Color(0xFFFFD58A)
private val Violet = Color(0xFFA394FF)
private val Muted = Color(0xFF8A97B3)
private val Glass = Color.White.copy(alpha = 0.05f)
private val GlassLine = Color.White.copy(alpha = 0.08f)

private fun stateColor(s: State) = when (s) { State.IDLE -> Color(0xFFB8C4DD); State.LISTENING -> Cyan; State.THINKING -> Violet; State.SPEAKING -> Orange }
private fun stateTitle(s: State) = when (s) { State.IDLE -> "REPOUSO"; State.LISTENING -> "ESCUTANDO"; State.THINKING -> "PENSANDO"; State.SPEAKING -> "FALANDO" }

@Composable
fun MainScreen(vm: JarvisViewModel, openMenu: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(vm.state) {
        if (vm.state == State.THINKING) {
            val t0 = System.currentTimeMillis()
            while (true) { elapsed = (System.currentTimeMillis() - t0) / 1000; delay(500) }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addUris(it) }
    var showLeft by remember { mutableStateOf(false) }
    var showRight by remember { mutableStateOf(true) }
    val shown = vm.shown
    val p = vm.pending
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bg, Color(0xFF182036)))).statusBarsPadding().navigationBarsPadding()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); vm.noteTouch() } } }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val wide = w > maxHeight * 1.15f && w >= 540.dp      // celular deitado ou tablet: 3 painéis como no app de referência
            val inlineLeft = wide && w >= 840.dp
            val pick = { picker.launch(arrayOf("*/*")); Unit }
            Column(Modifier.fillMaxSize()) {
                TopBar(vm, openMenu)
                if (wide) {
                    Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (inlineLeft) AnimatedVisibility(showLeft, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                            ModulesPanel(vm, { input = it }, pick, Modifier.width(220.dp).fillMaxHeight())
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            HologramCard(vm, shown, elapsed, Modifier.weight(1f).fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            PillsRow(shown, showLeft, showRight, { showLeft = !showLeft }, { showRight = !showRight })
                            ChipsRow(vm, pick, null)
                            AttachRow(vm)
                            InputBar(vm, input) { input = it }
                        }
                        AnimatedVisibility(showRight, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                            ActivityPanel(vm, openMenu, Modifier.width(if (w < 700.dp) 240.dp else 300.dp).fillMaxHeight())
                        }
                    }
                } else {
                    HologramCard(vm, shown, elapsed, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp))
                    Spacer(Modifier.height(6.dp)); Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { StatePills(shown) }; Spacer(Modifier.height(4.dp))
                    MessageList(vm, Modifier.weight(1.05f).fillMaxWidth())
                    ChipsRow(vm, pick) { showLeft = true }
                    AttachRow(vm)
                    InputBar(vm, input) { input = it }
                }
            }
            if (!inlineLeft) AnimatedVisibility(showLeft, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).tap { showLeft = false }) {
                    ModulesPanel(vm, { input = it; showLeft = false }, { showLeft = false; pick() },
                        Modifier.align(Alignment.CenterStart).padding(12.dp).width(270.dp).fillMaxHeight(0.92f).tap {})
                }
            }
        }
        AnimatedVisibility(p != null, enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut()) {
            if (p != null) PermissionDialog(p) { vm.decide(it) }
        }
    }
}

@Composable
private fun TopBar(vm: JarvisViewModel, openMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        NeonButton(pad = 10.dp, icon = Ico.MENU, onClick = openMenu)
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.logo), null, Modifier.size(30.dp).clip(CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("jarvis.", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text("ASSISTENTE DE IA", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (vm.cfg.key().isNotBlank()) Color(0xFF3DDC97) else Color(0xFFFF6B6B)))
        Spacer(Modifier.width(6.dp))
        Text(vm.cfg.model.substringAfterLast('/'), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 96.dp))
    }
}

@Composable
private fun HologramCard(vm: JarvisViewModel, shown: State, elapsed: Long, modifier: Modifier) {
    val sub = when (shown) {
        State.IDLE -> if (vm.cfg.clap) "Bata 2 palmas ou toque no núcleo" else "Toque no núcleo para falar"
        State.LISTENING -> vm.partial.ifBlank { "Aguardando sua voz" }
        State.THINKING -> "${vm.stage.ifBlank { "Processando" }} · ${elapsed}s"
        State.SPEAKING -> "Respondendo por voz"
    }
    Box(modifier.clip(R20).background(Brush.radialGradient(listOf(Color(0xFF2A3552), Color(0xFF1B2335)), radius = 900f)).border(1.dp, GlassLine, R20)) {
        NucleoHologram(shown, { vm.level }, vm.cfg.voiceViz, Modifier.fillMaxSize(), bottomInset = 62.dp,
            onTap = { vm.listenNow() },
            onLong = { vm.updateCfg(vm.cfg.copy(voiceViz = when (vm.cfg.voiceViz) { "bars" -> "wave"; "wave" -> "pulse"; else -> "bars" })) })
        Row(Modifier.align(Alignment.TopStart).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Muted))
            Text("VISUALIZAÇÃO ATIVA", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
        }
        if (vm.state == State.THINKING) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { NeonButton("Cancelar", pad = 8.dp, icon = Ico.CLOSE) { vm.cancel() } }
        else Text("JARVIS / CORE", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp))
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(stateColor(shown)))
                Text(stateTitle(shown), color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            }
            Text(sub, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatePills(shown: State) {
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Glass).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        State.values().forEach { s ->
            val sel = s == shown
            val bg by animateColorAsState(if (sel) Color.White.copy(alpha = 0.10f) else Color.Transparent, label = "pill")
            Row(Modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (sel) stateColor(s) else Muted.copy(alpha = 0.5f)))
                Text(stateTitle(s).lowercase().replaceFirstChar { it.uppercase() }, color = if (sel) Color.White else Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PillsRow(shown: State, showLeft: Boolean, showRight: Boolean, onLeft: () -> Unit, onRight: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SideToggle(Ico.PANEL_L, showLeft, onLeft)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { StatePills(shown) }
        SideToggle(Ico.PANEL_R, showRight, onRight)
    }
}

@Composable
private fun SideToggle(icon: Ico, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Color.White.copy(alpha = 0.10f) else Glass).tap(onClick), contentAlignment = Alignment.Center) {
        NeonIcon(icon, Modifier.size(18.dp), if (on) Color.White else Muted)
    }
}

@Composable
private fun Chip(text: String, icon: Ico, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Glass).border(1.dp, GlassLine, RoundedCornerShape(14.dp)).tap(onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NeonIcon(icon, Modifier.size(16.dp), Color(0xFFD5DEEE))
        Text(text, color = Color(0xFFD5DEEE), fontSize = 12.sp)
    }
}

@Composable
private fun ChipsRow(vm: JarvisViewModel, onPick: () -> Unit, onModules: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Anexar", Ico.CLIP, onPick)
        if (onModules != null) Chip("Módulos", Ico.BOLT, onModules)
        Chip("Verificar celular", Ico.SHIELD) { vm.runScanNow() }
        Chip("Limpar", Ico.TRASH) { vm.clearChat() }
    }
}

@Composable
private fun AttachRow(vm: JarvisViewModel) {
    if (vm.attachments.isEmpty()) return
    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(vm.attachments) { a ->
            Row(Modifier.clip(R12).background(Field).border(1.dp, Cyan.copy(alpha = 0.5f), R12).padding(horizontal = 10.dp, vertical = 6.dp)
                .tap { vm.removeAttachment(a) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(a.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                NeonIcon(Ico.CLOSE, Modifier.size(14.dp), Cyan)
            }
        }
    }
}

@Composable
private fun InputBar(vm: JarvisViewModel, input: String, onInput: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(R20).background(Glass).border(1.dp, GlassLine, R20).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        NeonField(input, onInput, modifier = Modifier.weight(1f), placeholder = "Pergunte qualquer coisa ao Jarvis...",
            singleLine = false, onSend = { vm.send(input); onInput("") }, onFocus = { vm.setTyping(it) })
        Spacer(Modifier.width(8.dp))
        NeonButton(modifier = Modifier.size(46.dp), shape = CircleShape, active = vm.state == State.LISTENING, pad = 0.dp, icon = Ico.MIC) { vm.listenNow() }
        Spacer(Modifier.width(6.dp))
        NeonButton(modifier = Modifier.size(46.dp), shape = CircleShape, pad = 0.dp, icon = Ico.SEND) { vm.send(input); onInput("") }
    }
}

@Composable
private fun MessageList(vm: JarvisViewModel, modifier: Modifier, hPad: Dp = 12.dp) {
    val list = rememberLazyListState()
    LaunchedEffect(vm.msgs.size, vm.msgs.lastOrNull()?.text?.length) {
        if (vm.msgs.isNotEmpty()) list.scrollToItem(vm.msgs.size - 1, 100000)
    }
    LazyColumn(modifier.padding(horizontal = hPad), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(vm.msgs) { m -> Bubble(m) { a -> if (a.startsWith("install:")) vm.installRun(a.removePrefix("install:")) } }
    }
}

private class Mod(val icon: Ico, val title: String, val sub: String, val prefill: String?, val action: (() -> Unit)? = null)

@Composable
private fun ModulesPanel(vm: JarvisViewModel, onPrefill: (String) -> Unit, onPick: () -> Unit, modifier: Modifier) {
    val mods = listOf(
        Mod(Ico.SEARCH, "Pesquisa web", "Pesquisar na internet", "Pesquise na web sobre "),
        Mod(Ico.FOLDER, "Arquivos", "Pastas, ler e editar código", "Liste os arquivos da pasta Download"),
        Mod(Ico.TERMINAL, "Terminal", "Comandos Linux (curl, git)", "No terminal, rode: "),
        Mod(Ico.CODE, "GitHub", "Repositórios e código", "Liste meus repositórios do GitHub"),
        Mod(Ico.BOLT, "Automações", "Alarmes, apps e ligações", "Crie um alarme para "),
        Mod(Ico.SHIELD, "Segurança", "Verificar o celular", null) { vm.runScanNow() },
        Mod(Ico.CLIP, "Anexos", "Fotos, zips e arquivos", null) { onPick() },
        Mod(Ico.DOWNLOAD, "Nova versão", "Gerar build do Jarvis", "Faça um build do app"),
    )
    Column(modifier.clip(R20).background(Panel.copy(alpha = 0.97f)).border(1.dp, GlassLine, R20).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MÓDULOS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            Text("${mods.size}", color = Muted, fontSize = 11.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            mods.forEach { m ->
                Row(Modifier.fillMaxWidth().clip(R12).background(Glass).border(1.dp, GlassLine, R12)
                    .tap { val act = m.action; if (act != null) act() else onPrefill(m.prefill.orEmpty()) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                        NeonIcon(m.icon, Modifier.size(18.dp), Color(0xFFD5DEEE))
                    }
                    Column {
                        Text(m.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(m.sub, color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(R12).background(Glass).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("MODELO", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            Text(vm.cfg.model, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ControlRow(icon: Ico, text: String, trailing: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(R12).background(Glass).border(1.dp, GlassLine, R12).tap(onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonIcon(icon, Modifier.size(16.dp), Color(0xFFD5DEEE))
        Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Text(trailing, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun ActivityPanel(vm: JarvisViewModel, openMenu: () -> Unit, modifier: Modifier) {
    var ctrl by remember { mutableStateOf(true) }
    val rot by animateFloatAsState(if (ctrl) 180f else 0f, label = "ctrl")
    Column(modifier.clip(R20).background(Glass).border(1.dp, GlassLine, R20).padding(12.dp)) {
        Text("ATIVIDADE DO ASSISTENTE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text("Histórico da sessão", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        MessageList(vm, Modifier.weight(1f).fillMaxWidth(), 0.dp)
        Row(Modifier.fillMaxWidth().tap { ctrl = !ctrl }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("CONTROLES", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            NeonIcon(Ico.CHEVRON, Modifier.size(16.dp).rotate(rot), Muted)
        }
        AnimatedVisibility(ctrl, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ControlRow(Ico.MIC, "Palmas", if (vm.cfg.clap) "ON" else "OFF") { vm.updateCfg(vm.cfg.copy(clap = !vm.cfg.clap)) }
                ControlRow(Ico.BOLT, "Responder por voz", if (vm.cfg.speak) "ON" else "OFF") { vm.updateCfg(vm.cfg.copy(speak = !vm.cfg.speak)) }
                ControlRow(Ico.CLOSE, "Interromper resposta", null) { vm.interrupt() }
                ControlRow(Ico.TRASH, "Limpar conversa", null) { vm.clearChat() }
                ControlRow(Ico.MENU, "Configurações", null, openMenu)
            }
        }
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

// ---------- configurações (em seções que abrem e fecham) ----------

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
private fun Label(t: String) { Text(t, color = Muted, fontSize = 11.sp, letterSpacing = 1.sp) }

@Composable
fun SettingsPanel(vm: JarvisViewModel) {
    val c = vm.cfg
    val ctx = LocalContext.current
    val options = vm.availableModels() + CUSTOM
    val efforts = listOf("low", "medium", "high")
    val rates = listOf("0.9", "1.0", "1.1", "1.25")
    val sens = listOf("low", "mid", "high")
    val auto = "Automática (melhor disponível)"
    val vopts = vm.voiceOptions
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            Text("Configurações", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Section("Inteligência artificial", startOpen = true) {
            NeonSelect("PROVEDOR", PROVIDERS.map { it.second }, PROVIDERS.first { it.first == c.provider }.second) { n ->
                vm.setProvider(PROVIDERS.first { it.second == n }.first)
            }
            if (c.provider == "gemini") {
                NeonField(c.geminiKey, { vm.updateCfg(c.copy(geminiKey = it)) }, "API KEY (GOOGLE AI STUDIO)", placeholder = "AIza...", secret = true)
                NeonButton("Criar chave no AI Studio", Modifier.fillMaxWidth(), icon = Ico.OPEN) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } else NeonField(c.apiKey, { vm.updateCfg(c.copy(apiKey = it)) }, "API KEY (FLATAI.PRO)", secret = true)
            NeonSelect("MODELO", options, c.preset) { n ->
                vm.updateCfg(
                    if (n == CUSTOM) c.copy(preset = n)
                    else c.copy(preset = n, model = n, api = apiFor(n, c.provider), baseUrl = baseFor(apiFor(n, c.provider), c.provider))
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("Atualizar", Modifier.weight(1f), pad = 12.dp, icon = Ico.REFRESH) { vm.refreshModels() }
                NeonButton("Testar", Modifier.weight(1f), pad = 12.dp, icon = Ico.CHECK) { vm.testConnection() }
            }
            if (vm.modelsMsg.isNotBlank()) Text(vm.modelsMsg, color = Muted, fontSize = 12.sp)
            AnimatedVisibility(c.preset == CUSTOM, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeonField(c.model, { vm.updateCfg(c.copy(model = it)) }, "ID DO MODELO")
                    NeonField(c.baseUrl, { vm.updateCfg(c.copy(baseUrl = it)) }, "URL BASE")
                    NeonSegmented(listOf("Anthropic", "OpenAI"), if (c.api == "anthropic") 0 else 1) {
                        vm.updateCfg(c.copy(api = if (it == 0) "anthropic" else "openai"))
                    }
                }
            }
            Label("RACIOCÍNIO (PROGRAMAÇÃO E TAREFAS DIFÍCEIS)")
            NeonSegmented(listOf("Baixo", "Médio", "Alto"), efforts.indexOf(c.effort).coerceAtLeast(0)) { vm.updateCfg(c.copy(effort = efforts[it])) }
            NeonToggle("Modo rápido", c.fast) { vm.updateCfg(c.copy(fast = it)) }
        }
        Section("Voz e palmas") {
            NeonToggle("Responder por voz", c.speak) { vm.updateCfg(c.copy(speak = it)) }
            NeonSelect("VOZ", listOf(auto) + vopts.map { it.second }, vopts.firstOrNull { it.first == c.voiceName }?.second ?: auto) { n ->
                vm.updateCfg(c.copy(voiceName = vopts.firstOrNull { it.second == n }?.first ?: "")); vm.previewVoice()
            }
            NeonSegmented(listOf("Lenta", "Normal", "Rápida", "Turbo"), rates.indexOf(c.voiceRate).coerceAtLeast(1)) {
                vm.updateCfg(c.copy(voiceRate = rates[it])); vm.previewVoice()
            }
            NeonToggle("Ativar com 2 palmas", c.clap) { vm.updateCfg(c.copy(clap = it)) }
            Label("SENSIBILIDADE DAS PALMAS")
            NeonSegmented(listOf("Baixa", "Média", "Alta"), sens.indexOf(c.clapSens).coerceAtLeast(1)) { vm.updateCfg(c.copy(clapSens = sens[it])) }
            Text("Dica: segure o núcleo para trocar o visual da voz.", color = Muted, fontSize = 12.sp)
        }
        Section("GitHub e build") {
            NeonField(c.ghToken, { vm.updateCfg(c.copy(ghToken = it)) }, "TOKEN DO GITHUB (ghp_...)", placeholder = "ghp_xxxxxxxxxxxx", secret = true)
            NeonField(c.repo, { vm.updateCfg(c.copy(repo = it)) }, "REPOSITÓRIO DO APP", placeholder = "dono/repositorio")
            Text("Com o token o Jarvis gerencia seu GitHub; toda ação que escreve pede sua permissão.", color = Muted, fontSize = 12.sp)
        }
        Section("Terminal Linux") {
            val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            Text(vm.termuxStatus(), color = Muted, fontSize = 12.sp)
            NeonButton("Instalar o Termux", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.OPEN) {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            NeonButton("Permitir acesso ao Termux", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.CHECK) { permLauncher.launch("com.termux.permission.RUN_COMMAND") }
            NeonButton("Copiar comando de configuração", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.TERMINAL) { vm.copyTermuxSetup() }
            NeonButton("Abrir o Termux", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.TERMINAL) { vm.openTermux() }
            Text("1) Instale o Termux pelo F-Droid ou GitHub (não pela Play Store). 2) Abra o Termux e cole o comando copiado. 3) Em Configurações do Android, deixe o Termux com \"Exibir sobre outros apps\" ligado. Sem o Termux, o Jarvis usa o shell do Android e um curl embutido.",
                color = Muted, fontSize = 12.sp)
        }
        Section("Pesquisa e segurança") {
            NeonField(c.firecrawl, { vm.updateCfg(c.copy(firecrawl = it)) }, "CHAVE FIRECRAWL (OPCIONAL)", secret = true)
            NeonToggle("Verificar o celular 1x por dia", c.autoScan) { vm.updateCfg(c.copy(autoScan = it)) }
            NeonButton("Verificar agora", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.SHIELD) { vm.runScanNow() }
            NeonButton("Permitir acesso aos arquivos", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.FOLDER) { vm.requestFiles() }
        }
        NeonButton(if (c.provider == "gemini") "Documentação do Gemini" else "Documentação flatai.pro", Modifier.fillMaxWidth(), pad = 12.dp, icon = Ico.BOOK) {
            val doc = if (c.provider == "gemini") "https://ai.google.dev/gemini-api/docs/models" else "https://flatai.pro"
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(doc)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

// ---------- núcleo: nuvem de partículas + emblema; ao falar, barras laranja no ritmo da voz ----------

private const val NU_N = 4200

@Composable
fun NucleoHologram(
    state: State, level: () -> Float, skin: String, modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp, onTap: () -> Unit = {}, onLong: () -> Unit = {}
) {
    val speed by animateFloatAsState(when (state) { State.THINKING -> 2.6f; State.LISTENING -> 1.4f; State.SPEAKING -> 1.2f; else -> 1f }, tween(700), label = "ns")
    val think by animateFloatAsState(if (state == State.THINKING) 1f else 0f, tween(500), label = "nt")
    val listen by animateFloatAsState(if (state == State.LISTENING) 1f else 0f, tween(400), label = "nl")
    val speak by animateFloatAsState(if (state == State.SPEAKING) 1f else 0f, tween(400), label = "nsp")
    val speedNow by rememberUpdatedState(speed)
    val levelFn by rememberUpdatedState(level)
    var phase by remember { mutableFloatStateOf(0f) }
    var lv by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { n ->
            if (last != 0L) {
                phase += (n - last) / 1_000_000_000f * speedNow
                val tg = levelFn()
                lv += (tg - lv) * (if (tg > lv) 0.45f else 0.08f)
            }
            last = n
        }
    }
    val data = remember {
        val r = Random(21)
        FloatArray(NU_N * 5).also { d ->
            for (i in 0 until NU_N) {
                val y = 1f - 2f * (i + 0.5f) / NU_N
                val rad = sqrt(1f - y * y)
                val th = i * 2.3999631f
                d[i * 5] = cos(th) * rad; d[i * 5 + 1] = y; d[i * 5 + 2] = sin(th) * rad
                d[i * 5 + 3] = if (r.nextFloat() < 0.72f) 0.86f + 0.26f * ((r.nextFloat() + r.nextFloat() + r.nextFloat()) / 3f) else 0.25f + 1.35f * r.nextFloat().let { it * it }
                d[i * 5 + 4] = r.nextFloat()
            }
        }
    }
    val back = remember { ArrayList<Offset>(NU_N) }
    val mid = remember { ArrayList<Offset>(NU_N) }
    val front = remember { ArrayList<Offset>(NU_N) }

    Box(modifier.pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() }) }) {
        Canvas(Modifier.fillMaxSize()) {
            val t = phase
            val L = lv.coerceIn(0f, 1f)
            val w = size.width; val h = size.height
            val dp = 1.dp.toPx()
            val ah = h - bottomInset.toPx()
            val cx = w / 2f; val cy = ah / 2f
            val R = min(w, ah) * 0.38f
            val pulse = 0.5f + 0.5f * sin(t * (2.2f + think * 3f))
            val yaw = t * 0.12f; val pit = 0.28f
            val cyw = cos(yaw); val syw = sin(yaw); val cp = cos(pit); val sp = sin(pit)
            back.clear(); mid.clear(); front.clear()
            for (i in 0 until NU_N) {
                val ux = data[i * 5]; val uy = data[i * 5 + 1]; val uz = data[i * 5 + 2]
                val rr = data[i * 5 + 3]; val sd = data[i * 5 + 4]
                var rad = rr * (1f + 0.02f * sin(t * 1.3f + sd * 6.283f))
                rad *= 1f + speak * (0.05f + 0.20f * L) * sin(t * 6f + sd * 12f)
                rad *= 1f - listen * 0.05f * pulse
                val tw = think * 1.6f * uy * sin(t * 0.8f)
                val c2 = cos(tw); val s2 = sin(tw)
                val x0 = ux * rad; val y0 = uy * rad; val z0 = uz * rad
                val x1 = x0 * c2 + z0 * s2; val z1 = -x0 * s2 + z0 * c2
                val x2 = x1 * cyw + z1 * syw; val z2 = -x1 * syw + z1 * cyw
                val y3 = y0 * cp - z2 * sp; val z3 = y0 * sp + z2 * cp
                val f = 1f / (1f - z3 * 0.18f)
                val p = Offset(cx + x2 * R * f, cy - y3 * R * f)
                if (z3 < -0.3f) back.add(p) else if (z3 < 0.3f) mid.add(p) else front.add(p)
            }
            // brilho do centro (muda de cor com o estado) e luz no chão
            val gr = R * 0.65f
            val idle = 1f - maxOf(listen, speak, think)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFCFE0FF).copy(alpha = 0.14f * idle), Color.Transparent), Offset(cx, cy), gr), gr, Offset(cx, cy))
            drawCircle(Brush.radialGradient(listOf(Cyan.copy(alpha = (0.30f + 0.30f * L) * listen), Color.Transparent), Offset(cx, cy), gr), gr, Offset(cx, cy))
            drawCircle(Brush.radialGradient(listOf(Violet.copy(alpha = 0.28f * think), Color.Transparent), Offset(cx, cy), gr), gr, Offset(cx, cy))
            drawCircle(Brush.radialGradient(listOf(Orange.copy(alpha = (0.22f + 0.40f * L) * speak), Color.Transparent), Offset(cx, cy), gr * 1.15f), gr * 1.15f, Offset(cx, cy))
            val fy = cy + R * 1.25f
            drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), Offset(cx, fy), R * 1.2f),
                Offset(cx - R * 1.2f, fy - R * 0.12f), Size(R * 2.4f, R * 0.24f))
            // partículas
            drawCircle(Color.White.copy(alpha = 0.05f), R * 1.12f, Offset(cx, cy), style = Stroke(1f * dp))
            drawPoints(back, PointMode.Points, Color(0x55B4C3E0), 0.8f * dp, StrokeCap.Round)
            drawPoints(mid, PointMode.Points, Color(0x99E4ECFB), 1.0f * dp, StrokeCap.Round)
            drawPoints(front, PointMode.Points, Color(0xE6FFFFFF), 1.25f * dp, StrokeCap.Round)
            // anel do emblema + ondas ao escutar
            val er = 41f * dp
            drawCircle(Color.White.copy(alpha = 0.35f * (1f - speak)), er, Offset(cx, cy), style = Stroke(1.5f * dp))
            drawCircle(Cyan.copy(alpha = 0.9f * listen), er, Offset(cx, cy), style = Stroke(2f * dp))
            if (listen > 0.01f) {
                val rp = (t * 0.7f) % 1f
                drawCircle(Cyan.copy(alpha = (1f - rp) * 0.5f * listen), er + rp * R * 0.55f, Offset(cx, cy), style = Stroke(2f * dp))
            }
            if (think > 0.01f) for (k in 0..2) {
                val r = (52f + k * 9f) * dp
                drawArc(Violet.copy(alpha = 0.9f * think), (t * 180f * (1f + k * 0.4f) + k * 120f) % 360f, 55f, false,
                    Offset(cx - r, cy - r), Size(r * 2f, r * 2f), style = Stroke(2.5f * dp, cap = StrokeCap.Round))
            }
            // voz: barras laranja (ou onda / pulso) no ritmo da fala
            if (speak > 0.01f) {
                val a = 0.12f + 0.88f * L
                val bw = R * 1.5f
                when (skin) {
                    "wave" -> for (k in 0..2) {
                        val path = Path()
                        val amp = R * 0.42f * a * (1f - k * 0.25f)
                        for (i in 0..80) {
                            val x = cx - bw / 2f + bw * i / 80f
                            val y = cy + amp * sin(i * 0.28f + t * (4f + k * 1.7f) + k * 1.3f) * sin(i / 80f * 3.1415927f)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, lerp(Orange, OrangeHot, k * 0.35f).copy(alpha = (0.95f - k * 0.25f) * speak), style = Stroke((3.5f - k * 0.7f) * dp, cap = StrokeCap.Round))
                    }
                    "pulse" -> for (k in 0..3) {
                        val p = (t * 0.7f + k * 0.25f) % 1f
                        val r = R * (0.12f + p * 0.55f * (0.45f + 0.55f * a))
                        drawCircle(Orange.copy(alpha = (1f - p) * 0.9f * speak), r, Offset(cx, cy), style = Stroke(2.5f * dp))
                    }
                    else -> {
                        val n = 29; val gap = bw / n; val maxH = R * 0.70f
                        for (i in 0 until n) {
                            val d = (i - (n - 1) / 2f) / (n * 0.26f)
                            val env = exp(-d * d)
                            val s = abs(sin(t * 3.4f + i * 0.62f)) * (0.5f + 0.5f * abs(sin(t * 1.9f + i * 1.7f)))
                            val bh = maxH * env * (0.10f + 0.90f * a * s) + 3f * dp
                            val x = cx - bw / 2f + gap * (i + 0.5f)
                            drawLine(Orange.copy(alpha = 0.18f * speak), Offset(x, cy - bh / 2f - 4f * dp), Offset(x, cy + bh / 2f + 4f * dp), gap * 1.1f, StrokeCap.Round)
                            drawLine(lerp(Orange, OrangeHot, env).copy(alpha = speak), Offset(x, cy - bh / 2f), Offset(x, cy + bh / 2f), gap * 0.42f, StrokeCap.Round)
                        }
                    }
                }
            }
        }
        Image(painterResource(R.drawable.logo), null,
            Modifier.align(Alignment.Center).offset(y = -(bottomInset / 2)).size(72.dp).clip(CircleShape).graphicsLayer { alpha = 1f - speak })
    }
}
