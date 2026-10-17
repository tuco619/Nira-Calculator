package com.example.calculator

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.floor

object CalcEngine {
    fun eval(s: String): String = try {
        val t = s.replace('×', '*').replace('÷', '/')
        var i = 0
        fun peek() = if (i < t.length) t[i] else '\u0000'
        fun num(): Double {
            val st = i
            while (peek().isDigit() || peek() == '.') i++
            return t.substring(st, i).toDouble()
        }
        fun factor(): Double {
            if (peek() == '-') { i++; return -factor() }
            var v = num()
            while (peek() == '%') { i++; v /= 100 }
            return v
        }
        fun term(): Double {
            var v = factor()
            while (peek() == '*' || peek() == '/') {
                val op = t[i++]
                val r = factor()
                v = if (op == '*') v * r else v / r
            }
            return v
        }
        fun expr(): Double {
            var v = term()
            while (peek() == '+' || peek() == '-') {
                val op = t[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }
        val r = expr()
        if (i != t.length || r.isNaN() || r.isInfinite()) "Error" else fmt(r)
    } catch (e: Exception) {
        "Error"
    }

    private fun fmt(r: Double): String =
        if (r == floor(r) && abs(r) < 1e15) r.toLong().toString()
        else BigDecimal(r).round(MathContext(10)).stripTrailingZeros().toPlainString()
}
