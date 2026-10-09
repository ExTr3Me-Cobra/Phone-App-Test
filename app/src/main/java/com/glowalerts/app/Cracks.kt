package com.glowalerts.app

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The lines of one crack pattern, as straight segments. Each segment knows how far along the
 * crack it starts ([dist]), so the crack can spread out from where it began.
 */
class CrackShape(
    /** x1, y1, x2, y2 for each segment. */
    val points: FloatArray,
    /** Distance from the starting point at which each segment begins. */
    val dist: FloatArray,
    val length: FloatArray,
    /** 0 = main crack, 1 = branch, 2 = small twig (drawn thinner). */
    val level: IntArray,
    val maxDist: Float,
    val originX: Float,
    val originY: Float,
) {
    val count get() = dist.size
}

/** Builds the crack patterns. Every call with a new seed gives a different crack. */
object Cracks {
    private class Builder(val w: Float, val h: Float, val rnd: Random) {
        val pts = ArrayList<Float>()
        val dist = ArrayList<Float>()
        val len = ArrayList<Float>()
        val level = ArrayList<Int>()
        val diag = hypot(w, h)
        val unit = min(w, h)

        fun seg(x1: Float, y1: Float, x2: Float, y2: Float, d: Float, lvl: Int) {
            pts += x1; pts += y1; pts += x2; pts += y2
            dist += d
            len += hypot(x2 - x1, y2 - y1)
            level += lvl
        }

        fun inside(x: Float, y: Float, margin: Float = 0f) =
            x >= -margin && y >= -margin && x <= w + margin && y <= h + margin

        /**
         * A jagged crack starting at (x, y) heading at [angle] for up to [length] (stops a little
         * past the screen edge), with random side branches.
         */
        fun jagged(
            x0: Float, y0: Float, angle: Float, length: Float, d0: Float, lvl: Int,
            branchChance: Float, wobble: Float = 0.55f,
        ) {
            var x = x0
            var y = y0
            var a = angle
            var d = d0
            var travelled = 0f
            val step = unit * (if (lvl == 0) 0.035f else 0.025f)
            while (travelled < length && inside(x, y, unit * 0.05f)) {
                // Wander, but keep drifting back to the main heading.
                a += (rnd.nextFloat() - 0.5f) * wobble + (angle - a) * 0.35f
                val s = step * (0.6f + rnd.nextFloat() * 0.8f)
                val nx = x + cos(a) * s
                val ny = y + sin(a) * s
                seg(x, y, nx, ny, d, lvl)
                d += s
                travelled += s
                x = nx
                y = ny
                if (lvl < 2 && rnd.nextFloat() < branchChance) {
                    val side = if (rnd.nextBoolean()) 1 else -1
                    val ba = a + side * (0.4f + rnd.nextFloat() * 0.7f)
                    val bl = unit * (if (lvl == 0) 0.12f + rnd.nextFloat() * 0.18f else 0.04f + rnd.nextFloat() * 0.07f)
                    jagged(x, y, ba, bl, d, lvl + 1, branchChance * 0.6f, wobble)
                }
            }
        }

        fun build(ox: Float, oy: Float) = CrackShape(
            pts.toFloatArray(), dist.toFloatArray(), len.toFloatArray(), level.toIntArray(),
            max(1f, (0 until dist.size).maxOfOrNull { dist[it] + len[it] } ?: 1f), ox, oy,
        )
    }

    fun build(
        effect: LightEffect, w: Float, h: Float, origin: CrackOrigin, detail: Int, seed: Long,
        camX: Float, camY: Float,
    ): CrackShape {
        val rnd = Random(seed)
        val b = Builder(w, h, rnd)
        val o = if (origin == CrackOrigin.RANDOM) {
            CrackOrigin.entries.filter { it != CrackOrigin.RANDOM }.random(rnd)
        } else {
            origin
        }
        val jitter = { rnd.nextFloat() - 0.5f }
        val (ox, oy) = when (o) {
            CrackOrigin.TOP -> w * (0.5f + jitter() * 0.3f) to 0f
            CrackOrigin.BOTTOM -> w * (0.5f + jitter() * 0.3f) to h
            CrackOrigin.LEFT -> 0f to h * (0.5f + jitter() * 0.3f)
            CrackOrigin.RIGHT -> w to h * (0.5f + jitter() * 0.3f)
            CrackOrigin.CENTER, CrackOrigin.RANDOM -> w * (0.5f + jitter() * 0.1f) to h * (0.5f + jitter() * 0.1f)
            CrackOrigin.TOP_LEFT -> 0f to 0f
            CrackOrigin.TOP_RIGHT -> w to 0f
            CrackOrigin.BOTTOM_LEFT -> 0f to h
            CrackOrigin.BOTTOM_RIGHT -> w to h
            CrackOrigin.CAMERA -> camX to camY
        }
        val toCentre = atan2(h / 2 - oy, w / 2 - ox)
        val centred = o == CrackOrigin.CENTER
        val k = detail.coerceIn(1, 10)

        when (effect) {
            LightEffect.CRACK_QUAKE -> {
                // One big fissure right across the screen, with side cracks.
                val heading = if (centred) (rnd.nextFloat() - 0.5f) * 0.8f else toCentre
                val chance = 0.04f + k * 0.022f
                b.jagged(ox, oy, heading, b.diag * 1.2f, 0f, 0, chance, wobble = 0.7f)
                if (centred) b.jagged(ox, oy, heading + PI.toFloat(), b.diag * 1.2f, 0f, 0, chance, wobble = 0.7f)
            }
            LightEffect.CRACK_SHATTER -> {
                // Cracks bursting out in every direction from the impact point.
                val rays = 5 + k
                val start = rnd.nextFloat() * 2 * PI.toFloat()
                for (i in 0 until rays) {
                    val a = start + i * 2 * PI.toFloat() / rays + (rnd.nextFloat() - 0.5f) * 0.4f
                    b.jagged(ox, oy, a, b.diag * (0.5f + rnd.nextFloat() * 0.7f), 0f, 0, 0.02f + k * 0.012f)
                }
            }
            LightEffect.CRACK_WEB -> {
                // Spokes out from the impact, joined by rings like a spider's web.
                val rays = 6 + k / 2
                val start = rnd.nextFloat() * 2 * PI.toFloat()
                val angles = FloatArray(rays) { start + it * 2 * PI.toFloat() / rays + (rnd.nextFloat() - 0.5f) * 0.3f }
                val reach = b.diag
                for (a in angles) b.jagged(ox, oy, a, reach, 0f, 0, 0.01f, wobble = 0.35f)
                val rings = 2 + k / 2
                val gap = b.unit * (0.07f + 0.03f * (10 - k) / 10f)
                for (r in 1..rings) {
                    val radius = gap * r * (1f + r * 0.18f)
                    for (i in angles.indices) {
                        if (rnd.nextFloat() < 0.15f) continue // a few gaps look more natural
                        val a1 = angles[i]
                        val a2 = angles[(i + 1) % rays].let { if (it < a1) it + 2 * PI.toFloat() else it }
                        val r1 = radius * (0.9f + rnd.nextFloat() * 0.2f)
                        val r2 = radius * (0.9f + rnd.nextFloat() * 0.2f)
                        // Each ring piece sags inwards a little between the spokes.
                        val pieces = 4
                        var px = ox + cos(a1) * r1
                        var py = oy + sin(a1) * r1
                        for (p in 1..pieces) {
                            val f = p / pieces.toFloat()
                            val a = a1 + (a2 - a1) * f
                            val rr = (r1 + (r2 - r1) * f) * (1f - 0.12f * sin(PI.toFloat() * f))
                            val nx = ox + cos(a) * rr
                            val ny = oy + sin(a) * rr
                            b.seg(px, py, nx, ny, radius, 1)
                            px = nx
                            py = ny
                        }
                    }
                }
            }
            LightEffect.CRACK_LIGHTNING -> {
                // A branching crack forking towards the far side, like lightning.
                val heading = if (centred) -PI.toFloat() / 2 + (rnd.nextFloat() - 0.5f) else toCentre
                b.jagged(ox, oy, heading, b.diag * 1.1f, 0f, 0, 0.1f + k * 0.03f, wobble = 0.9f)
                if (centred) b.jagged(ox, oy, heading + PI.toFloat(), b.diag * 1.1f, 0f, 0, 0.1f + k * 0.03f, wobble = 0.9f)
            }
            LightEffect.CRACK_FAULTS -> {
                // Several roughly parallel cracks crossing the screen.
                val lines = 2 + k / 3
                val heading = if (centred) 0f else toCentre
                val nx = -sin(heading)
                val ny = cos(heading)
                val spread = b.unit * 0.9f
                for (i in 0 until lines) {
                    val off = (i - (lines - 1) / 2f) / max(1, lines - 1) * spread + (rnd.nextFloat() - 0.5f) * b.unit * 0.08f
                    var sx = ox + nx * off
                    var sy = oy + ny * off
                    // Start just inside the screen so every line is visible.
                    sx = sx.coerceIn(0f, w)
                    sy = sy.coerceIn(0f, h)
                    val delay = rnd.nextFloat() * b.unit * 0.25f
                    b.jagged(sx, sy, heading + (rnd.nextFloat() - 0.5f) * 0.15f, b.diag * 1.2f, delay, 0, 0.03f + k * 0.01f, wobble = 0.5f)
                    if (centred) b.jagged(sx, sy, heading + PI.toFloat(), b.diag * 1.2f, delay, 0, 0.03f + k * 0.01f, wobble = 0.5f)
                }
            }
            LightEffect.CRACK_EDGES -> {
                // Cracks creeping in from all round the screen edge, starting nearest the origin.
                val count = 6 + k * 2
                val perimeter = 2 * (w + h)
                for (i in 0 until count) {
                    val p = (i + rnd.nextFloat() * 0.8f) / count * perimeter
                    val (ex, ey, inward) = when {
                        p < w -> Triple(p, 0f, PI.toFloat() / 2)
                        p < w + h -> Triple(w, p - w, PI.toFloat())
                        p < 2 * w + h -> Triple(w - (p - w - h), h, -PI.toFloat() / 2)
                        else -> Triple(0f, h - (p - 2 * w - h), 0f)
                    }
                    val d0 = hypot(ex - ox, ey - oy) * 0.6f
                    val a = inward + (rnd.nextFloat() - 0.5f) * 0.9f
                    b.jagged(ex, ey, a, b.unit * (0.08f + rnd.nextFloat() * 0.2f), d0, 0, 0.08f + k * 0.02f)
                }
            }
            else -> Unit
        }
        return b.build(ox, oy)
    }
}
