package com.example.ui.messenger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (id: String, name: String, about: String) -> Unit
) {
    var userId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("Available on GhostChat") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Add Peer Contact",
                color = WaLightTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Dusre phone se connect karne ke liye unki Gmail ID (e.g. peer@gmail.com) ya Stealth ID (e.g. #GHOST-4921) dalein.",
                    color = WaLightTextSecondary,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text("Peer Gmail ID / Stealth ID") },
                    placeholder = { Text("peer@gmail.com or #GHOST-1234") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Badge, contentDescription = null, tint = WaLightTealPrimary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WaLightTextPrimary,
                        unfocusedTextColor = WaLightTextPrimary,
                        focusedBorderColor = WaLightTealPrimary,
                        unfocusedBorderColor = CalcLightKeyNumberBorder,
                        focusedLabelColor = WaLightTealPrimary,
                        unfocusedLabelColor = WaLightTextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_contact_id_input")
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Phone 2 / Alex") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = WaLightTealPrimary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WaLightTextPrimary,
                        unfocusedTextColor = WaLightTextPrimary,
                        focusedBorderColor = WaLightTealPrimary,
                        unfocusedBorderColor = CalcLightKeyNumberBorder,
                        focusedLabelColor = WaLightTealPrimary,
                        unfocusedLabelColor = WaLightTextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_contact_name_input")
                )

                OutlinedTextField(
                    value = about,
                    onValueChange = { about = it },
                    label = { Text("Status / Note (Optional)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = WaLightTealPrimary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WaLightTextPrimary,
                        unfocusedTextColor = WaLightTextPrimary,
                        focusedBorderColor = WaLightTealPrimary,
                        unfocusedBorderColor = CalcLightKeyNumberBorder,
                        focusedLabelColor = WaLightTealPrimary,
                        unfocusedLabelColor = WaLightTextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanId = userId.trim()
                    val cleanName = if (name.isNotBlank()) name.trim() else cleanId
                    onAdd(cleanId, cleanName, about.trim())
                },
                enabled = userId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WaLightTealPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("add_contact_confirm_button")
            ) {
                Text("Add Peer", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = WaLightTextSecondary)
            }
        }
    )
}
