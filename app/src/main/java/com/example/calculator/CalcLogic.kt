package com.example.calculator

data class CalcState(
    val expr: String = "",
    val fresh: Boolean = false,
    val last: String = "",
    val hist: String = ""
)

object CalcLogic {
    const val OPS = "+-×÷"
    const val MAX_HIST = 50
    const val ERROR = "Error"

    val ROWS = listOf(
        listOf("c", "bk", "pct", "div"),
        listOf("7", "8", "9", "mul"),
        listOf("4", "5", "6", "sub"),
        listOf("1", "2", "3", "add"),
        listOf("00", "0", "dot", "eq")
    )

    fun sym(id: String) = when (id) {
        "bk" -> "⌫"; "pct" -> "%"; "div" -> "÷"; "mul" -> "×"
        "sub" -> "-"; "add" -> "+"; "dot" -> "."; else -> id
    }

    // History is stored as lines "expr<TAB>result", newest first
    fun parseHist(h: String): List<Pair<String, String>> =
        h.split("\n").filter { it.isNotEmpty() }.mapNotNull {
            val a = it.split("\t")
            if (a.size == 2) a[0] to a[1] else null
        }

    fun addHist(h: String, e: String, r: String): String =
        (listOf("$e\t$r") + h.split("\n").filter { it.isNotEmpty() }).take(MAX_HIST).joinToString("\n")

    fun press(s: CalcState, key: String): CalcState {
        if (key == "mascot") return s
        var expr = s.expr
        var fresh = s.fresh
        var last = s.last
        var hist = s.hist
        val k = sym(key)
        val isOp = k.length == 1 && OPS.contains(k)
        when {
            key == "c" -> { expr = ""; fresh = false }
            key == "bk" -> { expr = if (fresh) "" else expr.dropLast(1); fresh = false }
            key == "eq" -> {
                if (expr.isNotEmpty() && !fresh) {
                    val e = expr
                    val r = CalcEngine.eval(e)
                    expr = r
                    fresh = true
                    if (r != ERROR && r != e) {
                        last = "$e ="
                        hist = addHist(hist, e, r)
                    }
                }
            }
            isOp -> {
                if (expr != ERROR && !(expr.isEmpty() && k != "-")) {
                    // continuing after a result: the small line shows the operation and its result
                    if (fresh && last.endsWith(" =") && expr.isNotEmpty() && !OPS.contains(expr.last())) {
                        last = "$last $expr"
                    }
                    val base = if (expr.isNotEmpty() && OPS.contains(expr.last())) expr.dropLast(1) else expr
                    expr = base + k
                    fresh = false
                }
            }
            key == "pct" -> {
                if (expr.isNotEmpty() && expr != ERROR) { expr += k; fresh = false }
            }
            else -> {
                if (fresh) { expr = k; fresh = false }
                else if (expr.length < 60) { expr += k }
            }
        }
        return CalcState(expr, fresh, last, hist)
    }

    fun pick(s: CalcState, idx: Int): CalcState {
        val item = parseHist(s.hist).getOrNull(idx) ?: return s
        return s.copy(expr = item.second, fresh = true, last = item.first + " =")
    }
}
