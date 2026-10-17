package com.example.calculator

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private val EXPR = stringPreferencesKey("expr")
private val LASTOP = stringPreferencesKey("lastop")
private val HIST = stringPreferencesKey("hist")
private val FRESH = booleanPreferencesKey("fresh")
private val OPEN = booleanPreferencesKey("open")
private val KEY = ActionParameters.Key<String>("key")
private val IDX = ActionParameters.Key<Int>("idx")

private val ROWS = listOf(
    listOf("c", "bk", "pct", "div"),
    listOf("7", "8", "9", "mul"),
    listOf("4", "5", "6", "sub"),
    listOf("1", "2", "3", "add"),
    listOf("0", "00", "dot", "eq")
)

private fun tile(id: String): Int = when (id) {
    "c" -> R.drawable.k_c; "bk" -> R.drawable.k_bk; "pct" -> R.drawable.k_pct; "div" -> R.drawable.k_div
    "7" -> R.drawable.k_7; "8" -> R.drawable.k_8; "9" -> R.drawable.k_9; "mul" -> R.drawable.k_mul
    "4" -> R.drawable.k_4; "5" -> R.drawable.k_5; "6" -> R.drawable.k_6; "sub" -> R.drawable.k_sub
    "1" -> R.drawable.k_1; "2" -> R.drawable.k_2; "3" -> R.drawable.k_3; "add" -> R.drawable.k_add
    "0" -> R.drawable.k_0; "00" -> R.drawable.k_00; "dot" -> R.drawable.k_dot; else -> R.drawable.k_eq
}

class CalcWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val size = LocalSize.current
        val prefs = currentState<Preferences>()
        val expr = prefs[EXPR] ?: ""
        val lastOp = prefs[LASTOP] ?: ""
        val open = prefs[OPEN] ?: false
        val hist = CalcLogic.parseHist(prefs[HIST] ?: "")

        val w = size.width.value
        val h = size.height.value
        val cell = minOf(h / 6f, w / 4f)
        val ds = (cell * 0.46f).coerceIn(16f, 56f)
        val availW = (w - 56f).coerceAtLeast(20f)
        // Micsoram textul dupa lungime, ca sa incapa cat mai mult
        fun fit(text: String, base: Float, min: Float) =
            minOf(base, availW / (maxOf(text.length, 1) * 0.58f)).coerceAtLeast(min)

        val lilac = ColorProvider(Color(0xFFF1E6FF))
        val grey = ColorProvider(Color(0xFFA995D6))
        val mainText = expr.ifEmpty { "0" }
        val histSize = (cell * 0.4f).coerceIn(14f, 28f)
        val histSmall = (cell * 0.24f).coerceIn(10f, 16f)

        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(ImageProvider(R.drawable.bg_widget))
                .padding(4.dp)
        ) {
            // Afisaj (apasa ca sa deschizi istoricul)
            Box(
                modifier = GlanceModifier.fillMaxWidth().height(((h - 8f) / 6f).dp).padding(2.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize()
                        .background(ImageProvider(R.drawable.bg_display))
                        .clickable(actionRunCallback<ToggleHistoryAction>())
                        .padding(start = 8.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.width(26.dp).fillMaxHeight()) {
                        Text(
                            text = if (open) "⌃" else "⌄",
                            style = TextStyle(color = ColorProvider(Color(0xFFB58CFF)), fontSize = (cell * 0.26f).coerceIn(11f, 20f).sp)
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = "\uD83D\uDC3E",
                            style = TextStyle(fontSize = (cell * 0.26f).coerceIn(11f, 20f).sp)
                        )
                    }
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = lastOp.ifEmpty { " " },
                            maxLines = 1,
                            style = TextStyle(color = grey, fontSize = fit(lastOp, ds * 0.4f, 7f).sp, textAlign = TextAlign.End)
                        )
                        Text(
                            text = mainText,
                            maxLines = 1,
                            style = TextStyle(color = lilac, fontSize = fit(mainText, ds, 9f).sp, textAlign = TextAlign.End)
                        )
                    }
                }
            }

            if (open) {
                // Panou istoric, scrollabil
                Column(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                        .background(ImageProvider(R.drawable.bg_display))
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "History (${hist.size}/${CalcLogic.MAX_HIST})",
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(color = grey, fontSize = (cell * 0.2f).coerceIn(9f, 14f).sp)
                        )
                        Text(
                            text = "Clear",
                            modifier = GlanceModifier.clickable(actionRunCallback<ClearHistoryAction>()).padding(4.dp),
                            style = TextStyle(color = lilac, fontSize = (cell * 0.2f).coerceIn(9f, 14f).sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    if (hist.isEmpty()) {
                        Text(
                            text = "No operations yet",
                            modifier = GlanceModifier.fillMaxWidth().padding(12.dp),
                            style = TextStyle(color = grey, fontSize = histSmall.sp, textAlign = TextAlign.Center)
                        )
                    } else {
                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            itemsIndexed(hist) { index, item ->
                                Column(
                                    modifier = GlanceModifier.fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .clickable(actionRunCallback<PickHistoryAction>(actionParametersOf(IDX to index))),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = item.first + " =",
                                        maxLines = 1,
                                        style = TextStyle(color = grey, fontSize = histSmall.sp, textAlign = TextAlign.End)
                                    )
                                    Text(
                                        text = item.second,
                                        maxLines = 1,
                                        style = TextStyle(color = lilac, fontSize = histSize.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                ROWS.forEach { row ->
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        row.forEach { id ->
                            Box(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(2.dp)
                                    .clickable(actionRunCallback<KeyAction>(actionParametersOf(KEY to id))),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(tile(id)),
                                    contentDescription = CalcLogic.sym(id),
                                    contentScale = ContentScale.Fit,
                                    modifier = GlanceModifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class KeyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[KEY] ?: return
        updateAppWidgetState(context, glanceId) { p: MutablePreferences ->
            val s = CalcLogic.press(CalcState(p[EXPR] ?: "", p[FRESH] ?: false, p[LASTOP] ?: "", p[HIST] ?: ""), key)
            p[EXPR] = s.expr
            p[FRESH] = s.fresh
            p[LASTOP] = s.last
            p[HIST] = s.hist
        }
        CalcWidget().update(context, glanceId)
    }
}

class ToggleHistoryAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { p: MutablePreferences ->
            p[OPEN] = !(p[OPEN] ?: false)
        }
        CalcWidget().update(context, glanceId)
    }
}

class ClearHistoryAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { p: MutablePreferences ->
            p[HIST] = ""
            p[LASTOP] = ""
        }
        CalcWidget().update(context, glanceId)
    }
}

class PickHistoryAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val idx = parameters[IDX] ?: return
        updateAppWidgetState(context, glanceId) { p: MutablePreferences ->
            val item = CalcLogic.parseHist(p[HIST] ?: "").getOrNull(idx)
            if (item != null) {
                p[EXPR] = item.second
                p[FRESH] = true
                p[LASTOP] = item.first + " ="
                p[OPEN] = false
            }
        }
        CalcWidget().update(context, glanceId)
    }
}

class CalcWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalcWidget()
}
