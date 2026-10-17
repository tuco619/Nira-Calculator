package com.example.calculator

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

/**
 * Classic RemoteViews widget: every tap is handled right here and the widget is redrawn
 * immediately (no background jobs), so it keeps responding even right after the app was closed.
 */
class CalcWidgetProvider : AppWidgetProvider() {

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
                    refresh(context)
                }
            }
            ACTION_TOGGLE -> {
                setOpen(context, !isOpen(context))
                refresh(context)
            }
            ACTION_CLEAR -> {
                saveState(context, loadState(context).copy(hist = "", last = ""))
                refresh(context)
            }
            ACTION_PICK -> {
                val idx = intent.getIntExtra(EXTRA_IDX, -1)
                if (idx >= 0) {
                    saveState(context, CalcLogic.pick(loadState(context), idx))
                    setOpen(context, false)
                    refresh(context)
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_KEY = "com.example.calculator.KEY"
        const val ACTION_TOGGLE = "com.example.calculator.TOGGLE"
        const val ACTION_CLEAR = "com.example.calculator.CLEAR"
        const val ACTION_PICK = "com.example.calculator.PICK"
        const val EXTRA_KEY = "key"
        const val EXTRA_IDX = "idx"
        private const val PREFS = "widget_calc"

        private val KEY_VIEWS = mapOf(
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

        fun loadState(ctx: Context): CalcState {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return CalcState(
                p.getString("expr", "") ?: "",
                p.getBoolean("fresh", false),
                p.getString("last", "") ?: "",
                p.getString("hist", "") ?: ""
            )
        }

        fun saveState(ctx: Context, s: CalcState) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("expr", s.expr)
                .putBoolean("fresh", s.fresh)
                .putString("last", s.last)
                .putString("hist", s.hist)
                .apply()
        }

        fun isOpen(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("open", false)

        fun setOpen(ctx: Context, open: Boolean) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("open", open).apply()
        }

        private fun broadcast(
            ctx: Context,
            actionName: String,
            dataUri: String,
            key: String? = null,
            mutable: Boolean = false
        ): PendingIntent {
            val i = Intent(ctx, CalcWidgetProvider::class.java)
            i.action = actionName
            i.data = Uri.parse(dataUri) // makes every PendingIntent unique
            if (key != null) i.putExtra(EXTRA_KEY, key)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (mutable) {
                if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            } else {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            return PendingIntent.getBroadcast(ctx, 0, i, flags)
        }

        private fun buildViews(ctx: Context, widgetId: Int): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_calc)
            val s = loadState(ctx)
            val open = isOpen(ctx)

            rv.setTextViewText(R.id.w_main, s.expr.ifEmpty { "0" })
            rv.setTextViewText(R.id.w_last, s.last.ifEmpty { " " })
            rv.setTextViewText(R.id.w_chev, if (open) "\u2303" else "\u2304")
            rv.setViewVisibility(R.id.w_keypad, if (open) View.GONE else View.VISIBLE)
            rv.setViewVisibility(R.id.w_hist, if (open) View.VISIBLE else View.GONE)
            rv.setTextViewText(
                R.id.w_hist_title,
                "History (" + CalcLogic.parseHist(s.hist).size + "/" + CalcLogic.MAX_HIST + ")"
            )

            for ((key, viewId) in KEY_VIEWS) {
                rv.setOnClickPendingIntent(viewId, broadcast(ctx, ACTION_KEY, "calc://key/" + key, key))
            }
            rv.setOnClickPendingIntent(R.id.w_display, broadcast(ctx, ACTION_TOGGLE, "calc://toggle"))
            rv.setOnClickPendingIntent(R.id.w_clear, broadcast(ctx, ACTION_CLEAR, "calc://clear"))

            val svc = Intent(ctx, HistoryService::class.java)
            svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            rv.setRemoteAdapter(R.id.hist_list, svc)
            rv.setEmptyView(R.id.hist_list, R.id.hist_empty)
            rv.setPendingIntentTemplate(
                R.id.hist_list,
                broadcast(ctx, ACTION_PICK, "calc://pick", null, true)
            )
            return rv
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, CalcWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) mgr.updateAppWidget(id, buildViews(ctx, id))
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.hist_list)
        }
    }
}
