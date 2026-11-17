package com.jarvis.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun faceColor(s: State): Color = when (s) {
    State.IDLE -> Color(0xFF8EC8FF)
    State.LISTENING -> Color(0xFF4DE3C2)
    State.THINKING -> Color(0xFFB5A6FF)
    State.SPEAKING -> Color(0xFFFFB454)
}

/** Selo pequeno ao lado da cabeça com o que o robô está fazendo: web, código, arquivos, terminal, celular, tela. */
private fun DrawScope.badge(kind: String, c: Offset, r: Float, t: Float, col: Color) {
    val sw = r * 0.16f
    val st = Stroke(sw, cap = StrokeCap.Round)
    drawCircle(Color(0xFF0E1525), r, c)
    drawCircle(col.copy(alpha = 0.7f), r, c, style = Stroke(sw * 0.7f))
    when (kind) {
        "web" -> {
            drawCircle(col, r * 0.36f, Offset(c.x - r * 0.08f, c.y - r * 0.08f), style = st)
            drawLine(col, Offset(c.x + r * 0.18f, c.y + r * 0.18f), Offset(c.x + r * 0.5f, c.y + r * 0.5f), sw, StrokeCap.Round)
        }
        "code" -> {
            drawLine(col, Offset(c.x - r * 0.15f, c.y - r * 0.4f), Offset(c.x - r * 0.5f, c.y), sw, StrokeCap.Round)
            drawLine(col, Offset(c.x - r * 0.5f, c.y), Offset(c.x - r * 0.15f, c.y + r * 0.4f), sw, StrokeCap.Round)
            drawLine(col, Offset(c.x + r * 0.15f, c.y - r * 0.4f), Offset(c.x + r * 0.5f, c.y), sw, StrokeCap.Round)
            drawLine(col, Offset(c.x + r * 0.5f, c.y), Offset(c.x + r * 0.15f, c.y + r * 0.4f), sw, StrokeCap.Round)
        }
        "file" -> drawRoundRect(col, Offset(c.x - r * 0.45f, c.y - r * 0.3f), Size(r * 0.9f, r * 0.6f), CornerRadius(r * 0.12f, r * 0.12f), style = st)
        "term" -> {
            drawLine(col, Offset(c.x - r * 0.45f, c.y - r * 0.3f), Offset(c.x - r * 0.1f, c.y), sw, StrokeCap.Round)
            drawLine(col, Offset(c.x - r * 0.1f, c.y), Offset(c.x - r * 0.45f, c.y + r * 0.3f), sw, StrokeCap.Round)
            drawLine(col, Offset(c.x + r * 0.05f, c.y + r * 0.35f), Offset(c.x + r * 0.5f, c.y + r * 0.35f), sw, StrokeCap.Round)
        }
        "phone", "screen" -> {
            drawRoundRect(col, Offset(c.x - r * 0.26f, c.y - r * 0.46f), Size(r * 0.52f, r * 0.92f), CornerRadius(r * 0.1f, r * 0.1f), style = st)
            val k = (t * 1.5f) % 1f
            drawCircle(col.copy(alpha = 1f - k), r * (0.1f + 0.3f * k), Offset(c.x, c.y + r * 0.05f), style = Stroke(sw * 0.6f))
        }
        "retry" -> {
            drawArc(col, 200f, 260f, false, Offset(c.x - r * 0.3f, c.y - r * 0.5f), Size(r * 0.6f, r * 0.6f), style = st)
            drawCircle(col, sw * 0.7f, Offset(c.x, c.y + r * 0.45f))
        }
        else -> for (k in 0..2) {
            val a = t * 3f + k * 2.094f
            drawCircle(col, r * 0.12f, Offset(c.x + cos(a) * r * 0.4f, c.y + sin(a) * r * 0.4f))
        }
    }
}

/**
 * Robozinho fofo, a única cara do Jarvis. Não tem texto de status: o que ele está fazendo aparece na expressão.
 * Repouso: pisca, olha pros lados, sorri. Escutando: olhos grandes e boquinha "o". Falando: boca no ritmo da voz.
 * Trabalhando: olhar e selinho mudam conforme a tarefa (web, código, arquivos, terminal, celular). Erro: carinha triste.
 * activity: "", "web", "code", "file", "term", "phone", "screen", "retry" ou "work".
 */
@Composable
fun RobotFace(
    state: State, level: () -> Float, activity: String, error: Boolean, modifier: Modifier = Modifier,
    onTap: () -> Unit = {}, onLong: () -> Unit = {}
) {
    val think by animateFloatAsState(if (state == State.THINKING) 1f else 0f, tween(350), label = "ft")
    val listen by animateFloatAsState(if (state == State.LISTENING) 1f else 0f, tween(300), label = "fl")
    val speak by animateFloatAsState(if (state == State.SPEAKING) 1f else 0f, tween(250), label = "fs")
    val sad by animateFloatAsState(if (error && state == State.IDLE) 1f else 0f, tween(400), label = "fd")
    val tint by animateColorAsState(faceColor(state), tween(400), label = "fc")
    val levelFn by rememberUpdatedState(level)
    val act by rememberUpdatedState(activity)
    var phase by remember { mutableFloatStateOf(0f) }
    var lv by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { n ->
            if (last != 0L) {
                phase += (n - last) / 1_000_000_000f
                val tg = levelFn()
                lv += (tg - lv) * (if (tg > lv) 0.45f else 0.08f)
            }
            last = n
        }
    }

    Box(modifier.pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() }) }) {
        Canvas(Modifier.fillMaxSize()) {
            val t = phase
            val L = lv.coerceIn(0f, 1f)
            val w = size.width
            val h = size.height
            val dp = 1.dp.toPx()
            val cx = w / 2f
            val cy = h * 0.47f + sin(t * 1.3f) * 2f * dp
            val calm = (1f - think - listen - speak - sad).coerceIn(0f, 1f)
            val a = act
            val tc = lerp(tint, Color(0xFF8A97B3), sad)

            val fw = min(w * 0.86f, h * 0.6f / 0.72f)
            val fh = fw * 0.72f
            val left = cx - fw / 2f
            val top = cy - fh / 2f
            val r = fh * 0.3f

            val aura = fw * 0.8f
            drawCircle(
                Brush.radialGradient(listOf(tc.copy(alpha = 0.16f + 0.14f * L * speak), Color.Transparent), Offset(cx, cy), aura),
                aura, Offset(cx, cy)
            )

            // antena
            val tilt = sin(t * 1.1f) * fw * 0.01f
            drawLine(Color(0xFF3B4A6B), Offset(cx, top), Offset(cx + tilt, top - fh * 0.13f), 3f * dp, StrokeCap.Round)
            val ballA = 0.6f + 0.4f * (0.5f + 0.5f * sin(t * (2f + think * 4f)))
            drawCircle(tc.copy(alpha = 0.25f * ballA), fh * 0.07f, Offset(cx + tilt, top - fh * 0.15f))
            drawCircle(tc.copy(alpha = ballA), fh * 0.04f, Offset(cx + tilt, top - fh * 0.15f))

            // orelhas
            for (side in listOf(-1f, 1f)) {
                val ex = if (side < 0f) left - fw * 0.035f else left + fw
                drawRoundRect(tc.copy(alpha = 0.55f), Offset(ex, cy - fh * 0.12f), Size(fw * 0.035f, fh * 0.24f), CornerRadius(fw * 0.015f, fw * 0.015f))
            }

            // carinha
            drawRoundRect(Color(0xFF0E1525), Offset(left, top), Size(fw, fh), CornerRadius(r, r))
            drawRoundRect(tc.copy(alpha = 0.55f), Offset(left, top), Size(fw, fh), CornerRadius(r, r), style = Stroke(2f * dp))
            drawRoundRect(Color.White.copy(alpha = 0.05f), Offset(left + 4f * dp, top + 4f * dp), Size(fw - 8f * dp, fh * 0.32f), CornerRadius(r * 0.8f, r * 0.8f))

            // piscadas e momentos felizes
            val bc1 = t % 4.1f
            val bc2 = (t + 1.9f) % 6.7f
            val b1 = if (bc1 < 0.15f) sin(bc1 / 0.15f * 3.1415927f) else 0f
            val b2 = if (bc2 < 0.15f) sin(bc2 / 0.15f * 3.1415927f) else 0f
            val blink = maxOf(b1, b2)
            val cyc = t % 11f
            val happy = calm > 0.5f && cyc > 8.2f && cyc < 9.6f

            // olhos (o olhar muda conforme a tarefa)
            val ew = fw * 0.15f
            val openH = 1f + listen * 0.12f + listen * 0.18f * L - think * 0.30f - speak * 0.06f - sad * 0.2f
            val eh = (fw * 0.2f * openH * (1f - blink * 0.92f)).coerceAtLeast(2f * dp)
            val gxA = when (a) {
                "web" -> sin(t * 5f) * 0.9f
                "code", "file", "term" -> sin(t * 1.6f) * 0.8f
                "screen", "phone" -> sin(t * 1.2f) * 0.9f
                "retry" -> -0.7f
                else -> sin(t * 2.2f) * 0.7f
            }
            val gyA = when (a) {
                "web" -> 0.1f
                "code", "file", "term" -> 0.5f
                "screen", "phone" -> -0.1f
                "retry" -> -0.4f
                else -> -0.55f
            }
            val gx = calm * sin(t * 0.55f) * 0.7f + think * gxA + speak * sin(t * 0.9f) * 0.2f
            val gy = calm * sin(t * 0.37f + 1f) * 0.3f + think * gyA + sad * 0.6f
            val eyeY = cy - fh * 0.1f + gy * fh * 0.05f
            for (side in listOf(-1f, 1f)) {
                val ex = cx + side * fw * 0.2f + gx * fw * 0.035f
                if (happy) {
                    drawArc(tc, 200f, 140f, false, Offset(ex - ew * 0.6f, eyeY - ew * 0.15f), Size(ew * 1.2f, ew * 1.2f), style = Stroke(ew * 0.3f, cap = StrokeCap.Round))
                } else {
                    drawRoundRect(tc.copy(alpha = 0.2f), Offset(ex - ew * 0.78f, eyeY - eh * 0.78f), Size(ew * 1.56f, eh * 1.56f), CornerRadius(ew * 0.78f, ew * 0.78f))
                    drawRoundRect(tc, Offset(ex - ew / 2f, eyeY - eh / 2f), Size(ew, eh), CornerRadius(ew * 0.5f, ew * 0.5f))
                    if (eh > ew * 0.6f) drawCircle(Color.White.copy(alpha = 0.85f), ew * 0.13f, Offset(ex - ew * 0.16f, eyeY - eh * 0.22f))
                    if (sad > 0.02f) {
                        // sobrancelha caída
                        val lid = Path().apply {
                            moveTo(ex - side * ew * 0.9f, eyeY - eh * 0.7f); lineTo(ex + side * ew * 0.9f, eyeY - eh * 0.7f)
                            lineTo(ex - side * ew * 0.9f, eyeY - eh * (0.7f - 0.7f * sad)); close()
                        }
                        drawPath(lid, Color(0xFF0E1525))
                    }
                }
            }
            if (sad > 0.02f) {
                val ty = eyeY + eh * 0.7f + ((t * 0.6f) % 1f) * fh * 0.14f
                drawCircle(Color(0xFF6EC1FF).copy(alpha = sad), fw * 0.012f, Offset(cx - fw * 0.2f + ew * 0.3f, ty))
            }

            // bochechas
            for (side in listOf(-1f, 1f)) {
                val al = 0.16f + (if (happy) 0.2f else 0f) + 0.1f * speak
                drawOval(Color(0xFFFF8FA3).copy(alpha = al), Offset(cx + side * fw * 0.31f - fw * 0.04f, cy + fh * 0.12f), Size(fw * 0.08f, fw * 0.045f))
            }

            // boca
            val my = cy + fh * 0.22f
            val mw = fw * 0.2f
            val lw = fw * 0.024f
            if (calm + sad > 0.01f) {
                val smile = fh * (if (happy) 0.17f else 0.09f + 0.012f * sin(t * 1.4f))
                val curve = smile * (calm - sad * 0.8f)
                val p = Path().apply { moveTo(cx - mw, my); quadraticBezierTo(cx, my + curve, cx + mw, my) }
                drawPath(p, tc.copy(alpha = (calm + sad).coerceAtMost(1f)), style = Stroke(lw, cap = StrokeCap.Round))
            }
            if (listen > 0.01f) {
                drawCircle(tc.copy(alpha = listen), fw * 0.03f * (1f + 0.9f * L), Offset(cx, my), style = Stroke(lw))
            }
            if (think > 0.01f) {
                val wob = sin(t * 3f) * fh * 0.012f
                drawLine(tc.copy(alpha = think), Offset(cx - mw * 0.55f, my + wob), Offset(cx + mw * 0.55f, my - wob), lw, StrokeCap.Round)
                if (a.isEmpty()) {
                    for (k in 0..2) {
                        val by = top + fh * 0.17f - abs(sin(t * 4f + k * 0.9f)) * fh * 0.05f
                        drawCircle(tc.copy(alpha = think), fw * 0.013f, Offset(left + fw * 0.8f + k * fw * 0.05f, by))
                    }
                } else {
                    val br = fh * 0.17f
                    badge(a, Offset(left + fw * 0.88f, top - fh * 0.04f), br * think.coerceAtLeast(0.01f), t, tc)
                }
            }
            if (speak > 0.01f) {
                val mo = (0.15f + 0.85f * L) * (0.7f + 0.3f * abs(sin(t * 9f)))
                val mh = fh * (0.035f + 0.2f * mo)
                val mwd = fw * (0.17f + 0.06f * mo)
                drawRoundRect(tc.copy(alpha = speak), Offset(cx - mwd / 2f, my - mh / 2f + mh * 0.2f), Size(mwd, mh), CornerRadius(mh * 0.5f, mh * 0.5f))
            }
        }
    }
}
