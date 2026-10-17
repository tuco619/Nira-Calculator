package com.example.calculator

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class HistoryService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        HistoryFactory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        )
}

class HistoryFactory(
    private val ctx: Context,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<Pair<String, String>> = emptyList()

    private fun load() {
        items = CalcLogic.parseHist(CalcWidgetProvider.loadState(ctx).hist)
    }

    override fun onCreate() { load() }
    override fun onDataSetChanged() { load() }
    override fun onDestroy() {}
    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_hist_item)
        if (position < 0 || position >= items.size) return rv
        val item = items[position]

        // Scale the text with the widget size
        val opts = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(widgetId)
        val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220).toFloat()
        val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 330).toFloat()
        val cell = minOf(h / 6f, w / 4f)
        val small = (cell * 0.24f).coerceIn(10f, 16f)
        val big = (cell * 0.4f).coerceIn(14f, 28f)

        rv.setTextViewText(R.id.h_e, item.first + " =")
        rv.setTextViewText(R.id.h_r, item.second)
        rv.setTextViewTextSize(R.id.h_e, TypedValue.COMPLEX_UNIT_SP, small)
        rv.setTextViewTextSize(R.id.h_r, TypedValue.COMPLEX_UNIT_SP, big)
        rv.setOnClickFillInIntent(R.id.hist_row, Intent().putExtra(CalcWidgetProvider.EXTRA_IDX, position))
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
