package com.example

import com.example.calculator.CalculatorEngine
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    private val engine = CalculatorEngine()

    @Test
    fun testBasicCalculations() {
        assertEquals("4", engine.evaluate("2+2").getOrNull())
        assertEquals("15", engine.evaluate("3×5").getOrNull())
        assertEquals("10", engine.evaluate("20÷2").getOrNull())
        assertEquals("7", engine.evaluate("10-3").getOrNull())
    }

    @Test
    fun testOperatorPrecedenceAndParentheses() {
        // 2 + 3 * 4 = 14, not 20
        assertEquals("14", engine.evaluate("2+3×4").getOrNull())
        // (2 + 3) * 4 = 20
        assertEquals("20", engine.evaluate("(2+3)×4").getOrNull())
    }

    @Test
    fun testSquareRootAndPower() {
        assertEquals("5", engine.evaluate("√(25)").getOrNull())
        assertEquals("8", engine.evaluate("2^3").getOrNull())
    }
}
