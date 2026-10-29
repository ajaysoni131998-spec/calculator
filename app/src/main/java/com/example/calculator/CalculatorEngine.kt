package com.example.calculator

import java.text.DecimalFormat
import kotlin.math.pow
import kotlin.math.sqrt

data class CalculationHistoryItem(
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)

class CalculatorEngine {

    private val decimalFormat = DecimalFormat("#,###.########")

    fun evaluate(expression: String): Result<String> {
        val sanitized = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace(" ", "")

        if (sanitized.isEmpty()) return Result.success("0")

        return try {
            val parser = ExprParser(sanitized)
            val result = parser.parse()
            if (result.isNaN() || result.isInfinite()) {
                Result.failure(ArithmeticException("Cannot divide by zero"))
            } else {
                val formatted = if (result == result.toLong().toDouble()) {
                    result.toLong().toString()
                } else {
                    decimalFormat.format(result)
                }
                Result.success(formatted)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class ExprParser(private val expr: String) {
        private var pos = -1
        private var ch = -1

        private fun nextChar() {
            ch = if (++pos < expr.length) expr[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < expr.length) throw RuntimeException("Unexpected: " + ch.toChar())
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                x = expr.substring(startPos, pos).toDouble()
            } else if (eat('√'.code)) {
                val inner = parseFactor()
                x = sqrt(inner)
            } else {
                throw RuntimeException("Unexpected: " + ch.toChar())
            }

            if (eat('^'.code)) x = x.pow(parseFactor())
            if (eat('%'.code)) x /= 100.0

            return x
        }
    }
}
