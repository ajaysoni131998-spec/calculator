package com.example.ui.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CallLogEntity
import com.example.data.local.ContactEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallsTab(
    callLogs: List<CallLogEntity>,
    contacts: List<ContactEntity>,
    onStartVoiceCall: (ContactEntity) -> Unit,
    onStartVideoCall: (ContactEntity) -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    if (callLogs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = WaTopBar,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = WaEmerald,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Encrypted Calls Yet",
                    color = WaTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "High-definition encrypted voice & video calls. Tap a contact to start an untraceable call.",
                    color = WaTextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("calls_tab")
        ) {
            item {
                Text(
                    text = "ENCRYPTED CALL LOGS",
                    color = WaTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            items(callLogs, key = { it.id }) { log ->
                val matchingContact = contacts.find { it.id == log.contactId } ?: ContactEntity(
                    id = log.contactId,
                    name = log.contactName
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (log.isVideo) onStartVideoCall(matchingContact)
                            else onStartVoiceCall(matchingContact)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("call_log_item_${log.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(WaEmeraldDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = log.contactName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = log.contactName,
                            color = if (log.isMissed) Color(0xFFFF5252) else WaTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (arrowIcon, arrowTint) = when {
                                log.isMissed -> Pair(Icons.AutoMirrored.Filled.CallMissed, Color(0xFFFF5252))
                                log.isIncoming -> Pair(Icons.AutoMirrored.Filled.CallReceived, WaStatusUnread)
                                else -> Pair(Icons.AutoMirrored.Filled.CallMade, WaTextSecondary)
                            }

                            Icon(
                                imageVector = arrowIcon,
                                contentDescription = null,
                                tint = arrowTint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = dateFormat.format(Date(log.timestamp)),
                                color = WaTextSecondary,
                                fontSize = 13.sp
                            )
                            if (log.durationSeconds > 0) {
                                Text(
                                    text = " • ${log.durationSeconds}s",
                                    color = WaTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            if (log.isVideo) onStartVideoCall(matchingContact)
                            else onStartVoiceCall(matchingContact)
                        },
                        modifier = Modifier.testTag("redial_btn_${log.id}")
                    ) {
                        Icon(
                            imageVector = if (log.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = "Call",
                            tint = WaEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
