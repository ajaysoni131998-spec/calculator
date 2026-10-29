package com.example.ui.calculator

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.CalculationHistoryItem
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onUnlock: (isDecoy: Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is CalculatorNavEvent.UnlockMessenger -> onUnlock(event.isDecoy)
                is CalculatorNavEvent.ShowToast -> {}
            }
        }
    }

    Scaffold(
        containerColor = CalcLightBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Calculator",
                        color = CalcLightKeyNumberText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .testTag("calculator_title")
                            .clickable { /* tap */ }
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleHistory() },
                        modifier = Modifier.testTag("calculator_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = CalcLightKeyActionText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CalcLightBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {


                // Calculator Display Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = if (uiState.formula.isEmpty()) "0" else uiState.formula,
                        color = CalcLightKeyNumberText,
                        fontSize = when {
                            uiState.formula.length > 14 -> 32.sp
                            uiState.formula.length > 8 -> 44.sp
                            else -> 56.sp
                        },
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Default,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("calculator_formula_display")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (uiState.previewResult.isNotEmpty()) {
                        Text(
                            text = "= ${uiState.previewResult}",
                            color = CalcLightKeyActionText,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.testTag("calculator_preview_display")
                        )
                    }
                }

                // Keypad Grid with clean light keys
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: AC, ( ), %, ÷
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CalcButton(
                            text = "AC",
                            color = CalcLightKeyClearBg,
                            textColor = CalcLightKeyClearText,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onClear() }
                        )
                        CalcButton(
                            text = "( )",
                            color = CalcLightKeyAction,
                            textColor = CalcLightKeyActionText,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onParenthesis() }
                        )
                        CalcButton(
                            text = "%",
                            color = CalcLightKeyAction,
                            textColor = CalcLightKeyActionText,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onPercentage() }
                        )
                        CalcButton(
                            text = "÷",
                            color = CalcLightKeyAction,
                            textColor = CalcLightKeyActionText,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onOperator("÷") }
                        )
                    }

                    // Row 2: 7, 8, 9, ×
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CalcButton(text = "7", modifier = Modifier.weight(1f), onClick = { viewModel.onDigit("7") })
                        CalcButton(text = "8", modifier = Modifier.weight(1f), onClick = { viewModel.onDigit("8") })
                        CalcButton(text = "9", modifier = Modifier.weight(1f), onClick = { viewModel.onDigit("9") })
                        CalcButton(
                            text = "×",
                            color = CalcLightKeyAction,
                            textColor = CalcLightKeyActionText,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onOperator("×") }
                        )
                    }

                    // Row 3: 4, 5, 6, -
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CalcButton(text = "4", modifier = Modifier.weight(1f), onClick = { viewModel.onDigit("4") })
                        CalcButton(text = "5", modifier = Modifier.weight(1f), onClick = { viewModel.onDigit("5") })
                        CalcButton(text = "6", modifier = Modifier.weight(1f), onClick = { viewModel.onDigit("6") })
                        CalcButton(
                            text = "-",
                            color = CalcLightKeyAction,
                            textColor = CalcLightKeyActionText,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onOperator("-") }
                        )
                    }

                    // Row 4: 1, 2, 3, +
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CalcButton(text = "1", modifier = Modifier.weight(1f), onClick = { viewModel.onDigit("1") })
                        CalcButton(text = "2", modifier = Modifier.weight(1f), onClick = { viewModel.onDigit("2") })
                        CalcButton(text = "3", modifier = Modifier.weight(1f), onClick = { viewModel.onDigit("3") })
                        CalcButton(
                            text = "+",
                            color = CalcLightKeyAction,
                            textColor = CalcLightKeyActionText,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onOperator("+") }
                        )
                    }

                    // Row 5: 0, ., DEL, =
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CalcButton(text = "0", modifier = Modifier.weight(1f), onClick = { viewModel.onDigit("0") })
                        CalcButton(text = ".", modifier = Modifier.weight(1f), onClick = { viewModel.onDecimal() })
                        CalcIconButton(
                            icon = Icons.Default.Backspace,
                            color = CalcLightKeyAction,
                            tint = CalcLightKeyActionText,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onDelete() }
                        )
                        CalcButton(
                            text = "=",
                            color = CalcLightKeyEquals,
                            textColor = CalcLightKeyEqualsText,
                            isBold = true,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onEquals() }
                        )
                    }
                }
            }

            // Slide-over Calculation History
            AnimatedVisibility(
                visible = uiState.isHistoryOpen,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Surface(
                    color = CalcLightSurface,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.toggleHistory() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close History",
                                    tint = CalcLightKeyNumberText
                                )
                            }
                            Text(
                                text = "Calculation History",
                                color = CalcLightKeyNumberText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { viewModel.clearHistory() }) {
                                Text("Clear", color = CalcLightKeyClearText)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (uiState.history.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No calculations yet",
                                    color = CalcLightKeyNumberText.copy(alpha = 0.5f),
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.history) { item ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = CalcLightBackground
                                        ),
                                        border = BorderStroke(1.dp, CalcLightKeyNumberBorder),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.restoreHistoryItem(item) }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = item.expression,
                                                color = CalcLightKeyNumberText.copy(alpha = 0.7f),
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = "= ${item.result}",
                                                color = CalcLightKeyActionText,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CalcLightKeyNumber,
    textColor: Color = CalcLightKeyNumberText,
    isBold: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = textColor
        ),
        border = if (color == CalcLightKeyNumber) BorderStroke(1.dp, CalcLightKeyNumberBorder) else null,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .height(68.dp)
            .testTag("calc_btn_$text")
    ) {
        Text(
            text = text,
            fontSize = if (text.length > 2) 20.sp else 24.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun CalcIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    color: Color = CalcLightKeyNumber,
    tint: Color = CalcLightKeyNumberText,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = tint
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .height(68.dp)
            .testTag("calc_btn_backspace")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Backspace",
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
