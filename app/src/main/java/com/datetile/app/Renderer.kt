package com.datetile.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Draws the tile for a given day. The widget and the in-app preview use the same drawing. */
object Renderer {
    /** The special day for [date], if any. */
    fun special(s: Style, date: LocalDate): Special? = s.specials.firstOrNull {
        it.month == date.monthValue && it.day == date.dayOfMonth && (it.year == 0 || it.year == date.year)
    }

    /** Today's accent colour: special day > weekend > the chosen mode. */
    fun accent(context: Context, s: Style, date: LocalDate): Int {
        special(s, date)?.let { return it.color }
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        if (s.weekendOn && weekend) return s.weekendAccent
        return when (s.accentMode) {
            AccentMode.FIXED -> s.accent
            AccentMode.WEEKDAY -> s.weekdayColors[date.dayOfWeek.value - 1]
            AccentMode.MONTH -> s.monthColors[date.monthValue - 1]
            AccentMode.WALLPAPER -> runCatching { context.getColor(android.R.color.system_accent1_300) }.getOrDefault(s.accent)
        }
    }

    /** Black or white, whichever reads better on [bg]. */
    fun onColor(bg: Int): Int {
        fun ch(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        val l = 0.2126 * ch(Color.red(bg)) + 0.7152 * ch(Color.green(bg)) + 0.0722 * ch(Color.blue(bg))
        return if (l > 0.45) 0xFF111111.toInt() else Color.WHITE
    }

    private fun colorFor(role: Role, custom: Int, s: Style, accent: Int): Int = when (role) {
        Role.TEXT -> s.textColor
        Role.ACCENT -> accent
        Role.ON_ACCENT -> onColor(accent)
        Role.CUSTOM -> custom
    }

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

    /** The words for one line, or "" when it has nothing to show today. */
    fun text(l: Line, s: Style, date: LocalDate): String {
        val loc = Locale.getDefault()
        val t = when (l.kind) {
            Kind.WEEKDAY -> when (l.format) {
                0 -> date.dayOfWeek.getDisplayName(TextStyle.FULL, loc)
                1 -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, loc).trimEnd('.')
                2 -> date.dayOfWeek.getDisplayName(TextStyle.FULL, loc).take(2)
                else -> date.dayOfWeek.getDisplayName(TextStyle.NARROW, loc)
            }
            Kind.DATE -> when (l.format) {
                1 -> "%02d".format(date.dayOfMonth)
                2 -> ordinal(date.dayOfMonth)
                else -> date.dayOfMonth.toString()
            }
            Kind.MONTH -> when (l.format) {
                0 -> date.month.getDisplayName(TextStyle.FULL_STANDALONE, loc)
                1 -> date.month.getDisplayName(TextStyle.SHORT_STANDALONE, loc).trimEnd('.')
                2 -> date.monthValue.toString()
                else -> date.month.getDisplayName(TextStyle.NARROW_STANDALONE, loc)
            }
            Kind.YEAR -> if (l.format == 1) "'%02d".format(date.year % 100) else date.year.toString()
            Kind.WEEK -> date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).let {
                when (l.format) { 1 -> "Week $it"; 2 -> "$it"; else -> "W$it" }
            }
            Kind.DAY_OF_YEAR -> when (l.format) {
                1 -> "Day ${date.dayOfYear}"
                2 -> "${date.dayOfYear}/${date.lengthOfYear()}"
                else -> date.dayOfYear.toString()
            }
            Kind.DAYS_LEFT -> (date.lengthOfYear() - date.dayOfYear).let { if (l.format == 1) "$it" else "$it left" }
            Kind.SPECIAL -> special(s, date)?.label.orEmpty()
            Kind.PATTERN -> runCatching { date.format(DateTimeFormatter.ofPattern(l.text.ifBlank { "EEE d" }, loc)) }
                .getOrDefault("?")
            Kind.TEXT -> l.text
        }
        // Uppercase doesn't apply to your own text or an ordinal's "th".
        return if (s.uppercase && l.kind != Kind.TEXT && !(l.kind == Kind.DATE && l.format == 2)) t.uppercase(loc) else t
    }

    private fun shapePath(s: Style, r: RectF): Path = Path().apply {
        when (s.shape) {
            Shape.CIRCLE -> addOval(r, Path.Direction.CW)
            Shape.SQUARE -> addRect(r, Path.Direction.CW)
            Shape.ROUNDED, Shape.NONE -> {
                val c = s.corner * r.width()
                addRoundRect(r, c, c, Path.Direction.CW)
            }
            Shape.SQUIRCLE -> {
                // Superellipse (n = 4).
                val cx = r.centerX(); val cy = r.centerY(); val a = r.width() / 2; val b = r.height() / 2
                for (i in 0..120) {
                    val th = 2 * Math.PI * i / 120
                    val c = cos(th); val sn = sin(th)
                    val x = cx + a * sign(c) * abs(c).pow(0.5)
                    val y = cy + b * sign(sn) * abs(sn).pow(0.5)
                    if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
                }
                close()
            }
        }
    }

    private fun roundedFromTop(r: RectF, c: Float): Path = Path().apply {
        moveTo(r.centerX(), r.top)
        lineTo(r.right - c, r.top)
        arcTo(RectF(r.right - 2 * c, r.top, r.right, r.top + 2 * c), -90f, 90f)
        lineTo(r.right, r.bottom - c)
        arcTo(RectF(r.right - 2 * c, r.bottom - 2 * c, r.right, r.bottom), 0f, 90f)
        lineTo(r.left + c, r.bottom)
        arcTo(RectF(r.left, r.bottom - 2 * c, r.left + 2 * c, r.bottom), 90f, 90f)
        lineTo(r.left, r.top + c)
        arcTo(RectF(r.left, r.top, r.left + 2 * c, r.top + 2 * c), 180f, 90f)
        lineTo(r.centerX(), r.top)
    }

    private fun progress(ring: Ring, date: LocalDate): Float = when (ring) {
        Ring.WEEK -> date.dayOfWeek.value / 7f
        Ring.MONTH -> date.dayOfMonth / date.lengthOfMonth().toFloat()
        Ring.YEAR -> date.dayOfYear / date.lengthOfYear().toFloat()
        Ring.NONE -> 0f
    }

    fun render(context: Context, s: Style, date: LocalDate, sizePx: Int): Bitmap {
        val n = sizePx.coerceIn(48, 1024)
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        draw(context, Canvas(bmp), s, date, n.toFloat())
        return bmp
    }

    fun draw(context: Context, canvas: Canvas, s: Style, date: LocalDate, size: Float) {
        // Tile size: shrink it towards the middle of the widget's space.
        val k = s.tileScale.coerceIn(0.3f, 1f)
        canvas.save()
        canvas.translate(size * (1f - k) / 2f, size * (1f - k) / 2f)
        canvas.scale(k, k)
        drawTile(context, canvas, s, date, size)
        canvas.restore()
    }

    private fun drawTile(context: Context, canvas: Canvas, s: Style, date: LocalDate, size: Float) {
        val accent = accent(context, s, date)
        val full = RectF(0f, 0f, size, size)
        val inset = if (s.border > 0f) s.border * size / 2f else 0f
        val box = RectF(full).apply { inset(inset, inset) }
        val clip = shapePath(s, box)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Background and header band.
        if (s.shape != Shape.NONE) {
            canvas.save()
            canvas.clipPath(clip)
            val bg = colorFor(s.bgRole, s.bgColor, s, accent)
            paint.color = bg
            paint.alpha = (Color.alpha(bg) * s.bgAlpha).toInt().coerceIn(0, 255)
            canvas.drawRect(full, paint)
            if (s.header) {
                paint.color = colorFor(s.headerRole, s.headerColor, s, accent)
                canvas.drawRect(0f, 0f, size, box.top + s.headerHeight * box.height(), paint)
            }
            canvas.restore()
        } else if (s.header) {
            paint.color = colorFor(s.headerRole, s.headerColor, s, accent)
            val c = s.corner * box.width()
            canvas.drawRoundRect(RectF(box.left, box.top, box.right, box.top + s.headerHeight * box.height()), c, c, paint)
        }
        if (s.border > 0f && s.shape != Shape.NONE) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s.border * size
            paint.color = colorFor(s.borderRole, s.borderColor, s, accent)
            canvas.drawPath(clip, paint)
            paint.style = Paint.Style.FILL
        }

        // Progress ring.
        var ringSpace = 0f
        if (s.ring != Ring.NONE) {
            val w = s.ringWidth * size
            ringSpace = w * 1.6f
            val rr = RectF(box).apply { inset(w * 1.2f, w * 1.2f) }
            val c = when (s.shape) {
                Shape.SQUARE -> 0f
                Shape.SQUIRCLE -> rr.width() * 0.3f
                else -> max(0f, s.corner * box.width() - w * 1.2f)
            }.coerceAtMost(rr.width() / 2f)
            // Starts at the top centre and runs clockwise.
            val track = if (s.shape == Shape.CIRCLE) Path().apply { addOval(rr, Path.Direction.CW) } else roundedFromTop(rr, c)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = colorFor(Role.TEXT, 0, s, accent)
            paint.alpha = 50
            canvas.drawPath(track, paint)
            paint.color = accent
            val frac = progress(s.ring, date)
            if (s.shape == Shape.CIRCLE) {
                canvas.drawArc(rr, -90f, 360f * frac, false, paint)
            } else {
                val m = android.graphics.PathMeasure(track, false)
                val part = Path()
                m.getSegment(0f, m.length * frac, part, true)
                canvas.drawPath(part, paint)
            }
            paint.style = Paint.Style.FILL
        }

        // Text.
        val base = Typeface.create(s.font.family, Typeface.NORMAL)
        val pad = s.padding * size + ringSpace
        val headerBottom = box.top + s.headerHeight * box.height()
        val headerArea = RectF(box.left + pad, box.top, box.right - pad, headerBottom)
        val bodyTop = if (s.header) headerBottom else box.top + pad
        val circleInset = if (s.shape == Shape.CIRCLE) size * 0.06f else 0f
        val body = RectF(box.left + pad + circleInset, bodyTop + circleInset * 0.5f, box.right - pad - circleInset, box.bottom - pad - circleInset * 0.5f)

        val visible = s.lines.mapNotNull { l -> text(l, s, date).takeIf { it.isNotEmpty() }?.let { l to it } }
        fun drawGroup(group: List<Pair<Line, String>>, area: RectF, valign: VAlign) {
            if (group.isEmpty()) return
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
            // Measure each line (shrunk to fit the width), then stack them.
            val laid = group.map { (l, t) ->
                p.typeface = Typeface.create(base, l.weight.coerceIn(100, 1000), l.italic)
                p.letterSpacing = s.spacing
                p.textSize = l.size * s.textScale * size
                val w = p.measureText(t)
                if (w > area.width() && w > 0f) p.textSize *= area.width() / w
                val fm = p.fontMetrics
                // Cap height-ish box: tight enough that stacked lines look even.
                val bounds = android.graphics.Rect()
                p.getTextBounds(t, 0, t.length, bounds)
                Triple(l, t, floatArrayOf(p.textSize, bounds.top.toFloat(), bounds.bottom.toFloat(), fm.ascent))
            }
            val gap = s.lineGap * size
            val heights = laid.map { it.third[2] - it.third[1] }
            val total = heights.sum() + gap * (laid.size - 1)
            var y = when (valign) {
                VAlign.TOP -> area.top
                VAlign.CENTER -> area.centerY() - total / 2f
                VAlign.BOTTOM -> area.bottom - total
            }
            laid.forEachIndexed { i, (l, t, m) ->
                p.typeface = Typeface.create(base, l.weight.coerceIn(100, 1000), l.italic)
                p.letterSpacing = s.spacing
                p.textSize = m[0]
                p.color = colorFor(l.role, l.color, s, accent)
                if (l.inHeader && s.header) {
                    when (s.headerText) {
                        HeaderText.AUTO -> p.color = onColor(colorFor(s.headerRole, s.headerColor, s, accent))
                        HeaderText.CUSTOM -> p.color = s.headerTextColor
                        HeaderText.ACCENT -> p.color = accent
                        HeaderText.LINES -> Unit
                    }
                }
                if (s.shadow && s.shadowSize > 0f) {
                    p.setShadowLayer(size * s.shadowSize, 0f, size * s.shadowSize / 3f, 0x99000000.toInt())
                } else {
                    p.clearShadowLayer()
                }
                val w = p.measureText(t)
                val x = when (s.align) {
                    Align.LEFT -> area.left
                    Align.CENTER -> area.centerX() - w / 2f
                    Align.RIGHT -> area.right - w
                }
                canvas.drawText(t, x, y - m[1], p)
                y += heights[i] + gap
            }
        }
        if (s.header) {
            drawGroup(visible.filter { it.first.inHeader }, headerArea, VAlign.CENTER)
            drawGroup(visible.filter { !it.first.inHeader }, body, s.valign)
        } else {
            drawGroup(visible, body, s.valign)
        }
    }
}
