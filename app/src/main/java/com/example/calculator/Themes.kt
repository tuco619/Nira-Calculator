package com.example.calculator

import android.graphics.Color

class CalcTheme(
    val index: Int,
    val label: String,
    val prefix: String,          // drawable prefix of the key images: k, k2, k3
    val prefsName: String,       // where the widget of this theme keeps its state
    val layoutRes: Int,          // widget layout
    val rows: List<List<String>>,
    val bgApp: Int,
    val bgDisplay: Int,
    val main: Int,
    val sub: Int,
    val accent: Int,
    val line: Int
) {
    // "mascot" is just a picture in the 00 spot, not a key
    val keyIds: List<String> get() = rows.flatten().filter { it != "mascot" }
}

object Themes {
    private val top = listOf(
        listOf("c", "bk", "pct", "div"),
        listOf("7", "8", "9", "mul"),
        listOf("4", "5", "6", "sub"),
        listOf("1", "2", "3", "add")
    )

    val ALL: List<CalcTheme> = listOf(
        CalcTheme(
            0, "Dogs", "k", "widget_calc", R.layout.widget_calc,
            top + listOf(listOf("00", "0", "dot", "eq")),
            R.drawable.bg_app, R.drawable.bg_display,
            Color.parseColor("#F1E6FF"), Color.parseColor("#A995D6"),
            Color.parseColor("#B58CFF"), Color.parseColor("#261845")
        ),
        CalcTheme(
            1, "Purple", "k2", "widget_calc2", R.layout.widget_calc_2,
            top + listOf(listOf("mascot", "0", "dot", "eq")),
            R.drawable.bg_app2, R.drawable.bg_display2,
            Color.parseColor("#FFFFFF"), Color.parseColor("#CDBBF2"),
            Color.parseColor("#C9A0F0"), Color.parseColor("#4A3A80")
        ),
        CalcTheme(
            2, "Black", "k3", "widget_calc3", R.layout.widget_calc_3,
            top + listOf(listOf("mascot", "0", "dot", "eq")),
            R.drawable.bg_app3, R.drawable.bg_display3,
            Color.parseColor("#FFFFFF"), Color.parseColor("#9E9E9E"),
            Color.parseColor("#E0E0E0"), Color.parseColor("#2A2A2A")
        )
    )
}
