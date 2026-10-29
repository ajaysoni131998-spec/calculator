package com.example.ui.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseUser
import com.example.ui.theme.*

@Composable
fun StealthSettingsScreen(
    currentSecretCode: String,
    currentDecoyCode: String,
    currentUserId: String,
    currentDisplayName: String,
    currentDestructSec: Int,
    currentUser: FirebaseUser? = null,
    onGoogleSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onSaveSettings: (secretCode: String, decoyCode: String, userId: String, displayName: String, destructSec: Int) -> Unit,
    onNukeClick: () -> Unit
) {
    var secretCode by remember { mutableStateOf(currentSecretCode) }
    var decoyCode by remember { mutableStateOf(currentDecoyCode) }
    var userId by remember { mutableStateOf(currentUserId) }
    var displayName by remember { mutableStateOf(currentDisplayName) }
    var selectedDestructSec by remember { mutableIntStateOf(currentDestructSec) }
    var savedFeedback by remember { mutableStateOf(false) }

    val destructOptions = listOf(
        Pair(10, "10s (Default)"),
        Pair(30, "30s"),
        Pair(60, "1m"),
        Pair(300, "5m")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WaLightChatBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("stealth_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // User Identity Profile Card
        Card(
            colors = CardDefaults.cardColors(containerColor = WaLightCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, tint = WaLightTealPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Your Device Identity (Sender ID)",
                        color = WaLightTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current Device Identifier:", color = WaLightTextSecondary, fontSize = 11.sp)
                            Text(userId, color = WaLightTealPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val randomId = "GHOST_" + (1000..9999).random()
                                userId = randomId
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaLightTealPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Random ID", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text("Custom User ID (e.g. DEVICE_1, DEVICE_2, or your name)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_user_id_input")
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Your Display Name") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Vault Calculator Passcode Card
        Card(
            colors = CardDefaults.cardColors(containerColor = WaLightCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Calculate, contentDescription = null, tint = CalcLightKeyEquals)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Calculator Stealth Unlock Code",
                        color = WaLightTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Type this number in the calculator and press '=' to unlock your private messenger.",
                    color = WaLightTextSecondary,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = secretCode,
                    onValueChange = { secretCode = it },
                    label = { Text("Master Vault Code") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_secret_code_input")
                )

                OutlinedTextField(
                    value = decoyCode,
                    onValueChange = { decoyCode = it },
                    label = { Text("Decoy Code (Opens fake decoy vault)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Self-destruct timer Card (1 minute requirement)
        Card(
            colors = CardDefaults.cardColors(containerColor = WaLightCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = WaLightFlameWarning)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Disappearing Message Duration",
                        color = WaLightTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Messages automatically self-destruct once read. (Default: 1 minute).",
                    color = WaLightTextSecondary,
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    destructOptions.forEach { (sec, label) ->
                        val isSelected = selectedDestructSec == sec
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDestructSec = sec },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WaLightTealPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFF1F5F9),
                                labelColor = WaLightTextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Save Button
        Button(
            onClick = {
                onSaveSettings(secretCode, decoyCode, userId, displayName, selectedDestructSec)
                savedFeedback = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = WaLightTealPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_settings_button")
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (savedFeedback) "Settings Saved Successfully! ✅" else "Save Settings",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        // Emergency Panic Wipe Button
        OutlinedButton(
            onClick = onNukeClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("nuke_data_button")
        ) {
            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFD32F2F))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Emergency Wipe All Chats & Data",
                color = Color(0xFFD32F2F),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
