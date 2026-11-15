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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

private fun faceColor(s: State): Color = when (s) {
    State.IDLE -> Color(0xFF8EC8FF)
    State.LISTENING -> Color(0xFF4DE3C2)
    State.THINKING -> Color(0xFFB5A6FF)
    State.SPEAKING -> Color(0xFFFFB454)
}

/**
 * Rostinho de robô fofo, no lugar do holograma.
 * Repouso: pisca, olha pros lados, sorri e de vez em quando faz "olhinhos felizes".
 * Escutando: olhos grandes e boquinha "o". Pensando: olha pra cima, boca reta e pontinhos.
 * Falando: boca abre e fecha no ritmo da voz. Toque = falar; segurar = troca o formato dos olhos.
 */
@Composable
fun RobotFace(
    state: State, level: () -> Float, skin: String, modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp, onTap: () -> Unit = {}, onLong: () -> Unit = {}
) {
    val think by animateFloatAsState(if (state == State.THINKING) 1f else 0f, tween(350), label = "ft")
    val listen by animateFloatAsState(if (state == State.LISTENING) 1f else 0f, tween(300), label = "fl")
    val speak by animateFloatAsState(if (state == State.SPEAKING) 1f else 0f, tween(250), label = "fs")
    val tint by animateColorAsState(faceColor(state), tween(400), label = "fc")
    val levelFn by rememberUpdatedState(level)
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
            val ah = h - bottomInset.toPx()
            val cx = w / 2f
            val cy = ah / 2f + sin(t * 1.3f) * 2f * dp
            val calm = (1f - think - listen - speak).coerceIn(0f, 1f)

            val fw = min(w * 0.84f, ah * 0.78f / 0.72f)
            val fh = fw * 0.72f
            val left = cx - fw / 2f
            val top = cy - fh / 2f
            val r = fh * 0.3f

            // brilho de fundo
            val aura = fw * 0.75f
            drawCircle(
                Brush.radialGradient(listOf(tint.copy(alpha = 0.16f + 0.14f * L * speak), Color.Transparent), Offset(cx, cy), aura),
                aura, Offset(cx, cy)
            )

            // antena
            val tilt = sin(t * 1.1f) * fw * 0.01f
            drawLine(Color(0xFF3B4A6B), Offset(cx, top), Offset(cx + tilt, top - fh * 0.13f), 3f * dp, StrokeCap.Round)
            val ballA = 0.6f + 0.4f * (0.5f + 0.5f * sin(t * (2f + think * 4f)))
            drawCircle(tint.copy(alpha = 0.25f * ballA), fh * 0.07f, Offset(cx + tilt, top - fh * 0.15f))
            drawCircle(tint.copy(alpha = ballA), fh * 0.04f, Offset(cx + tilt, top - fh * 0.15f))

            // orelhas
            for (side in listOf(-1f, 1f)) {
                val ex = if (side < 0f) left - fw * 0.035f else left + fw
                drawRoundRect(tint.copy(alpha = 0.55f), Offset(ex, cy - fh * 0.12f), Size(fw * 0.035f, fh * 0.24f), CornerRadius(fw * 0.015f, fw * 0.015f))
            }

            // carinha (tela escura com borda colorida)
            drawRoundRect(Color(0xFF0E1525), Offset(left, top), Size(fw, fh), CornerRadius(r, r))
            drawRoundRect(tint.copy(alpha = 0.55f), Offset(left, top), Size(fw, fh), CornerRadius(r, r), style = Stroke(2f * dp))
            drawRoundRect(Color.White.copy(alpha = 0.05f), Offset(left + 4f * dp, top + 4f * dp), Size(fw - 8f * dp, fh * 0.32f), CornerRadius(r * 0.8f, r * 0.8f))

            // piscadas e momentos felizes
            val bc1 = t % 4.1f
            val bc2 = (t + 1.9f) % 6.7f
            val b1 = if (bc1 < 0.15f) sin(bc1 / 0.15f * 3.1415927f) else 0f
            val b2 = if (bc2 < 0.15f) sin(bc2 / 0.15f * 3.1415927f) else 0f
            val blink = maxOf(b1, b2)
            val cyc = t % 11f
            val happy = calm > 0.5f && cyc > 8.2f && cyc < 9.6f

            // olhos
            val round = skin == "pulse"
            val corner = if (skin == "wave") 0.3f else 0.5f
            val ew = fw * (if (round) 0.17f else 0.15f)
            val openH = 1f + listen * 0.12f + listen * 0.18f * L - think * 0.30f - speak * 0.06f
            val baseH = if (round) ew else fw * 0.2f
            val eh = (baseH * openH * (1f - blink * 0.92f)).coerceAtLeast(2f * dp)
            val gx = calm * sin(t * 0.55f) * 0.7f + think * sin(t * 2.2f) * 0.7f + speak * sin(t * 0.9f) * 0.2f
            val gy = calm * sin(t * 0.37f + 1f) * 0.3f - think * 0.55f
            val eyeY = cy - fh * 0.1f + gy * fh * 0.05f
            for (side in listOf(-1f, 1f)) {
                val ex = cx + side * fw * 0.2f + gx * fw * 0.035f
                if (happy) {
                    drawArc(tint, 200f, 140f, false, Offset(ex - ew * 0.6f, eyeY - ew * 0.15f), Size(ew * 1.2f, ew * 1.2f), style = Stroke(ew * 0.3f, cap = StrokeCap.Round))
                } else {
                    drawRoundRect(tint.copy(alpha = 0.2f), Offset(ex - ew * 0.78f, eyeY - eh * 0.78f), Size(ew * 1.56f, eh * 1.56f), CornerRadius(ew * 0.78f, ew * 0.78f))
                    drawRoundRect(tint, Offset(ex - ew / 2f, eyeY - eh / 2f), Size(ew, eh), CornerRadius(ew * corner, ew * corner))
                    if (eh > ew * 0.6f) drawCircle(Color.White.copy(alpha = 0.85f), ew * 0.13f, Offset(ex - ew * 0.16f, eyeY - eh * 0.22f))
                }
            }

            // bochechas
            for (side in listOf(-1f, 1f)) {
                val a = 0.16f + (if (happy) 0.2f else 0f) + 0.1f * speak
                drawOval(Color(0xFFFF8FA3).copy(alpha = a), Offset(cx + side * fw * 0.31f - fw * 0.04f, cy + fh * 0.12f), Size(fw * 0.08f, fw * 0.045f))
            }

            // boca
            val my = cy + fh * 0.22f
            val mw = fw * 0.2f
            val lw = fw * 0.024f
            if (calm > 0.01f) {
                val curve = fh * (if (happy) 0.17f else 0.09f + 0.012f * sin(t * 1.4f))
                val p = Path().apply { moveTo(cx - mw, my); quadraticBezierTo(cx, my + curve, cx + mw, my) }
                drawPath(p, tint.copy(alpha = calm), style = Stroke(lw, cap = StrokeCap.Round))
            }
            if (listen > 0.01f) {
                drawCircle(tint.copy(alpha = listen), fw * 0.03f * (1f + 0.9f * L), Offset(cx, my), style = Stroke(lw))
            }
            if (think > 0.01f) {
                val wob = sin(t * 3f) * fh * 0.012f
                drawLine(tint.copy(alpha = think), Offset(cx - mw * 0.55f, my + wob), Offset(cx + mw * 0.55f, my - wob), lw, StrokeCap.Round)
                for (k in 0..2) {
                    val by = top + fh * 0.17f - abs(sin(t * 4f + k * 0.9f)) * fh * 0.05f
                    drawCircle(tint.copy(alpha = think), fw * 0.013f, Offset(left + fw * 0.8f + k * fw * 0.05f, by))
                }
            }
            if (speak > 0.01f) {
                val mo = (0.15f + 0.85f * L) * (0.7f + 0.3f * abs(sin(t * 9f)))
                val mh = fh * (0.035f + 0.2f * mo)
                val mwd = fw * (0.17f + 0.06f * mo)
                drawRoundRect(tint.copy(alpha = speak), Offset(cx - mwd / 2f, my - mh / 2f + mh * 0.2f), Size(mwd, mh), CornerRadius(mh * 0.5f, mh * 0.5f))
            }
        }
    }
}
