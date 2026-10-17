package com.example.calculator

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

private const val MAX_ROWS = 8

private val ALL_KEY_VIEWS = mapOf(
    "c" to R.id.k_c,
    "bk" to R.id.k_bk,
    "pct" to R.id.k_pct,
    "div" to R.id.k_div,
    "7" to R.id.k_7,
    "8" to R.id.k_8,
    "9" to R.id.k_9,
    "mul" to R.id.k_mul,
    "4" to R.id.k_4,
    "5" to R.id.k_5,
    "6" to R.id.k_6,
    "sub" to R.id.k_sub,
    "1" to R.id.k_1,
    "2" to R.id.k_2,
    "3" to R.id.k_3,
    "add" to R.id.k_add,
    "00" to R.id.k_00,
    "0" to R.id.k_0,
    "dot" to R.id.k_dot,
    "eq" to R.id.k_eq
)
private val ROW_IDS = intArrayOf(R.id.h_row_0, R.id.h_row_1, R.id.h_row_2, R.id.h_row_3, R.id.h_row_4, R.id.h_row_5, R.id.h_row_6, R.id.h_row_7)
private val E_IDS = intArrayOf(R.id.h_e_0, R.id.h_e_1, R.id.h_e_2, R.id.h_e_3, R.id.h_e_4, R.id.h_e_5, R.id.h_e_6, R.id.h_e_7)
private val R_IDS = intArrayOf(R.id.h_r_0, R.id.h_r_1, R.id.h_r_2, R.id.h_r_3, R.id.h_r_4, R.id.h_r_5, R.id.h_r_6, R.id.h_r_7)

/**
 * Classic RemoteViews widget: every tap is handled here and the whole widget (including the
 * history pages) is redrawn immediately, so nothing can get stuck after the app is closed.
 * One subclass per theme.
 */
abstract class BaseCalcProvider : AppWidgetProvider() {

    abstract val calcTheme: CalcTheme

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_KEY -> {
                val key = intent.getStringExtra(EXTRA_KEY)
                if (key != null) {
                    saveState(context, CalcLogic.press(loadState(context), key))
                    if (key == "eq") setOffset(context, 0)
                    refresh(context)
                }
            }
            ACTION_TOGGLE -> {
                setOpen(context, !isOpen(context))
                setOffset(context, 0)
                refresh(context)
            }
            ACTION_CLEAR -> {
                saveState(context, loadState(context).copy(hist = "", last = ""))
                setOffset(context, 0)
                refresh(context)
            }
            ACTION_PICK -> {
                val idx = intent.getIntExtra(EXTRA_N, -1)
                if (idx >= 0) {
                    saveState(context, CalcLogic.pick(loadState(context), idx))
                    setOpen(context, false)
                    refresh(context)
                }
            }
            ACTION_PAGE -> {
                val step = intent.getIntExtra(EXTRA_N, 0)
                val count = CalcLogic.parseHist(loadState(context).hist).size
                val next = (getOffset(context) + step).coerceIn(0, maxOf(0, count - 1))
                setOffset(context, next)
                refresh(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(calcTheme.prefsName, Context.MODE_PRIVATE)

    private fun loadState(ctx: Context): CalcState {
        val p = prefs(ctx)
        return CalcState(
            p.getString("expr", "") ?: "",
            p.getBoolean("fresh", false),
            p.getString("last", "") ?: "",
            p.getString("hist", "") ?: ""
        )
    }

    private fun saveState(ctx: Context, s: CalcState) {
        prefs(ctx).edit()
            .putString("expr", s.expr)
            .putBoolean("fresh", s.fresh)
            .putString("last", s.last)
            .putString("hist", s.hist)
            .apply()
    }

    private fun isOpen(ctx: Context): Boolean = prefs(ctx).getBoolean("open", false)
    private fun setOpen(ctx: Context, open: Boolean) { prefs(ctx).edit().putBoolean("open", open).apply() }
    private fun getOffset(ctx: Context): Int = prefs(ctx).getInt("hoff", 0)
    private fun setOffset(ctx: Context, v: Int) { prefs(ctx).edit().putInt("hoff", v).apply() }

    private fun broadcast(
        ctx: Context,
        actionName: String,
        dataUri: String,
        key: String? = null,
        n: Int? = null
    ): PendingIntent {
        val i = Intent(ctx, javaClass)
        i.action = actionName
        i.data = Uri.parse(dataUri) // makes every PendingIntent unique
        if (key != null) i.putExtra(EXTRA_KEY, key)
        if (n != null) i.putExtra(EXTRA_N, n)
        return PendingIntent.getBroadcast(
            ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // How many history rows fit in this widget
    private fun rowsFor(ctx: Context, widgetId: Int): Int {
        val h = try {
            AppWidgetManager.getInstance(ctx).getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 330)
        } catch (e: Exception) {
            330
        }
        val usable = h * 5f / 6f - 52f
        return (usable / 42f).toInt().coerceIn(3, MAX_ROWS)
    }

    private fun buildViews(ctx: Context, widgetId: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, calcTheme.layoutRes)
        val s = loadState(ctx)
        val open = isOpen(ctx)
        val items = CalcLogic.parseHist(s.hist)

        rv.setTextViewText(R.id.w_main, s.expr.ifEmpty { "0" })
        rv.setTextViewText(R.id.w_last, s.last.ifEmpty { " " })
        rv.setTextViewText(R.id.w_chev, if (open) "\u2303" else "\u2304")
        rv.setViewVisibility(R.id.w_keypad, if (open) View.GONE else View.VISIBLE)
        rv.setViewVisibility(R.id.w_hist, if (open) View.VISIBLE else View.GONE)

        for (key in calcTheme.keyIds) {
            val viewId = ALL_KEY_VIEWS.getValue(key)
            rv.setOnClickPendingIntent(viewId, broadcast(ctx, ACTION_KEY, "calc://key/" + key, key))
        }
        rv.setOnClickPendingIntent(R.id.w_display, broadcast(ctx, ACTION_TOGGLE, "calc://toggle"))
        rv.setOnClickPendingIntent(R.id.w_clear, broadcast(ctx, ACTION_CLEAR, "calc://clear"))

        // History: one page of rows, with up/down arrows to move through all 50
        val rows = rowsFor(ctx, widgetId)
        val off = getOffset(ctx).coerceIn(0, maxOf(0, items.size - 1))
        rv.setTextViewText(R.id.w_hist_title, "History (" + items.size + "/" + CalcLogic.MAX_HIST + ")")
        rv.setViewVisibility(R.id.h_empty, if (items.isEmpty()) View.VISIBLE else View.GONE)
        for (i in 0 until MAX_ROWS) {
            if (i >= rows) {
                rv.setViewVisibility(ROW_IDS[i], View.GONE)
                continue
            }
            val abs = off + i
            if (abs >= items.size) {
                rv.setViewVisibility(ROW_IDS[i], View.INVISIBLE)
                continue
            }
            rv.setViewVisibility(ROW_IDS[i], View.VISIBLE)
            rv.setTextViewText(E_IDS[i], items[abs].first + " =")
            rv.setTextViewText(R_IDS[i], items[abs].second)
            rv.setOnClickPendingIntent(
                ROW_IDS[i], broadcast(ctx, ACTION_PICK, "calc://pick/" + abs, null, abs)
            )
        }
        rv.setOnClickPendingIntent(
            R.id.w_up, broadcast(ctx, ACTION_PAGE, "calc://page/up/" + rows, null, -rows)
        )
        rv.setOnClickPendingIntent(
            R.id.w_down, broadcast(ctx, ACTION_PAGE, "calc://page/down/" + rows, null, rows)
        )
        return rv
    }

    private fun refresh(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, javaClass))
        for (id in ids) mgr.updateAppWidget(id, buildViews(ctx, id))
    }

    companion object {
        const val ACTION_KEY = "com.example.calculator.KEY"
        const val ACTION_TOGGLE = "com.example.calculator.TOGGLE"
        const val ACTION_CLEAR = "com.example.calculator.CLEAR"
        const val ACTION_PICK = "com.example.calculator.PICK"
        const val ACTION_PAGE = "com.example.calculator.PAGE"
        const val EXTRA_KEY = "key"
        const val EXTRA_N = "n"
    }
}

class CalcWidgetProvider : BaseCalcProvider() {
    override val calcTheme: CalcTheme get() = Themes.ALL[0]
}

class CalcWidgetProvider2 : BaseCalcProvider() {
    override val calcTheme: CalcTheme get() = Themes.ALL[1]
}

class CalcWidgetProvider3 : BaseCalcProvider() {
    override val calcTheme: CalcTheme get() = Themes.ALL[2]
}
