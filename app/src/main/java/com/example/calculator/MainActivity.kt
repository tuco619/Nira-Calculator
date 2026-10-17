package com.example.calculator

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val lilac = Color.parseColor("#F1E6FF")
    private val grey = Color.parseColor("#A995D6")
    private val accent = Color.parseColor("#B58CFF")

    private var state = CalcState()
    private var histOpen = false

    private lateinit var mainText: TextView
    private lateinit var subText: TextView
    private lateinit var chev: TextView
    private lateinit var histPanel: LinearLayout
    private lateinit var histTitle: TextView
    private lateinit var histItems: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("calc", MODE_PRIVATE)
        state = CalcState(
            sp.getString("expr", "") ?: "",
            sp.getBoolean("fresh", false),
            sp.getString("last", "") ?: "",
            sp.getString("hist", "") ?: ""
        )
        setContentView(buildUi())
        refresh()
    }

    // After the app was force-closed, Android cancels the widget's tap actions.
    // Asking the widget to redraw gives it fresh ones, so it works again.
    override fun onResume() {
        super.onResume()
        refreshWidgets()
    }

    private fun refreshWidgets() {
        val ids = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, CalcWidgetProvider::class.java))
        if (ids.isEmpty()) return
        sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setClass(this, CalcWidgetProvider::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        )
    }

    private fun autoText(maxSp: Int, minSp: Int, color: Int, bold: Boolean): TextView = TextView(this).apply {
        setTextColor(color)
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        maxLines = 1
        includeFontPadding = false
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setAutoSizeTextTypeUniformWithConfiguration(minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP)
    }

    private fun buildUi(): View {
        val root = FrameLayout(this)
        root.setBackgroundResource(R.drawable.bg_app)
        root.fitsSystemWindows = true

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(8), dp(8), dp(8), dp(8))
        root.addView(col, FrameLayout.LayoutParams(MP, MP))

        // Display (tap to open the history)
        val display = LinearLayout(this)
        display.orientation = LinearLayout.HORIZONTAL
        display.gravity = Gravity.CENTER_VERTICAL
        display.setBackgroundResource(R.drawable.bg_display)
        display.setPadding(dp(12), dp(8), dp(16), dp(8))
        display.setOnClickListener { toggleHistory() }

        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        chev = TextView(this).apply { setTextColor(accent); textSize = 22f }
        val paw = TextView(this).apply { text = "\uD83D\uDC3E"; textSize = 22f }
        left.addView(chev, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        left.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        left.addView(paw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        display.addView(left, LinearLayout.LayoutParams(dp(36), MP))

        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        subText = autoText(22, 8, grey, false)
        mainText = autoText(72, 12, lilac, false)
        right.addView(subText, LinearLayout.LayoutParams(MP, 0, 1f))
        right.addView(mainText, LinearLayout.LayoutParams(MP, 0, 2.2f))
        display.addView(right, LinearLayout.LayoutParams(0, MP, 1f))

        col.addView(display, LinearLayout.LayoutParams(MP, 0, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })

        // Body: keypad + history panel on top
        val body = FrameLayout(this)
        col.addView(body, LinearLayout.LayoutParams(MP, 0, 5f))

        val keypad = LinearLayout(this)
        keypad.orientation = LinearLayout.VERTICAL
        CalcLogic.ROWS.forEach { rowIds ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            rowIds.forEach { id -> row.addView(makeKey(id), LinearLayout.LayoutParams(0, MP, 1f)) }
            keypad.addView(row, LinearLayout.LayoutParams(MP, 0, 1f))
        }
        body.addView(keypad, FrameLayout.LayoutParams(MP, MP))

        histPanel = LinearLayout(this)
        histPanel.orientation = LinearLayout.VERTICAL
        histPanel.setBackgroundResource(R.drawable.bg_display)
        histPanel.setPadding(dp(10), dp(10), dp(10), dp(10))
        histPanel.visibility = View.GONE
        histPanel.isClickable = true

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        histTitle = TextView(this).apply { setTextColor(grey); textSize = 15f }
        val clear = TextView(this).apply {
            text = "Clear"; setTextColor(lilac); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                state = state.copy(hist = "", last = "")
                save(); buildHistory(); refresh()
            }
        }
        header.addView(histTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(clear, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        histPanel.addView(header, LinearLayout.LayoutParams(MP, ViewGroup.LayoutParams.WRAP_CONTENT))

        histItems = LinearLayout(this)
        histItems.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        scroll.addView(histItems, FrameLayout.LayoutParams(MP, ViewGroup.LayoutParams.WRAP_CONTENT))
        histPanel.addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f))

        body.addView(histPanel, FrameLayout.LayoutParams(MP, MP).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        return root
    }

    private fun makeKey(id: String): ImageView {
        val iv = ImageView(this)
        val res = resources.getIdentifier("k_$id", "drawable", packageName)
        iv.setImageResource(res)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setPadding(dp(3), dp(3), dp(3), dp(3))
        iv.contentDescription = CalcLogic.sym(id)
        iv.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(60).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(60).start()
            }
            false
        }
        iv.setOnClickListener {
            state = CalcLogic.press(state, id)
            refresh()
            save()
        }
        return iv
    }

    private fun toggleHistory() {
        histOpen = !histOpen
        if (histOpen) {
            buildHistory()
            histPanel.alpha = 0f
            histPanel.translationY = -dp(40).toFloat()
            histPanel.visibility = View.VISIBLE
            histPanel.animate().alpha(1f).translationY(0f).setDuration(180).start()
        } else {
            histPanel.animate().alpha(0f).translationY(-dp(40).toFloat()).setDuration(150)
                .withEndAction { histPanel.visibility = View.GONE }.start()
        }
        refresh()
    }

    private fun buildHistory() {
        val items = CalcLogic.parseHist(state.hist)
        histTitle.text = "History (${items.size}/${CalcLogic.MAX_HIST})"
        histItems.removeAllViews()
        if (items.isEmpty()) {
            histItems.addView(TextView(this).apply {
                text = "No operations yet"; setTextColor(grey); textSize = 16f
                gravity = Gravity.CENTER; setPadding(dp(8), dp(32), dp(8), dp(8))
            }, LinearLayout.LayoutParams(MP, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        items.forEachIndexed { i, item ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.gravity = Gravity.END
            row.setPadding(dp(8), dp(8), dp(8), dp(8))
            row.addView(TextView(this).apply {
                text = item.first + " ="; setTextColor(grey); textSize = 15f; maxLines = 1
            })
            row.addView(TextView(this).apply {
                text = item.second; setTextColor(lilac); textSize = 26f; maxLines = 1
                typeface = Typeface.DEFAULT_BOLD
            })
            row.setOnClickListener {
                state = CalcLogic.pick(state, i)
                save()
                toggleHistory()
            }
            histItems.addView(row, LinearLayout.LayoutParams(MP, ViewGroup.LayoutParams.WRAP_CONTENT))
            histItems.addView(View(this).apply { setBackgroundColor(Color.parseColor("#261845")) },
                LinearLayout.LayoutParams(MP, dp(1)))
        }
    }

    private fun refresh() {
        mainText.text = state.expr.ifEmpty { "0" }
        subText.text = state.last.ifEmpty { " " }
        chev.text = if (histOpen) "⌃" else "⌄"
    }

    private fun save() {
        getSharedPreferences("calc", MODE_PRIVATE).edit()
            .putString("expr", state.expr).putBoolean("fresh", state.fresh)
            .putString("last", state.last).putString("hist", state.hist).apply()
    }
}
