package com.example.ui.messenger

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ContactEntity
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsTab(
    myUserId: String,
    contacts: List<ContactEntity>,
    searchQuery: String,
    onContactClick: (ContactEntity) -> Unit,
    onAddContactClick: () -> Unit,
    onDeleteConversation: (ContactEntity, Boolean) -> Unit = { _, _ -> },
    onClearConversationMessages: (ContactEntity) -> Unit = {},
    onTogglePinContact: (ContactEntity) -> Unit = {},
    onStartVoiceCall: (ContactEntity) -> Unit = {},
    onStartVideoCall: (ContactEntity) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedContactForMenu by remember { mutableStateOf<ContactEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredContacts = if (searchQuery.isBlank()) {
        contacts
    } else {
        contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.id.contains(searchQuery, ignoreCase = true)
        }
    }

    if (filteredContacts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = WaLightTealPrimary.copy(alpha = 0.1f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "No Chats",
                            tint = WaLightTealPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Encrypted Vault is Clear",
                    color = WaLightTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your ID: $myUserId\nDusre mobile ka Gmail ya Stealth ID add karne ke liye '+' dabayein.",
                    color = WaLightTextSecondary,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAddContactClick,
                    colors = ButtonDefaults.buttonColors(containerColor = WaLightTealPrimary)
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Contact by User ID / Gmail", color = Color.White)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .testTag("chats_list")
        ) {


            items(filteredContacts, key = { it.id }) { contact ->
                ChatItemRow(
                    contact = contact,
                    onClick = { onContactClick(contact) },
                    onLongClick = { selectedContactForMenu = contact }
                )
                HorizontalDivider(
                    color = Color(0xFFF1F5F9),
                    thickness = 0.5.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }

    // WhatsApp-Style Contextual Bottom Sheet for Chat Long-Press
    if (selectedContactForMenu != null) {
        val contact = selectedContactForMenu!!
        ModalBottomSheet(
            onDismissRequest = { selectedContactForMenu = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Header with contact details
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(WaLightTealPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.name.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name,
                            color = WaLightTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ID: ${contact.id}",
                            color = WaLightTealPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(onClick = { selectedContactForMenu = null }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = WaLightTextSecondary)
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                Spacer(modifier = Modifier.height(8.dp))

                // Pin / Unpin Option
                ChatMenuOptionItem(
                    icon = if (contact.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                    title = if (contact.isPinned) "Unpin Chat" else "Pin to top",
                    subtitle = if (contact.isPinned) "Remove pin from top" else "Sabse upar rakhein",
                    onClick = {
                        onTogglePinContact(contact)
                        selectedContactForMenu = null
                        Toast.makeText(context, if (contact.isPinned) "Chat unpinned" else "Chat pinned to top", Toast.LENGTH_SHORT).show()
                    }
                )

                // Voice Call Option
                ChatMenuOptionItem(
                    icon = Icons.Default.Call,
                    title = "Voice Call",
                    subtitle = "Real-time encrypted audio call",
                    iconTint = WaLightTealPrimary,
                    onClick = {
                        selectedContactForMenu = null
                        onStartVoiceCall(contact)
                    }
                )

                // Video Call Option
                ChatMenuOptionItem(
                    icon = Icons.Default.Videocam,
                    title = "Video Call",
                    subtitle = "Encrypted live video stream",
                    iconTint = WaLightTealPrimary,
                    onClick = {
                        selectedContactForMenu = null
                        onStartVideoCall(contact)
                    }
                )

                // Clear Chat History Option
                ChatMenuOptionItem(
                    icon = Icons.Default.CleaningServices,
                    title = "Clear Chat History",
                    subtitle = "Dono phones aur cloud se sabhi messages saaf karein",
                    iconTint = Color(0xFFF59E0B),
                    onClick = {
                        showClearConfirmDialog = true
                    }
                )

                // Delete Chat Option
                ChatMenuOptionItem(
                    icon = Icons.Default.Delete,
                    title = "Delete Chat",
                    subtitle = "Chat aur saare messages permanently hatayein",
                    iconTint = Color(0xFFDC2626),
                    textColor = Color(0xFFDC2626),
                    onClick = {
                        showDeleteConfirmDialog = true
                    }
                )

                // Mute Notifications Option
                ChatMenuOptionItem(
                    icon = Icons.Default.NotificationsOff,
                    title = "Mute Notifications",
                    subtitle = "8 ghante ke liye alerts band karein",
                    onClick = {
                        selectedContactForMenu = null
                        Toast.makeText(context, "Notifications muted for ${contact.name}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Delete Chat Confirmation Dialog
    if (showDeleteConfirmDialog && selectedContactForMenu != null) {
        val contactToDelete = selectedContactForMenu!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Chat?", fontWeight = FontWeight.Bold, color = WaLightTextPrimary)
                }
            },
            text = {
                Text(
                    text = "Kya aap '${contactToDelete.name}' ke sath chat aur sabhi messages ko delete karna chahte hain?\n\nYeh aapke device, peer device aur Firebase Firestore database se permanently hat jayega.",
                    color = WaLightTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteConversation(contactToDelete, true)
                        showDeleteConfirmDialog = false
                        selectedContactForMenu = null
                        Toast.makeText(context, "Chat deleted successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = WaLightTextPrimary)
                }
            },
            containerColor = Color.White
        )
    }

    // Clear Chat History Confirmation Dialog
    if (showClearConfirmDialog && selectedContactForMenu != null) {
        val contactToClear = selectedContactForMenu!!
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, tint = Color(0xFFF59E0B))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Chat Messages?", fontWeight = FontWeight.Bold, color = WaLightTextPrimary)
                }
            },
            text = {
                Text(
                    text = "'${contactToClear.name}' ke sabhi messages dono phones se delete ho jayenge, lekin ye contact list me bana rahega.",
                    color = WaLightTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearConversationMessages(contactToClear)
                        showClearConfirmDialog = false
                        selectedContactForMenu = null
                        Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                ) {
                    Text("Clear Messages", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = WaLightTextPrimary)
                }
            },
            containerColor = Color.White
        )
    }
}

@Composable
private fun ChatMenuOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = WaLightTextPrimary,
    textColor: Color = WaLightTextPrimary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = WaLightTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatItemRow(
    contact: ContactEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val avatarBg = try {
        Color(android.graphics.Color.parseColor(contact.avatarColorHex))
    } catch (e: Exception) {
        WaLightTealPrimary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("chat_item_${contact.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle with User ID initial
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(avatarBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (contact.isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(WaLightStatusUnread)
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Name, User ID and about text
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contact.name,
                    color = WaLightTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Active",
                    color = WaLightTealPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User ID tag badge
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = contact.id,
                        color = WaLightTealPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }

                Text(
                    text = contact.about,
                    color = WaLightTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (contact.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = WaLightTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Disappearing",
                        tint = WaLightFlameWarning,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Quick 3-dots action icon button for touch accessibility
        IconButton(
            onClick = onLongClick,
            modifier = Modifier
                .size(36.dp)
                .testTag("chat_options_button_${contact.id}")
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Chat Options",
                tint = WaLightTextSecondary.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
