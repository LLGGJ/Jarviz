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

private fun lerpF(a: Float, b: Float, k: Float): Float = a + (b - a) * k.coerceIn(0f, 1f)

/** Selo ao lado da cabeça com o que o robô está fazendo: web, código, arquivos, terminal, celular, tela. */
private fun DrawScope.badge(kind: String, c: Offset, r: Float, t: Float, col: Color) {
    val sw = r * 0.16f
    val st = Stroke(sw, cap = StrokeCap.Round)
    drawCircle(Color(0xFF0E1525), r, c)
    drawCircle(col.copy(alpha = 0.8f), r, c, style = Stroke(sw * 0.7f))
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
 * Robozinho de mesa (inspirado no Eilik e no Emo): cabeça branca com tela preta, olhos que brilham, corpinho com braços e luz no peito.
 * Sem texto de status: o que ele faz aparece na expressão e nos braços.
 * activity: "", "web", "code", "file", "term", "phone", "screen", "retry" ou "work". mood: "", "angry", "scared", "sleepy", "joy".
 */
@Composable
fun RobotFace(
    state: State, level: () -> Float, activity: String, error: Boolean, modifier: Modifier = Modifier,
    onTap: () -> Unit = {}, onLong: () -> Unit = {}, mood: String = ""
) {
    val think by animateFloatAsState(if (state == State.THINKING) 1f else 0f, tween(350), label = "ft")
    val listen by animateFloatAsState(if (state == State.LISTENING) 1f else 0f, tween(300), label = "fl")
    val speak by animateFloatAsState(if (state == State.SPEAKING) 1f else 0f, tween(250), label = "fs")
    val sad by animateFloatAsState(if (error && state == State.IDLE) 1f else 0f, tween(400), label = "fd")
    val angry by animateFloatAsState(if (mood == "angry") 1f else 0f, tween(200), label = "fa")
    val scared by animateFloatAsState(if (mood == "scared") 1f else 0f, tween(150), label = "fx")
    val sleepy by animateFloatAsState(if (mood == "sleepy") 1f else 0f, tween(600), label = "fz")
    val joy by animateFloatAsState(if (mood == "joy") 1f else 0f, tween(200), label = "fj")
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
            val calm = (1f - think - listen - speak - sad - angry - scared - sleepy - joy).coerceIn(0f, 1f)
            val a = act
            val tc = lerp(lerp(lerp(tint, Color(0xFF8A97B3), sad), Color(0xFFFF5A5A), angry), Color(0xFF9AA7C7), sleepy * 0.5f)
            val shell = Color(0xFFE9EEF7)
            val shade = Color(0xFFB9C5DA)
            val dark = Color(0xFF04070D)

            val fw = min(w * 0.72f, h * 0.44f / 0.74f)
            val fh = fw * 0.74f
            val cx = w / 2f + sin(t * 55f) * 2f * dp * (angry + scared)
            val cy = h * 0.36f + sin(t * 1.3f) * 2f * dp
            val left = cx - fw / 2f
            val top = cy - fh / 2f
            val r = fh * 0.3f
            val bw = fw * 0.66f
            val bh = fh * 0.82f
            val bx = cx - bw / 2f
            val byy = top + fh * 0.9f

            // brilho no fundo e sombra no chão
            val aura = fw * 0.9f
            drawCircle(Brush.radialGradient(listOf(tc.copy(alpha = 0.16f + 0.14f * L * speak), Color.Transparent), Offset(cx, cy), aura), aura, Offset(cx, cy))
            drawOval(Color.Black.copy(alpha = 0.35f), Offset(cx - bw * 0.75f, byy + bh * 0.96f), Size(bw * 1.5f, bh * 0.14f))

            // braços (os gestos mostram o que ele sente)
            val cyc = t % 11f
            val happy = (calm > 0.5f && cyc > 8.2f && cyc < 9.6f) || joy > 0.5f
            val base = 22f + sin(t * 1.5f) * 4f
            var aL = base
            var aR = base
            aR = lerpF(aR, 70f + sin(t * 7f) * (14f + 24f * L), speak)
            aR = lerpF(aR, 142f, think)
            aL = lerpF(aL, 45f, listen); aR = lerpF(aR, 45f, listen)
            val wave = 150f + sin(t * 9f) * 18f
            aL = lerpF(aL, wave, joy); aR = lerpF(aR, wave, joy)
            aL = lerpF(aL, 160f + sin(t * 30f) * 6f, scared); aR = lerpF(aR, 160f + sin(t * 30f) * 6f, scared)
            aL = lerpF(aL, 10f + sin(t * 40f) * 3f, angry); aR = lerpF(aR, 10f + sin(t * 40f) * 3f, angry)
            aL = lerpF(aL, 6f, sleepy); aR = lerpF(aR, 6f, sleepy)
            if (happy && joy < 0.5f) aR = wave
            for (side in listOf(-1f, 1f)) {
                val deg = if (side < 0f) aL else aR
                val rad = Math.toRadians(deg.toDouble()).toFloat()
                val sx = cx + side * bw * 0.5f
                val sy = byy + bh * 0.22f
                val len = bh * 0.55f
                val ex = sx + side * sin(rad) * len
                val ey = sy + cos(rad) * len
                drawLine(shade, Offset(sx, sy), Offset(ex, ey), bw * 0.17f, StrokeCap.Round)
                drawLine(shell, Offset(sx, sy), Offset(ex, ey), bw * 0.11f, StrokeCap.Round)
                drawCircle(tc, bw * 0.095f, Offset(ex, ey))
            }

            // corpo e luz do peito
            drawRoundRect(Brush.verticalGradient(listOf(shell, shade), startY = byy, endY = byy + bh), Offset(bx, byy), Size(bw, bh), CornerRadius(bw * 0.3f, bw * 0.3f))
            val pulse = 0.55f + 0.45f * sin(t * (2f + think * 4f))
            drawCircle(tc.copy(alpha = 0.25f * pulse), bw * 0.14f, Offset(cx, byy + bh * 0.4f))
            drawCircle(tc.copy(alpha = pulse), bw * 0.07f, Offset(cx, byy + bh * 0.4f))

            // antena
            val tilt = sin(t * 1.1f) * fw * 0.01f
            drawLine(shade, Offset(cx, top + 2f * dp), Offset(cx + tilt, top - fh * 0.13f), 3f * dp, StrokeCap.Round)
            val ballA = 0.6f + 0.4f * (0.5f + 0.5f * sin(t * (2f + think * 4f)))
            drawCircle(tc.copy(alpha = 0.3f * ballA), fh * 0.07f, Offset(cx + tilt, top - fh * 0.15f))
            drawCircle(tc.copy(alpha = ballA), fh * 0.04f, Offset(cx + tilt, top - fh * 0.15f))

            // fones laterais e casca da cabeça
            for (side in listOf(-1f, 1f)) {
                val ex = if (side < 0f) left - fw * 0.045f else left + fw - fw * 0.015f
                drawRoundRect(shade, Offset(ex, cy - fh * 0.14f), Size(fw * 0.06f, fh * 0.28f), CornerRadius(fw * 0.03f, fw * 0.03f))
                drawRoundRect(tc.copy(alpha = 0.7f), Offset(ex + fw * 0.012f, cy - fh * 0.07f), Size(fw * 0.036f, fh * 0.14f), CornerRadius(fw * 0.018f, fw * 0.018f))
            }
            drawRoundRect(Brush.verticalGradient(listOf(Color.White, shell, shade), startY = top, endY = top + fh), Offset(left, top), Size(fw, fh), CornerRadius(r, r))

            // tela preta
            val sx0 = left + fw * 0.07f
            val sy0 = top + fh * 0.1f
            val sw = fw * 0.86f
            val sh = fh * 0.8f
            val sr = r * 0.75f
            drawRoundRect(dark, Offset(sx0, sy0), Size(sw, sh), CornerRadius(sr, sr))
            drawRoundRect(tc.copy(alpha = 0.5f), Offset(sx0, sy0), Size(sw, sh), CornerRadius(sr, sr), style = Stroke(2f * dp))
            drawRoundRect(Color.White.copy(alpha = 0.05f), Offset(sx0 + sw * 0.05f, sy0 + sh * 0.04f), Size(sw * 0.9f, sh * 0.26f), CornerRadius(sr * 0.8f, sr * 0.8f))

            // piscadas
            val bc1 = t % 4.1f
            val bc2 = (t + 1.9f) % 6.7f
            val b1 = if (bc1 < 0.15f) sin(bc1 / 0.15f * 3.1415927f) else 0f
            val b2 = if (bc2 < 0.15f) sin(bc2 / 0.15f * 3.1415927f) else 0f
            val blink = maxOf(b1, b2)

            // olhos (o olhar muda conforme a tarefa)
            val ew = sw * 0.17f
            val openH = 1f + listen * 0.12f + listen * 0.18f * L - think * 0.30f - speak * 0.06f - sad * 0.2f - angry * 0.15f + scared * 0.45f - sleepy * 0.88f
            val eh = (sw * 0.23f * openH * (1f - blink * 0.92f)).coerceAtLeast(2f * dp)
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
            val eyeY = sy0 + sh * 0.4f + gy * sh * 0.05f
            for (side in listOf(-1f, 1f)) {
                val ex = cx + side * sw * 0.23f + gx * sw * 0.03f
                if (happy) {
                    drawArc(tc, 200f, 140f, false, Offset(ex - ew * 0.6f, eyeY - ew * 0.15f), Size(ew * 1.2f, ew * 1.2f), style = Stroke(ew * 0.3f, cap = StrokeCap.Round))
                } else {
                    val top0 = eyeY - eh / 2f
                    drawRoundRect(tc.copy(alpha = 0.18f), Offset(ex - ew * 0.8f, eyeY - eh * 0.8f), Size(ew * 1.6f, eh * 1.6f), CornerRadius(ew * 0.8f, ew * 0.8f))
                    drawRoundRect(
                        Brush.verticalGradient(listOf(lerp(tc, Color.White, 0.55f), tc), startY = top0, endY = top0 + eh),
                        Offset(ex - ew / 2f, top0), Size(ew, eh), CornerRadius(ew * 0.5f, ew * 0.5f)
                    )
                    if (eh > ew * 0.6f) drawCircle(Color.White.copy(alpha = 0.9f), ew * 0.13f, Offset(ex - ew * 0.16f, eyeY - eh * 0.22f))
                    val outer = ex + side * ew * 0.62f
                    val inner = ex - side * ew * 0.62f
                    if (angry > 0.02f) {
                        val lid = Path().apply { moveTo(outer, top0 - 2f * dp); lineTo(inner, top0 - 2f * dp); lineTo(inner, top0 + eh * 0.55f * angry); close() }
                        drawPath(lid, dark)
                    }
                    if (sad > 0.02f) {
                        val lid = Path().apply { moveTo(outer, top0 - 2f * dp); lineTo(inner, top0 - 2f * dp); lineTo(outer, top0 + eh * 0.5f * sad); close() }
                        drawPath(lid, dark)
                    }
                }
            }
            if (sad > 0.02f) {
                val ty = eyeY + eh * 0.7f + ((t * 0.6f) % 1f) * sh * 0.14f
                drawCircle(Color(0xFF6EC1FF).copy(alpha = sad), sw * 0.014f, Offset(cx - sw * 0.23f + ew * 0.3f, ty))
            }
            if (angry > 0.02f) {
                for (k in 0..1) {
                    val pp = (t * 1.2f + k * 0.5f) % 1f
                    drawCircle(Color(0xFFFFB0B0).copy(alpha = angry * (1f - pp) * 0.8f), fh * 0.04f * (1f + pp), Offset(cx + (k * 2 - 1) * fw * 0.3f, top - pp * fh * 0.2f))
                }
            }
            if (scared > 0.02f) {
                val dy = ((t * 0.8f) % 1f) * fh * 0.25f
                drawCircle(Color(0xFF6EC1FF).copy(alpha = scared * 0.9f), fw * 0.02f, Offset(left + fw * 0.1f, top + fh * 0.15f + dy))
                drawCircle(Color(0xFF6EC1FF).copy(alpha = scared * 0.9f), fw * 0.016f, Offset(left + fw * 0.92f, top + fh * 0.25f + dy * 0.7f))
            }
            if (sleepy > 0.02f) {
                for (k in 0..2) {
                    val pp = (t * 0.5f + k * 0.33f) % 1f
                    val zx = left + fw * 0.8f + pp * fw * 0.1f
                    val zy = top + fh * 0.05f - pp * fh * 0.25f
                    val zs = fh * 0.06f * (0.6f + pp)
                    val zc = tc.copy(alpha = sleepy * (1f - pp))
                    drawLine(zc, Offset(zx, zy), Offset(zx + zs, zy), 2f * dp, StrokeCap.Round)
                    drawLine(zc, Offset(zx + zs, zy), Offset(zx, zy + zs), 2f * dp, StrokeCap.Round)
                    drawLine(zc, Offset(zx, zy + zs), Offset(zx + zs, zy + zs), 2f * dp, StrokeCap.Round)
                }
            }

            // bochechas
            for (side in listOf(-1f, 1f)) {
                val al = 0.16f + (if (happy) 0.2f else 0f) + 0.1f * speak
                drawOval(Color(0xFFFF8FA3).copy(alpha = al), Offset(cx + side * sw * 0.36f - sw * 0.05f, sy0 + sh * 0.62f), Size(sw * 0.1f, sw * 0.055f))
            }

            // boca
            val my = sy0 + sh * 0.8f
            val mw = sw * 0.2f
            val lw = sw * 0.028f
            if (calm + sad + angry + scared + joy > 0.01f) {
                val smile = sh * (if (happy) 0.2f else 0.1f + 0.012f * sin(t * 1.4f))
                val curve = smile * (calm - sad * 0.8f - angry * 1.2f - scared * 0.6f + joy * 1.5f)
                val p = Path().apply { moveTo(cx - mw, my); quadraticBezierTo(cx, my + curve, cx + mw, my) }
                drawPath(p, tc.copy(alpha = (calm + sad + angry + scared + joy).coerceAtMost(1f)), style = Stroke(lw, cap = StrokeCap.Round))
            }
            if (listen > 0.01f) {
                drawCircle(tc.copy(alpha = listen), sw * 0.035f * (1f + 0.9f * L), Offset(cx, my), style = Stroke(lw))
            }
            if (think > 0.01f) {
                val wob = sin(t * 3f) * sh * 0.012f
                drawLine(tc.copy(alpha = think), Offset(cx - mw * 0.55f, my + wob), Offset(cx + mw * 0.55f, my - wob), lw, StrokeCap.Round)
                if (a.isEmpty()) {
                    for (k in 0..2) {
                        val by2 = sy0 + sh * 0.14f - abs(sin(t * 4f + k * 0.9f)) * sh * 0.05f
                        drawCircle(tc.copy(alpha = think), sw * 0.016f, Offset(sx0 + sw * 0.78f + k * sw * 0.06f, by2))
                    }
                } else {
                    badge(a, Offset(left + fw * 0.93f, top - fh * 0.05f), fh * 0.17f * think.coerceAtLeast(0.01f), t, tc)
                }
            }
            if (speak > 0.01f) {
                val mo = (0.15f + 0.85f * L) * (0.7f + 0.3f * abs(sin(t * 9f)))
                val mh = sh * (0.04f + 0.2f * mo)
                val mwd = sw * (0.17f + 0.06f * mo)
                drawRoundRect(tc.copy(alpha = speak), Offset(cx - mwd / 2f, my - mh / 2f + mh * 0.2f), Size(mwd, mh), CornerRadius(mh * 0.5f, mh * 0.5f))
            }
        }
    }
}
