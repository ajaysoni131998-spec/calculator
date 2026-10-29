package com.example.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calculator.CalculationHistoryItem
import com.example.calculator.CalculatorEngine
import com.example.data.preferences.StealthPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CalculatorNavEvent {
    data class UnlockMessenger(val isDecoy: Boolean) : CalculatorNavEvent()
    data class ShowToast(val message: String) : CalculatorNavEvent()
}

data class CalculatorUiState(
    val formula: String = "",
    val previewResult: String = "",
    val history: List<CalculationHistoryItem> = emptyList(),
    val isHistoryOpen: Boolean = false,
    val showInitialHint: Boolean = false
)

class CalculatorViewModel(
    private val preferences: StealthPreferences,
    private val engine: CalculatorEngine = CalculatorEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<CalculatorNavEvent>()
    val navEvents = _navEvents.asSharedFlow()

    init {
        // Show subtle first-time stealth instruction if not seen yet
        if (!preferences.isInitialSetupDone) {
            _uiState.value = _uiState.value.copy(showInitialHint = true)
        }
    }

    fun dismissInitialHint() {
        preferences.isInitialSetupDone = true
        _uiState.value = _uiState.value.copy(showInitialHint = false)
    }

    fun onDigit(digit: String) {
        val current = _uiState.value.formula
        // Allow typing consecutive zeros so PIN codes like "0000" work properly!
        val newFormula = if (current == "0" && digit != "0") digit else current + digit
        updateFormula(newFormula)
    }

    fun onOperator(op: String) {
        val current = _uiState.value.formula
        if (current.isEmpty() && (op == "-" || op == "+")) {
            updateFormula(op)
            return
        }
        if (current.isNotEmpty()) {
            val lastChar = current.last().toString()
            val newFormula = if (isOperator(lastChar)) {
                current.dropLast(1) + op
            } else {
                current + op
            }
            updateFormula(newFormula)
        }
    }

    fun onDecimal() {
        val current = _uiState.value.formula
        val parts = current.split('+', '-', '×', '÷', '%', '^')
        val lastPart = parts.lastOrNull() ?: ""
        if (!lastPart.contains('.')) {
            val newFormula = if (current.isEmpty() || isOperator(current.last().toString())) {
                current + "0."
            } else {
                current + "."
            }
            updateFormula(newFormula)
        }
    }

    fun onParenthesis() {
        val current = _uiState.value.formula
        val openCount = current.count { it == '(' }
        val closeCount = current.count { it == ')' }
        val newFormula = if (openCount > closeCount && current.isNotEmpty() && !isOperator(current.last().toString())) {
            current + ")"
        } else {
            current + "("
        }
        updateFormula(newFormula)
    }

    fun onSquareRoot() {
        val current = _uiState.value.formula
        val newFormula = current + "√("
        updateFormula(newFormula)
    }

    fun onPercentage() {
        val current = _uiState.value.formula
        if (current.isNotEmpty() && !isOperator(current.last().toString())) {
            updateFormula(current + "%")
        }
    }

    fun onClear() {
        _uiState.value = _uiState.value.copy(
            formula = "",
            previewResult = ""
        )
    }

    fun onDelete() {
        val current = _uiState.value.formula
        if (current.isNotEmpty()) {
            val newFormula = current.dropLast(1)
            updateFormula(newFormula)
        }
    }

    fun onEquals() {
        val input = _uiState.value.formula.trim()
        val secretCode = preferences.secretCode
        val decoyCode = preferences.decoyCode

        // Check if formula matches secret passcode
        if (input == secretCode || input == "$secretCode=") {
            viewModelScope.launch {
                // Clear display to remain stealthy
                _uiState.value = _uiState.value.copy(formula = "", previewResult = "")
                _navEvents.emit(CalculatorNavEvent.UnlockMessenger(isDecoy = false))
            }
            return
        }

        // Check if formula matches decoy code (e.g. 0000, 0000=, 0=)
        val isDecoyMatch = input == decoyCode ||
                input == "$decoyCode=" ||
                (decoyCode == "0000" && (input == "0" || input == "00" || input == "000" || input == "0000"))

        if (isDecoyMatch) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(formula = "", previewResult = "")
                _navEvents.emit(CalculatorNavEvent.UnlockMessenger(isDecoy = true))
            }
            return
        }

        // Standard calculation evaluation
        val evalResult = engine.evaluate(input)
        evalResult.onSuccess { res ->
            val newItem = CalculationHistoryItem(input, res)
            val updatedHistory = listOf(newItem) + _uiState.value.history
            _uiState.value = _uiState.value.copy(
                formula = res,
                previewResult = "",
                history = updatedHistory.take(30)
            )
        }.onFailure {
            _uiState.value = _uiState.value.copy(previewResult = "Error")
        }
    }

    fun onSecretLongPress() {
        // Direct backdoor unlock via long press on app title or header
        viewModelScope.launch {
            _navEvents.emit(CalculatorNavEvent.UnlockMessenger(isDecoy = false))
        }
    }

    fun toggleHistory() {
        _uiState.value = _uiState.value.copy(isHistoryOpen = !_uiState.value.isHistoryOpen)
    }

    fun clearHistory() {
        _uiState.value = _uiState.value.copy(history = emptyList())
    }

    fun restoreHistoryItem(item: CalculationHistoryItem) {
        _uiState.value = _uiState.value.copy(
            formula = item.expression,
            previewResult = item.result,
            isHistoryOpen = false
        )
    }

    private fun updateFormula(newFormula: String) {
        val preview = if (newFormula.isNotEmpty()) {
            engine.evaluate(newFormula).getOrNull() ?: ""
        } else ""
        _uiState.value = _uiState.value.copy(
            formula = newFormula,
            previewResult = preview
        )
    }

    private fun isOperator(s: String): Boolean {
        return s in listOf("+", "-", "×", "÷", "%", "^")
    }
}
