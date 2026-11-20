package com.jarvis.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private fun faceColor(s: State): Color = when (s) {
    State.IDLE -> Color(0xFF8EC8FF)
    State.LISTENING -> Color(0xFF4DE3C2)
    State.THINKING -> Color(0xFFB5A6FF)
    State.SPEAKING -> Color(0xFFFFB454)
}

private val SAD = Color(0xFF8A97B3)
private val RED = Color(0xFFFF5A5A)
private val SLEEPC = Color(0xFF9AA7C7)
private val SHELL = Color(0xFFE9EEF7)
private val SHADE = Color(0xFFB9C5DA)
private val DARK = Color(0xFF04070D)
private val WHITE = Color.White

/** Mola amortecida: dá o movimento orgânico (leve "balanço" ao chegar). Cálculo simples, sem alocar nada por quadro. */
private class Spring(var v: Float = 0f, var target: Float = 0f, private val k: Float = 170f, private val c: Float = 17f) {
    private var vel = 0f
    fun step(dt: Float) { vel += (k * (target - v) - c * vel) * dt; v += vel * dt }
    fun w(): Float = v.coerceIn(0f, 1f)
    fun kick(x: Float) { vel += x }
}

private fun weight(k: Float = 120f) = Spring(k = k, c = 2f * kotlin.math.sqrt(k))
private fun lerpF(a: Float, b: Float, k: Float): Float = a + (b - a) * k.coerceIn(0f, 1f)

/** Todos os parâmetros animados do robô, atualizados no laço de quadros (não causa recomposição). */
private class Rig {
    val think = weight(); val listen = weight(); val speak = weight(180f); val sad = weight(); val angry = weight(200f)
    val scared = weight(260f); val sleepy = weight(60f); val joy = weight(200f); val curious = weight(); val love = weight()
    val surprised = weight(300f); val dizzy = weight(); val bored = weight(60f)
    val gx = Spring(k = 260f, c = 24f); val gy = Spring(k = 260f, c = 24f)
    val blink = Spring(k = 700f, c = 48f)
    val mOpen = Spring(k = 420f, c = 30f); val mWide = Spring(k = 300f, c = 26f)
    val armL = Spring(k = 140f, c = 10f); val armR = Spring(k = 140f, c = 10f)
    val tilt = Spring(k = 90f, c = 11f); val squash = Spring(k = 230f, c = 9f)
    var t = 0f
    private var blinkT = 0f
    private var nextBlink = 2f
    private var nextSacc = 1f
    private var sacX = 0f
    private var sacY = 0f
    private var lastState = State.IDLE
    private var level = 0f
    private var slow = 0f
    private var prevRaw = 0f
    private var sinceOnset = 1f
    private var viseme = 0
    var envelope = 0f

    fun calm(): Float = (1f - think.w() - listen.w() - speak.w() - sad.w() - angry.w() - scared.w() - sleepy.w() - joy.w() -
        curious.w() - love.w() - surprised.w() - dizzy.w() - bored.w()).coerceIn(0f, 1f)

    fun update(dt: Float, st: State, mood: String, act: String, err: Boolean, raw: Float, tx: Float, ty: Float, touching: Boolean) {
        t += dt
        val idle = st == State.IDLE
        think.target = if (st == State.THINKING) 1f else 0f
        listen.target = if (st == State.LISTENING) 1f else 0f
        speak.target = if (st == State.SPEAKING) 1f else 0f
        sad.target = if (err && idle) 1f else 0f
        angry.target = if (mood == "angry") 1f else 0f
        scared.target = if (mood == "scared") 1f else 0f
        sleepy.target = if (mood == "sleepy") 1f else 0f
        joy.target = if (mood == "joy") 1f else 0f
        curious.target = if (mood == "curious") 1f else 0f
        love.target = if (mood == "love") 1f else 0f
        surprised.target = if (mood == "surprised") 1f else 0f
        dizzy.target = if (mood == "dizzy") 1f else 0f
        bored.target = if (mood == "bored") 1f else 0f
        for (s in arrayOf(think, listen, speak, sad, angry, scared, sleepy, joy, curious, love, surprised, dizzy, bored)) s.step(dt)
        if (st != lastState) { squash.kick(if (st == State.IDLE) -2f else 2.6f); lastState = st }

        // áudio: ataque rápido, soltura lenta; detecta "sílabas" para trocar o formato da boca
        level += (raw - level) * (1f - exp(-dt * (if (raw > level) 32f else 9f)))
        slow += (raw - slow) * (1f - exp(-dt * 2.5f))
        envelope = slow
        sinceOnset += dt
        if (raw - prevRaw > 0.1f && sinceOnset > 0.09f) { viseme = (viseme + 1 + Random.nextInt(2)) % 4; sinceOnset = 0f }
        prevRaw = raw

        // piscadas
        if (blinkT > 0f) { blinkT -= dt; blink.target = 1f } else blink.target = 0f
        if (t > nextBlink) {
            blinkT = 0.09f
            nextBlink = t + (if (Random.nextFloat() < 0.15f) 0.25f else 2f + Random.nextFloat() * 3.2f)
        }
        blink.step(dt)

        // olhar: sacadas aleatórias; segue o dedo; padrões por tarefa
        if (t > nextSacc) {
            sacX = (Random.nextFloat() - 0.5f) * 1.4f
            sacY = (Random.nextFloat() - 0.5f) * 0.6f
            nextSacc = t + 0.7f + Random.nextFloat() * 1.9f
        }
        val gxA = when (act) {
            "web" -> sin(t * 5f) * 0.9f
            "code", "file", "term" -> sin(t * 1.6f) * 0.8f
            "screen", "phone" -> sin(t * 1.2f) * 0.9f
            "retry" -> -0.7f
            else -> sin(t * 2.2f) * 0.7f
        }
        val gyA = when (act) { "web" -> 0.1f; "code", "file", "term" -> 0.5f; "screen", "phone" -> -0.1f; "retry" -> -0.4f; else -> -0.55f }
        val ca = calm()
        var gxT = ca * sacX + think.w() * gxA + speak.w() * sin(t * 0.9f) * 0.2f + curious.w() * 0.9f + bored.w() * sin(t * 0.7f) * 0.8f
        var gyT = ca * sacY + think.w() * gyA + sad.w() * 0.6f + sleepy.w() * 0.7f + dizzy.w() * sin(t * 6f) * 0.4f
        if (touching) { gxT = ((tx - 0.5f) * 2.4f).coerceIn(-1f, 1f); gyT = ((ty - 0.4f) * 2f).coerceIn(-0.8f, 0.8f) }
        gx.target = gxT; gy.target = gyT
        gx.step(dt); gy.step(dt)

        // boca: formas de fala (A, O, E, M) no ritmo do áudio
        val sp = speak.w()
        val vo = when (viseme) { 0 -> 1f; 1 -> 0.8f; 2 -> 0.55f; else -> 0.12f }
        val vw = when (viseme) { 0 -> 1f; 1 -> 0.45f; 2 -> 1.25f; else -> 0.85f }
        val yawn = if (bored.w() > 0.5f && (t % 7f) < 1.6f) 1f else 0f
        mOpen.target = sp * (0.08f + level * 1.15f * vo) + joy.w() * 0.45f + surprised.w() * 0.9f + scared.w() * 0.45f + listen.w() * 0.12f + yawn
        mWide.target = 1f + (vw - 1f) * sp - surprised.w() * 0.6f - love.w() * 0.2f
        mOpen.step(dt); mWide.step(dt)

        // braços e cabeça
        val base = 22f + sin(t * 1.5f) * 4f
        var aL = base
        var aR = base
        val wave = 150f + sin(t * 9f) * 18f
        aR = lerpF(aR, 70f + sin(t * 7f) * (14f + 24f * level), sp)
        aR = lerpF(aR, 142f, think.w())
        aL = lerpF(aL, 45f, listen.w()); aR = lerpF(aR, 45f, listen.w())
        aL = lerpF(aL, wave, joy.w()); aR = lerpF(aR, wave, joy.w())
        aL = lerpF(aL, 160f + sin(t * 30f) * 6f, scared.w()); aR = lerpF(aR, 160f + sin(t * 30f) * 6f, scared.w())
        aL = lerpF(aL, 10f + sin(t * 40f) * 3f, angry.w()); aR = lerpF(aR, 10f + sin(t * 40f) * 3f, angry.w())
        aL = lerpF(aL, 6f, sleepy.w()); aR = lerpF(aR, 6f, sleepy.w())
        aL = lerpF(aL, 120f, surprised.w()); aR = lerpF(aR, 120f, surprised.w())
        aL = lerpF(aL, 100f + sin(t * 2f) * 8f, love.w()); aR = lerpF(aR, 100f + sin(t * 2f) * 8f, love.w())
        if (joy.w() < 0.5f && calm() > 0.5f && (t % 11f) in 8.2f..9.6f) aR = wave
        armL.target = aL; armR.target = aR
        armL.step(dt); armR.step(dt)
        tilt.target = curious.w() * 9f - sad.w() * 4f + dizzy.w() * sin(t * 3f) * 9f + love.w() * sin(t * 2f) * 4f -
            sleepy.w() * 7f + bored.w() * sin(t * 0.6f) * 3f + ca * sin(t * 0.4f) * 1.5f + (if (touching) (tx - 0.5f) * 8f else 0f)
        tilt.step(dt)
        squash.target = 0f
        squash.step(dt)
    }
}

private fun heartPath(p: Path, cx: Float, cy: Float, s: Float) {
    p.reset()
    p.moveTo(cx, cy + s * 0.9f)
    p.cubicTo(cx - s * 1.6f, cy - s * 0.2f, cx - s * 0.7f, cy - s * 1.3f, cx, cy - s * 0.45f)
    p.cubicTo(cx + s * 0.7f, cy - s * 1.3f, cx + s * 1.6f, cy - s * 0.2f, cx, cy + s * 0.9f)
    p.close()
}

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

/** Desenha o robô inteiro. Só usa cores sólidas e formas (sem criar Brush/lista por quadro), então roda a 60 fps sem lixo na memória. */
private fun DrawScope.drawRobot(r: Rig, tint: Color, act: String, path: Path, errW: Float) {
    val t = r.t
    val w = size.width
    val h = size.height
    val dp = 1.dp.toPx()
    val think = r.think.w(); val listen = r.listen.w(); val speak = r.speak.w(); val sad = r.sad.w()
    val angry = r.angry.w(); val scared = r.scared.w(); val sleepy = r.sleepy.w(); val joy = r.joy.w()
    val curious = r.curious.w(); val love = r.love.w(); val surprised = r.surprised.w(); val dizzy = r.dizzy.w(); val bored = r.bored.w()
    val calm = r.calm()
    val L = r.envelope.coerceIn(0f, 1f)
    val tc = lerp(lerp(lerp(tint, SAD, sad), RED, angry), SLEEPC, sleepy * 0.5f)

    val fw = min(w * 0.72f, h * 0.44f / 0.74f)
    val fh = fw * 0.74f
    val cx = w / 2f + sin(t * 55f) * 2f * dp * (angry + scared)
    val cy = h * 0.36f + sin(t * 1.3f) * 2f * dp
    val left = cx - fw / 2f
    val top = cy - fh / 2f
    val rad = fh * 0.3f
    val bw = fw * 0.66f
    val bh = fh * 0.82f
    val bx = cx - bw / 2f
    val byy = top + fh * 0.9f

    // brilho de fundo (3 anéis, sem gradiente) e sombra no chão
    for (i in 1..3) drawCircle(tc.copy(alpha = (0.05f + 0.05f * L * speak) / i), fw * (0.55f + 0.2f * i), Offset(cx, cy))
    drawOval(Color.Black.copy(alpha = 0.35f), Offset(cx - bw * 0.75f, byy + bh * 0.96f), Size(bw * 1.5f, bh * 0.14f))

    // braços
    for (side in -1..1 step 2) {
        val deg = if (side < 0) r.armL.v else r.armR.v
        val rd = Math.toRadians(deg.toDouble()).toFloat()
        val sx = cx + side * bw * 0.5f
        val sy = byy + bh * 0.22f
        val len = bh * 0.55f
        val ex = sx + side * sin(rd) * len
        val ey = sy + cos(rd) * len
        drawLine(SHADE, Offset(sx, sy), Offset(ex, ey), bw * 0.17f, StrokeCap.Round)
        drawLine(SHELL, Offset(sx, sy), Offset(ex, ey), bw * 0.11f, StrokeCap.Round)
        drawCircle(tc, bw * 0.095f, Offset(ex, ey))
    }

    // corpo e luz do peito (bate mais forte quando ele fala)
    drawRoundRect(SHELL, Offset(bx, byy), Size(bw, bh), CornerRadius(bw * 0.3f, bw * 0.3f))
    drawRoundRect(SHADE.copy(alpha = 0.55f), Offset(bx, byy + bh * 0.55f), Size(bw, bh * 0.45f), CornerRadius(bw * 0.3f, bw * 0.3f))
    val pulse = 0.55f + 0.45f * sin(t * (2f + think * 4f + L * 8f))
    drawCircle(tc.copy(alpha = 0.25f * pulse), bw * (0.14f + 0.08f * L), Offset(cx, byy + bh * 0.4f))
    drawCircle(tc.copy(alpha = pulse), bw * 0.07f, Offset(cx, byy + bh * 0.4f))

    // cabeça (inclina e "amassa" ao mudar de estado)
    val sq = r.squash.v
    withTransform({
        rotate(r.tilt.v, Offset(cx, top + fh))
        scale(1f + sq * 0.5f, 1f - sq, Offset(cx, top + fh))
    }) {
        val tilt = sin(t * 1.1f) * fw * 0.01f
        drawLine(SHADE, Offset(cx, top + 2f * dp), Offset(cx + tilt, top - fh * 0.13f), 3f * dp, StrokeCap.Round)
        val ballA = 0.6f + 0.4f * (0.5f + 0.5f * sin(t * (2f + think * 4f + L * 10f)))
        drawCircle(tc.copy(alpha = 0.3f * ballA), fh * 0.07f, Offset(cx + tilt, top - fh * 0.15f))
        drawCircle(tc.copy(alpha = ballA), fh * 0.04f, Offset(cx + tilt, top - fh * 0.15f))
        for (side in -1..1 step 2) {
            val ex = if (side < 0) left - fw * 0.045f else left + fw - fw * 0.015f
            drawRoundRect(SHADE, Offset(ex, cy - fh * 0.14f), Size(fw * 0.06f, fh * 0.28f), CornerRadius(fw * 0.03f, fw * 0.03f))
            drawRoundRect(tc.copy(alpha = 0.7f), Offset(ex + fw * 0.012f, cy - fh * 0.07f), Size(fw * 0.036f, fh * 0.14f), CornerRadius(fw * 0.018f, fw * 0.018f))
        }
        drawRoundRect(SHELL, Offset(left, top), Size(fw, fh), CornerRadius(rad, rad))
        drawRoundRect(WHITE.copy(alpha = 0.55f), Offset(left + fw * 0.06f, top + fh * 0.02f), Size(fw * 0.88f, fh * 0.07f), CornerRadius(fh * 0.035f, fh * 0.035f))
        drawRoundRect(SHADE.copy(alpha = 0.6f), Offset(left, top + fh * 0.82f), Size(fw, fh * 0.18f), CornerRadius(rad, rad))

        // tela
        val sx0 = left + fw * 0.07f
        val sy0 = top + fh * 0.1f
        val sw = fw * 0.86f
        val sh = fh * 0.8f
        val sr = rad * 0.75f
        drawRoundRect(DARK, Offset(sx0, sy0), Size(sw, sh), CornerRadius(sr, sr))
        drawRoundRect(tc.copy(alpha = 0.5f), Offset(sx0, sy0), Size(sw, sh), CornerRadius(sr, sr), style = Stroke(2f * dp))
        drawRoundRect(WHITE.copy(alpha = 0.05f), Offset(sx0 + sw * 0.05f, sy0 + sh * 0.04f), Size(sw * 0.9f, sh * 0.26f), CornerRadius(sr * 0.8f, sr * 0.8f))

        // olhos
        val happy = joy > 0.5f || (calm > 0.5f && (t % 11f) in 8.2f..9.6f)
        val ew = sw * 0.17f
        val blink = r.blink.v.coerceIn(0f, 1f)
        val openH = 1f + listen * 0.12f + listen * 0.18f * L - think * 0.30f - speak * 0.06f - sad * 0.2f - angry * 0.15f +
            scared * 0.45f + surprised * 0.5f - sleepy * 0.88f - bored * 0.45f
        val eh0 = (sw * 0.23f * openH * (1f - blink * 0.92f)).coerceAtLeast(2f * dp)
        val eyeY = sy0 + sh * 0.4f + r.gy.v * sh * 0.05f
        for (side in -1..1 step 2) {
            val ex = cx + side * sw * 0.23f + r.gx.v * sw * 0.03f
            if (love > 0.5f) {
                val s = ew * (0.55f + 0.08f * sin(t * 6f))
                heartPath(path, ex, eyeY, s)
                drawPath(path, Color(0xFFFF6B9A))
                continue
            }
            if (dizzy > 0.5f) {
                path.reset()
                val maxR = ew * 0.75f
                for (i in 0..36) {
                    val a = i * 0.55f + t * 6f * side
                    val rr = maxR * i / 36f
                    val px = ex + cos(a) * rr
                    val py = eyeY + sin(a) * rr
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, tc, style = Stroke(2.2f * dp, cap = StrokeCap.Round))
                continue
            }
            if (happy) {
                drawArc(tc, 200f, 140f, false, Offset(ex - ew * 0.6f, eyeY - ew * 0.15f), Size(ew * 1.2f, ew * 1.2f), style = Stroke(ew * 0.3f, cap = StrokeCap.Round))
                continue
            }
            val sizeK = if (curious > 0.02f) (1f + 0.18f * side * curious) else 1f
            val eh = eh0 * sizeK
            val top0 = eyeY - eh / 2f
            drawRoundRect(tc.copy(alpha = 0.16f), Offset(ex - ew * 0.8f, eyeY - eh * 0.8f), Size(ew * 1.6f, eh * 1.6f), CornerRadius(ew * 0.8f, ew * 0.8f))
            drawRoundRect(tc, Offset(ex - ew / 2f, top0), Size(ew, eh), CornerRadius(ew * 0.5f, ew * 0.5f))
            drawRoundRect(WHITE.copy(alpha = 0.28f), Offset(ex - ew / 2f, top0), Size(ew, eh * 0.45f), CornerRadius(ew * 0.5f, ew * 0.5f))
            if (eh > ew * 0.6f) drawCircle(WHITE.copy(alpha = 0.9f), ew * 0.13f, Offset(ex - ew * 0.16f, eyeY - eh * 0.22f))
            val outer = ex + side * ew * 0.62f
            val inner = ex - side * ew * 0.62f
            if (angry > 0.02f) {
                path.reset(); path.moveTo(outer, top0 - 2f * dp); path.lineTo(inner, top0 - 2f * dp); path.lineTo(inner, top0 + eh * 0.55f * angry); path.close()
                drawPath(path, DARK)
            }
            if (sad > 0.02f) {
                path.reset(); path.moveTo(outer, top0 - 2f * dp); path.lineTo(inner, top0 - 2f * dp); path.lineTo(outer, top0 + eh * 0.5f * sad); path.close()
                drawPath(path, DARK)
            }
            if (curious > 0.02f && side > 0) {
                drawLine(tc.copy(alpha = curious), Offset(ex - ew * 0.6f, top0 - eh * 0.25f), Offset(ex + ew * 0.6f, top0 - eh * 0.42f), 3f * dp, StrokeCap.Round)
            }
        }
        if (sad > 0.02f) {
            val ty = eyeY + eh0 * 0.7f + ((t * 0.6f) % 1f) * sh * 0.14f
            drawCircle(Color(0xFF6EC1FF).copy(alpha = sad), sw * 0.014f, Offset(cx - sw * 0.23f + ew * 0.3f, ty))
        }
        if (surprised > 0.02f) {
            val ax = left + fw * 0.88f
            val ay = top - fh * 0.02f
            drawLine(Color(0xFFFFE08A).copy(alpha = surprised), Offset(ax, ay - fh * 0.14f), Offset(ax, ay + fh * 0.02f), 4f * dp, StrokeCap.Round)
            drawCircle(Color(0xFFFFE08A).copy(alpha = surprised), 2.6f * dp, Offset(ax, ay + fh * 0.09f))
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
        if (sleepy > 0.02f || bored > 0.5f) {
            val a0 = maxOf(sleepy, bored * 0.5f)
            for (k in 0..2) {
                val pp = (t * 0.5f + k * 0.33f) % 1f
                val zx = left + fw * 0.8f + pp * fw * 0.1f
                val zy = top + fh * 0.05f - pp * fh * 0.25f
                val zs = fh * 0.06f * (0.6f + pp)
                val zc = tc.copy(alpha = a0 * (1f - pp))
                drawLine(zc, Offset(zx, zy), Offset(zx + zs, zy), 2f * dp, StrokeCap.Round)
                drawLine(zc, Offset(zx + zs, zy), Offset(zx, zy + zs), 2f * dp, StrokeCap.Round)
                drawLine(zc, Offset(zx, zy + zs), Offset(zx + zs, zy + zs), 2f * dp, StrokeCap.Round)
            }
        }

        // bochechas
        for (side in -1..1 step 2) {
            val al = 0.16f + (if (happy) 0.2f else 0f) + 0.1f * speak + 0.2f * love
            drawOval(Color(0xFFFF8FA3).copy(alpha = al), Offset(cx + side * sw * 0.36f - sw * 0.05f, sy0 + sh * 0.62f), Size(sw * 0.1f, sw * 0.055f))
        }

        // boca
        val my = sy0 + sh * 0.8f
        val mw = sw * 0.2f
        val lw = sw * 0.028f
        val openAmt = r.mOpen.v.coerceIn(0f, 1.3f)
        if (openAmt > 0.12f) {
            val mh = sh * (0.04f + 0.2f * openAmt)
            val mwd = sw * (0.17f + 0.06f * openAmt) * r.mWide.v.coerceIn(0.4f, 1.4f)
            drawRoundRect(tc, Offset(cx - mwd / 2f, my - mh / 2f + mh * 0.2f), Size(mwd, mh), CornerRadius(mh * 0.5f, mh * 0.5f))
            drawRoundRect(DARK.copy(alpha = 0.55f), Offset(cx - mwd * 0.36f, my - mh * 0.05f), Size(mwd * 0.72f, mh * 0.45f), CornerRadius(mh * 0.25f, mh * 0.25f))
        } else if (calm + sad + angry + scared + joy + curious + love > 0.01f) {
            val smile = sh * (if (happy) 0.2f else 0.1f + 0.012f * sin(t * 1.4f))
            val curve = smile * (calm + curious * 0.4f + love * 1.1f - sad * 0.8f - angry * 1.2f - scared * 0.6f + joy * 1.5f)
            path.reset(); path.moveTo(cx - mw, my); path.quadraticBezierTo(cx, my + curve, cx + mw, my)
            drawPath(path, tc.copy(alpha = (calm + sad + angry + scared + joy + curious + love).coerceAtMost(1f)), style = Stroke(lw, cap = StrokeCap.Round))
        }
        if (listen > 0.01f && openAmt <= 0.12f) {
            drawCircle(tc.copy(alpha = listen), sw * 0.035f * (1f + 0.9f * L), Offset(cx, my), style = Stroke(lw))
        }
        if (think > 0.01f) {
            if (openAmt <= 0.12f) {
                val wob = sin(t * 3f) * sh * 0.012f
                drawLine(tc.copy(alpha = think), Offset(cx - mw * 0.55f, my + wob), Offset(cx + mw * 0.55f, my - wob), lw, StrokeCap.Round)
            }
            if (act.isEmpty()) {
                for (k in 0..2) {
                    val by2 = sy0 + sh * 0.14f - abs(sin(t * 4f + k * 0.9f)) * sh * 0.05f
                    drawCircle(tc.copy(alpha = think), sw * 0.016f, Offset(sx0 + sw * 0.78f + k * sw * 0.06f, by2))
                }
            } else {
                badge(act, Offset(left + fw * 0.93f, top - fh * 0.05f), fh * 0.17f * think.coerceAtLeast(0.01f), t, tc)
            }
        }
    }
}

/**
 * Robozinho de mesa (inspirado no Eilik e no Emo). Tudo é calculado em molas dentro de UM laço de quadros e desenhado com
 * formas simples: 60 fps quando algo acontece, 30 fps parado, e pausa quando o app sai da tela.
 * activity: "", "web", "code", "file", "term", "phone", "screen", "retry" ou "work".
 * mood: "", "angry", "scared", "sleepy", "joy", "curious", "love", "surprised", "dizzy", "bored".
 */
@Composable
fun RobotFace(
    state: State, level: () -> Float, activity: String, error: Boolean, modifier: Modifier = Modifier,
    onTap: () -> Unit = {}, onLong: () -> Unit = {}, mood: String = ""
) {
    val tint by animateColorAsState(faceColor(state), tween(400), label = "fc")
    val stateNow by rememberUpdatedState(state)
    val moodNow by rememberUpdatedState(mood)
    val actNow by rememberUpdatedState(activity)
    val errNow by rememberUpdatedState(error)
    val levelFn by rememberUpdatedState(level)
    val rig = remember { Rig() }
    val path = remember { Path() }
    val tick = remember { mutableLongStateOf(0L) }
    var touchX by remember { mutableFloatStateOf(0.5f) }
    var touchY by remember { mutableFloatStateOf(0.4f) }
    var touching by remember { mutableStateOf(false) }

    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(true) }
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }
    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        var last = 0L
        var skip = false
        while (true) withFrameNanos { n ->
            val busy = stateNow != State.IDLE || moodNow.isNotEmpty() || touching
            skip = !skip
            if (!busy && skip && last != 0L) return@withFrameNanos
            val dt = if (last == 0L) 0.016f else ((n - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            last = n
            rig.update(dt, stateNow, moodNow, actNow, errNow, levelFn(), touchX, touchY, touching)
            tick.longValue = n
        }
    }

    Box(
        modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() }) }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val p = e.changes.firstOrNull()
                        if (p != null) {
                            touching = p.pressed
                            if (size.width > 0) touchX = p.position.x / size.width
                            if (size.height > 0) touchY = p.position.y / size.height
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (tick.longValue < 0L) return@Canvas
            drawRobot(rig, tint, actNow, path, 0f)
        }
    }
}
