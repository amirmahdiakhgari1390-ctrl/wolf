package com.wolf.assistant

/** ماشین‌حساب ساده: + - * / و پرانتز */
object Calc {
    fun eval(input: String): Double? = try {
        val p = Parser(input.replace(" ", ""))
        val v = p.expr()
        if (p.pos != p.s.length) null else v
    } catch (e: Exception) {
        null
    }

    fun format(v: Double): String =
        if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')

    private class Parser(val s: String) {
        var pos = 0

        fun expr(): Double {
            var v = term()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                val t = term()
                v = if (op == '+') v + t else v - t
            }
            return v
        }

        fun term(): Double {
            var v = factor()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos++]
                val f = factor()
                v = if (op == '*') v * f else v / f
            }
            return v
        }

        fun factor(): Double {
            if (pos < s.length && s[pos] == '-') {
                pos++
                return -factor()
            }
            if (pos < s.length && s[pos] == '(') {
                pos++
                val v = expr()
                if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException()
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException()
            return s.substring(start, pos).toDouble()
        }
    }
}
