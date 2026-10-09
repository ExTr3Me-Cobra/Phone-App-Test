package com.datetile.app

import android.graphics.Color

/** Starting points. Picking one sets the layout; colours you've chosen are kept. */
object Presets {
    class Preset(val name: String, val lines: List<Line>, val apply: (Style) -> Style)

    val all = listOf(
        Preset(
            "Calendar page",
            listOf(
                Line(Kind.MONTH, 1, 0.17f, 700, role = Role.ON_ACCENT, inHeader = true),
                Line(Kind.DATE, 0, 0.42f, 500),
                Line(Kind.WEEKDAY, 1, 0.14f, 500, role = Role.ACCENT),
            ),
        ) { it.copy(shape = Shape.ROUNDED, header = true, headerHeight = 0.3f, ring = Ring.NONE, align = Align.CENTER, valign = VAlign.CENTER) },
        Preset(
            "Stacked",
            listOf(
                Line(Kind.WEEKDAY, 1, 0.17f, 700, role = Role.ACCENT),
                Line(Kind.DATE, 0, 0.44f, 400),
                Line(Kind.MONTH, 1, 0.15f, 500),
            ),
        ) { it.copy(header = false, ring = Ring.NONE, align = Align.CENTER, valign = VAlign.CENTER) },
        Preset(
            "Big date",
            listOf(Line(Kind.DATE, 0, 0.62f, 300)),
        ) { it.copy(header = false, ring = Ring.MONTH, align = Align.CENTER, valign = VAlign.CENTER) },
        Preset(
            "Day & date",
            listOf(
                Line(Kind.WEEKDAY, 0, 0.14f, 500, role = Role.ACCENT),
                Line(Kind.DATE, 0, 0.5f, 600),
            ),
        ) { it.copy(header = false, ring = Ring.NONE, align = Align.LEFT, valign = VAlign.BOTTOM) },
        Preset(
            "Circle",
            listOf(
                Line(Kind.WEEKDAY, 1, 0.15f, 700, role = Role.ACCENT),
                Line(Kind.DATE, 0, 0.42f, 500),
            ),
        ) { it.copy(shape = Shape.CIRCLE, header = false, ring = Ring.NONE, align = Align.CENTER, valign = VAlign.CENTER) },
        Preset(
            "Month ring",
            listOf(
                Line(Kind.DATE, 0, 0.4f, 600),
                Line(Kind.MONTH, 1, 0.14f, 600, role = Role.ACCENT),
            ),
        ) { it.copy(shape = Shape.CIRCLE, header = false, ring = Ring.MONTH, ringWidth = 0.06f, align = Align.CENTER, valign = VAlign.CENTER) },
        Preset(
            "Text only",
            listOf(
                Line(Kind.WEEKDAY, 1, 0.16f, 800, role = Role.ACCENT),
                Line(Kind.DATE, 0, 0.5f, 800),
            ),
        ) { it.copy(shape = Shape.NONE, header = false, ring = Ring.NONE, shadow = true, align = Align.CENTER, valign = VAlign.CENTER) },
        Preset(
            "Week & day",
            listOf(
                Line(Kind.DATE, 0, 0.36f, 600),
                Line(Kind.MONTH, 1, 0.13f, 500, role = Role.ACCENT),
                Line(Kind.WEEK, 0, 0.11f, 400, role = Role.CUSTOM, color = Color.GRAY),
            ),
        ) { it.copy(header = false, ring = Ring.YEAR, align = Align.CENTER, valign = VAlign.CENTER) },
    )
}
