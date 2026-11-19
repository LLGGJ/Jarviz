package com.jarvis.app

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.sin

/** Robozinho flutuante sobre os outros apps (aparece quando o app do Jarvis não está na tela). Arraste para mover; toque para falar. */
object Overlay {
    @Volatile var appVisible = false
    private var view: View? = null

    /** Chamar na thread principal. */
    fun sync(c: Context, wanted: Boolean) {
        val app = c.applicationContext
        val show = wanted && !appVisible && Settings.canDrawOverlays(app)
        if (show && view == null) add(app) else if (!show && view != null) remove(app)
    }

    private fun add(app: Context) {
        try {
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val vm = BrainStore.get(app as Application)
            val size = (96 * app.resources.displayMetrics.density).toInt()
            val lp = WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = 24
            lp.y = 360
            val v = FaceView(app, vm, wm, lp)
            wm.addView(v, lp)
            view = v
        } catch (e: Exception) {
            view = null
        }
    }

    private fun remove(app: Context) {
        try {
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            view?.let { wm.removeView(it) }
        } catch (e: Exception) { }
        view = null
    }
}

private class FaceView(ctx: Context, val vm: JarvisViewModel, val wm: WindowManager, val lp: WindowManager.LayoutParams) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private var last = 0L
    private var dx = 0f
    private var dy = 0f
    private var sx = 0
    private var sy = 0
    private var moved = false

    override fun onDraw(cv: Canvas) {
        val now = System.nanoTime()
        if (last != 0L) t += (now - last) / 1_000_000_000f
        last = now
        val w = width.toFloat()
        val h = height.toFloat()
        val st = vm.state
        val tint = when (st) {
            State.IDLE -> 0xFF8EC8FF.toInt()
            State.LISTENING -> 0xFF4DE3C2.toInt()
            State.THINKING -> 0xFFB5A6FF.toInt()
            State.SPEAKING -> 0xFFFFB454.toInt()
        }
        val lv = vm.level.coerceIn(0f, 1f)
        val cx = w / 2f
        val plate = RectF(w * 0.06f, h * 0.14f, w * 0.94f, h * 0.94f)
        p.style = Paint.Style.FILL
        p.color = 0xFF0E1525.toInt()
        cv.drawRoundRect(plate, h * 0.26f, h * 0.26f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = tint
        cv.drawRoundRect(plate, h * 0.26f, h * 0.26f, p)
        // antena
        p.strokeWidth = 4f
        cv.drawLine(cx, plate.top, cx, h * 0.04f, p)
        p.style = Paint.Style.FILL
        cv.drawCircle(cx, h * 0.04f, 5f, p)
        // olhos
        val blink = if (t % 4f < 0.14f) 0.12f else 1f
        val eh = h * 0.2f * blink * (if (st == State.LISTENING) 1.15f else if (st == State.THINKING) 0.75f else 1f)
        val ew = w * 0.13f
        val gx = (if (st == State.THINKING) sin(t * 3f) else sin(t * 0.6f)) * w * 0.03f
        val ey = h * 0.45f - (if (st == State.THINKING) h * 0.03f else 0f)
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * w * 0.2f + gx
            cv.drawRoundRect(RectF(ex - ew / 2f, ey - eh / 2f, ex + ew / 2f, ey + eh / 2f), ew / 2f, ew / 2f, p)
        }
        // boca
        val my = h * 0.72f
        val mw = w * 0.16f
        when (st) {
            State.SPEAKING -> {
                val open = h * (0.03f + 0.12f * (0.2f + 0.8f * lv) * (0.7f + 0.3f * abs(sin(t * 9f))))
                cv.drawRoundRect(RectF(cx - mw * 0.7f, my - open / 2f, cx + mw * 0.7f, my + open / 2f), open / 2f, open / 2f, p)
            }
            State.LISTENING -> cv.drawCircle(cx, my, w * 0.035f * (1f + 0.8f * lv), p.apply { style = Paint.Style.STROKE; strokeWidth = 3f })
            State.THINKING -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                cv.drawLine(cx - mw * 0.6f, my, cx + mw * 0.6f, my, p)
            }
            State.IDLE -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                cv.drawArc(RectF(cx - mw, my - h * 0.07f, cx + mw, my + h * 0.07f), 25f, 130f, false, p)
            }
        }
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; moved = false }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.rawX - dx) > 12 || abs(e.rawY - dy) > 12) moved = true
                if (moved) {
                    lp.x = sx + (e.rawX - dx).toInt()
                    lp.y = sy + (e.rawY - dy).toInt()
                    wm.updateViewLayout(this, lp)
                }
            }
            MotionEvent.ACTION_UP -> if (!moved) {
                when (vm.state) {
                    State.THINKING -> vm.cancel()
                    State.SPEAKING -> vm.interrupt()
                    else -> { BeepGuard.pre(); vm.listenNow() }
                }
            }
        }
        return true
    }
}
