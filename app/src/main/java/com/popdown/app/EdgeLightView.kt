package com.popdown.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Everything needed to draw one lighting effect. Sizes are in pixels. */
data class LightSpec(
    val effect: LightEffect,
    /** One colour, or two for a gradient. */
    val colors: IntArray,
    val speed: Float,
    val thicknessPx: Float,
    val cornerPx: Float,
    /** 0..1 */
    val brightness: Float,
    /** How long it plays; 0 = forever (the preview in the app), or until [keepGoing] says stop. */
    val durationMs: Long,
    /** For endless lighting: checked every frame once started; returning false ends it. */
    val keepGoing: (() -> Boolean)? = null,
)

/**
 * Draws the edge lighting around the screen. Animation time starts at the first frame actually
 * drawn, so when the screen is still off the effect starts as soon as it turns on.
 */
@SuppressLint("ViewConstructor")
class EdgeLightView(context: Context, spec: LightSpec, private val onDone: () -> Unit = {}) : View(context) {
    var spec: LightSpec = spec
        set(value) {
            field = value
            buildPath()
            invalidate()
        }

    private var start = 0L
    private var finished = false
    private val path = Path()
    private val measure = PathMeasure()
    private var length = 0f
    private val segment = Path()
    private val rotate = Matrix()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Restarts the effect from the beginning (for a new notification). */
    fun restart() {
        start = 0L
        finished = false
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = buildPath()

    /** A rounded rectangle along the screen edge that starts (and ends) at the top centre. */
    private fun buildPath() {
        path.reset()
        if (width == 0 || height == 0) return
        val inset = spec.thicknessPx / 2f
        val r0 = RectF(inset, inset, width - inset, height - inset)
        val r = (spec.cornerPx - inset).coerceIn(0f, min(r0.width(), r0.height()) / 2f)
        val cx = r0.centerX()
        path.moveTo(cx, r0.top)
        path.lineTo(r0.right - r, r0.top)
        path.arcTo(RectF(r0.right - 2 * r, r0.top, r0.right, r0.top + 2 * r), -90f, 90f)
        path.lineTo(r0.right, r0.bottom - r)
        path.arcTo(RectF(r0.right - 2 * r, r0.bottom - 2 * r, r0.right, r0.bottom), 0f, 90f)
        path.lineTo(r0.left + r, r0.bottom)
        path.arcTo(RectF(r0.left, r0.bottom - 2 * r, r0.left + 2 * r, r0.bottom), 90f, 90f)
        path.lineTo(r0.left, r0.top + r)
        path.arcTo(RectF(r0.left, r0.top, r0.left + 2 * r, r0.top + 2 * r), 180f, 90f)
        path.close()
        measure.setPath(path, false)
        length = measure.length
    }

    override fun onDraw(canvas: Canvas) {
        if (finished || length == 0f) return
        val now = SystemClock.uptimeMillis()
        if (start == 0L) {
            // Screen still fully off: wait, so the whole effect is seen. (It does play on the
            // Always On Display.)
            if (!LightService.screenVisible(context)) {
                postInvalidateDelayed(100)
                return
            }
            start = now
        }
        val t = now - start
        val s = spec
        if ((s.durationMs > 0 && t >= s.durationMs) || (s.durationMs == 0L && s.keepGoing?.invoke() == false)) {
            finished = true
            post(onDone)
            return
        }
        // Fade in and out at the ends.
        val env = if (s.durationMs == 0L) 1f else min(1f, min(t / 250f, (s.durationMs - t) / 400f))
        val cycle = 2200f / s.speed.coerceAtLeast(0.1f)
        val phase = (t % cycle.toLong()) / cycle
        val alpha = (s.brightness * env).coerceIn(0f, 1f)
        val w = s.thicknessPx

        applyColors(phase)
        when (s.effect) {
            LightEffect.GLOW -> {
                val breathe = 0.8f + 0.2f * sin(2 * PI * phase).toFloat()
                stroke(canvas, path, w * 2.2f, alpha * breathe * 0.7f, blur = w * 1.6f)
                stroke(canvas, path, w * 0.6f, alpha * breathe, blur = 0f)
            }
            LightEffect.LINE -> stroke(canvas, path, w, alpha, blur = 0f)
            LightEffect.PULSE -> {
                val p = (0.5f - 0.5f * cos(2 * PI * phase)).toFloat()
                stroke(canvas, path, w * (0.6f + 1.4f * p), alpha * (0.2f + 0.8f * p) * 0.6f, blur = w * 1.4f)
                stroke(canvas, path, w * (0.4f + 0.6f * p), alpha * (0.2f + 0.8f * p), blur = 0f)
            }
            LightEffect.COMET -> comet(canvas, phase * length, forward = true, alpha, w)
            LightEffect.TWIN -> {
                comet(canvas, phase * length / 2f, forward = true, alpha, w)
                comet(canvas, length - phase * length / 2f, forward = false, alpha, w)
            }
            LightEffect.RAINBOW -> {
                stroke(canvas, path, w * 2f, alpha * 0.6f, blur = w * 1.4f)
                stroke(canvas, path, w * 0.7f, alpha, blur = 0f)
            }
            LightEffect.ECHO -> echo(canvas, phase, alpha, w)
            LightEffect.FLASH -> {
                val on = phase < 0.12f || (phase in 0.24f..0.36f)
                if (on) {
                    stroke(canvas, path, w * 2f, alpha * 0.7f, blur = w * 1.4f)
                    stroke(canvas, path, w, alpha, blur = 0f)
                }
            }
        }
        postInvalidateOnAnimation()
    }

    private fun applyColors(phase: Float) {
        val s = spec
        val cx = width / 2f
        val cy = height / 2f
        val shader: Shader? = when {
            s.effect == LightEffect.RAINBOW -> SweepGradient(cx, cy, RAINBOW, null)
            s.colors.size > 1 -> SweepGradient(cx, cy, intArrayOf(s.colors[0], s.colors[1], s.colors[0]), null)
            else -> null
        }
        if (shader != null) {
            rotate.setRotate(phase * 360f, cx, cy)
            shader.setLocalMatrix(rotate)
        }
        for (p in arrayOf(line, glow)) {
            p.shader = shader
            p.color = if (shader == null) s.colors.firstOrNull() ?: Color.WHITE else Color.WHITE
        }
    }

    private fun stroke(canvas: Canvas, p: Path, width: Float, alpha: Float, blur: Float) {
        val paint = if (blur > 0f) glow else line
        paint.strokeWidth = max(1f, width)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        paint.maskFilter = if (blur > 0f) blurFilter(blur) else null
        canvas.drawPath(p, paint)
    }

    private val ring = Path()
    private val ringRect = RectF()

    /**
     * Like One UI's "Echo": the edge glows and rings of light keep rippling inwards from it,
     * fading as they travel.
     */
    private fun echo(canvas: Canvas, phase: Float, alpha: Float, w: Float) {
        // The edge itself breathes in time with each new ring leaving it.
        val beat = (1f - ((phase * RINGS) % 1f)).pow(2f)
        stroke(canvas, path, w * 2f, alpha * (0.45f + 0.35f * beat), blur = w * 1.5f)
        stroke(canvas, path, w * 0.7f, alpha * (0.7f + 0.3f * beat), blur = 0f)
        val travel = min(width, height) * 0.16f
        val inset = spec.thicknessPx / 2f
        for (k in 0 until RINGS) {
            val p = (phase + k / RINGS.toFloat()) % 1f
            val d = inset + p * travel
            ringRect.set(d, d, width - d, height - d)
            val r = (spec.cornerPx - d).coerceAtLeast(spec.cornerPx * 0.35f)
                .coerceAtMost(min(ringRect.width(), ringRect.height()) / 2f)
            ring.reset()
            ring.addRoundRect(ringRect, r, r, Path.Direction.CW)
            val fade = (1f - p).pow(1.8f)
            stroke(canvas, ring, w * (1.6f - p), alpha * fade * 0.5f, blur = w * (1.2f + p * 2f))
            stroke(canvas, ring, w * (0.6f - 0.4f * p), alpha * fade * 0.8f, blur = 0f)
        }
    }

    private val blurs = HashMap<Int, BlurMaskFilter>()

    private fun blurFilter(radius: Float): BlurMaskFilter {
        val key = max(1, radius.toInt())
        return blurs.getOrPut(key) { BlurMaskFilter(key.toFloat(), BlurMaskFilter.Blur.NORMAL) }
    }

    /** A bright head at [head] (distance along the edge) with a fading tail behind it. */
    private fun comet(canvas: Canvas, head: Float, forward: Boolean, alpha: Float, w: Float) {
        val tail = length * 0.28f
        val pieces = 16
        for (i in pieces - 1 downTo 0) {
            val a = i / pieces.toFloat()
            val b = (i + 1) / pieces.toFloat()
            val fade = (1f - a).pow(1.6f)
            val d1 = if (forward) head - tail * b else head + tail * a
            val d2 = if (forward) head - tail * a else head + tail * b
            segmentPath(d1, d2)
            stroke(canvas, segment, w * (1.9f - a), alpha * fade * 0.55f, blur = w * 1.3f)
            stroke(canvas, segment, w * (1f - 0.5f * a), alpha * fade, blur = 0f)
        }
    }

    /** Fills [segment] with the edge between two distances, wrapping round the start point. */
    private fun segmentPath(from: Float, to: Float) {
        segment.reset()
        var a = from % length
        if (a < 0) a += length
        var b = a + (to - from)
        if (b <= length) {
            measure.getSegment(a, b, segment, true)
        } else {
            measure.getSegment(a, length, segment, true)
            b -= length
            measure.getSegment(0f, b, segment, true)
        }
    }

    companion object {
        private const val RINGS = 3
        private val RAINBOW = intArrayOf(
            0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(),
            0xFF00C7BE.toInt(), 0xFF007AFF.toInt(), 0xFFAF52DE.toInt(), 0xFFFF2D55.toInt(), 0xFFFF3B30.toInt(),
        )
    }
}
