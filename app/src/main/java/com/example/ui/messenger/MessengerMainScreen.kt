package com.example.ui.messenger

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.example.data.repository.FirestoreCallSignal
import com.google.firebase.auth.FirebaseUser
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerMainScreen(
    viewModel: MessengerViewModel,
    currentUser: FirebaseUser? = null,
    onGoogleSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onLockToCalculator: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    val statusStories by viewModel.statusStories.collectAsState()
    val activeMessages by viewModel.activeMessages.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showUserIdDialog by remember { mutableStateOf(false) }
    var showVaultSettingsSheet by remember { mutableStateOf(false) }

    // If incoming call is ringing, show IncomingCallScreen overlay
    if (uiState.incomingCall != null) {
        IncomingCallScreen(
            incomingCall = uiState.incomingCall!!,
            onAccept = { viewModel.acceptIncomingCall() },
            onDecline = { viewModel.declineIncomingCall() }
        )
        return
    }

    // If active call is ongoing, show CallScreen overlay
    if (uiState.activeCall != null) {
        CallScreen(
            callInfo = uiState.activeCall!!,
            myUserId = uiState.myUserId,
            onEndCall = { viewModel.endCall() },
            onToggleMute = { viewModel.toggleMute() },
            onToggleSpeaker = { viewModel.toggleSpeaker() },
            onToggleCamera = { viewModel.toggleCamera() }
        )
        return
    }

    // If viewing a status story, show StoryViewerModal
    if (uiState.viewingStory != null) {
        StoryViewerModal(
            story = uiState.viewingStory!!,
            onDismiss = { viewModel.viewStory(null) }
        )
        return
    }

    // If active chat is open, show ChatDetailScreen
    if (uiState.activeContact != null) {
        ChatDetailScreen(
            contact = uiState.activeContact!!,
            messages = activeMessages,
            remainingSecondsMap = uiState.remainingSecondsMap,
            myUserId = uiState.myUserId,
            onBack = { viewModel.closeConversation() },
            onSendMessage = { text, mediaType, mediaPayload, isViewOnce ->
                viewModel.sendMessage(text, mediaType, mediaPayload, isViewOnce)
            },
            onStartVoiceCall = {
                viewModel.startVoiceCall(uiState.activeContact!!)
            },
            onStartVideoCall = {
                viewModel.startVideoCall(uiState.activeContact!!)
            },
            onQuickLock = onLockToCalculator,
            onCamouflageMessage = { text, style, callback ->
                viewModel.camouflageMessage(text, style, callback)
            },
            onScanPrivacy = { text, callback ->
                viewModel.scanPrivacy(text, callback)
            },
            onGetSmartReplies = { lastMsg, callback ->
                viewModel.getSmartReplies(lastMsg, callback)
            }
        )
        return
    }

    // Main WhatsApp-style multi-tab layout
    Scaffold(
        containerColor = Color.White,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(WaLightTopBar)) {
                TopAppBar(
                    title = {
                        if (uiState.isSearching) {
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search peer ID or name...", color = Color.White.copy(alpha = 0.7f)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_text_field")
                            )
                        } else {
                            Text(
                                text = if (uiState.isDecoyMode) "Decoy Notes" else "GhostChat",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        if (uiState.isSearching) {
                            IconButton(onClick = { viewModel.toggleSearch() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    actions = {
                        // User ID button in upper right corner (takes minimum space, easy 1-tap view & copy)
                        IconButton(
                            onClick = { showUserIdDialog = true },
                            modifier = Modifier.testTag("show_user_id_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = "My User ID",
                                tint = Color.White
                            )
                        }

                        // Search toggle
                        IconButton(
                            onClick = { viewModel.toggleSearch() },
                            modifier = Modifier.testTag("toggle_search_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isSearching) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }

                        // Quick Lock to Calculator button
                        IconButton(
                            onClick = onLockToCalculator,
                            modifier = Modifier.testTag("quick_lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock to Calculator",
                                tint = Color.White
                            )
                        }

                        // 3-dots Menu
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("messenger_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Vault & Stealth Settings", color = WaLightTextPrimary, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = WaLightTealPrimary) },
                                onClick = {
                                    showMenu = false
                                    showVaultSettingsSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Secret & Decoy PINs", color = WaLightTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null, tint = WaLightTealPrimary) },
                                onClick = {
                                    showMenu = false
                                    showVaultSettingsSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Auto-Delete Timer (10s)", color = WaLightTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = WaLightTealPrimary) },
                                onClick = {
                                    showMenu = false
                                    showVaultSettingsSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Google Account & Sync", color = WaLightTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = WaLightTealPrimary) },
                                onClick = {
                                    showMenu = false
                                    showVaultSettingsSheet = true
                                }
                            )
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            DropdownMenuItem(
                                text = { Text("Add Peer by User ID / Gmail", color = WaLightTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = WaLightTealPrimary) },
                                onClick = {
                                    showMenu = false
                                    viewModel.showAddContactDialog(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("New Disappearing Story", color = WaLightTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = WaLightTealPrimary) },
                                onClick = {
                                    showMenu = false
                                    viewModel.showNewStoryDialog(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Lock to Calculator", color = WaLightTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Calculate, contentDescription = null, tint = CalcLightKeyEquals) },
                                onClick = {
                                    showMenu = false
                                    onLockToCalculator()
                                }
                            )
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            DropdownMenuItem(
                                text = { Text("Emergency Nuke All", color = Color(0xFFFF5252)) },
                                leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFFF5252)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.showNukeDialog(true)
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = WaLightTopBar
                    )
                )

                // WhatsApp Tabs right below TopAppBar (3 standard tabs: CHATS, STATUS, CALLS)
                TabRow(
                    selectedTabIndex = uiState.currentTab.coerceIn(0, 2),
                    containerColor = WaLightTopBar,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        val activeIndex = uiState.currentTab.coerceIn(0, 2)
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeIndex]),
                            color = Color.White,
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = uiState.currentTab == 0,
                        onClick = { viewModel.setTab(0) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "CHATS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (uiState.currentTab == 0) Color.White else Color.White.copy(alpha = 0.7f)
                                )
                                if (contacts.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = contacts.size.toString(),
                                                color = WaLightTealPrimary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("tab_chats")
                    )
                    Tab(
                        selected = uiState.currentTab == 1,
                        onClick = { viewModel.setTab(1) },
                        text = {
                            Text(
                                text = "STATUS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (uiState.currentTab == 1) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                        },
                        modifier = Modifier.testTag("tab_status")
                    )
                    Tab(
                        selected = uiState.currentTab == 2,
                        onClick = { viewModel.setTab(2) },
                        text = {
                            Text(
                                text = "CALLS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (uiState.currentTab == 2) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                        },
                        modifier = Modifier.testTag("tab_calls")
                    )
                }
            }
        },
        floatingActionButton = {
            when (uiState.currentTab) {
                // Tab 0 (Chats): No floating button on the right side as requested by user
                1 -> {
                    FloatingActionButton(
                        onClick = { viewModel.showNewStoryDialog(true) },
                        containerColor = WaLightTealPrimary,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.testTag("fab_add_story")
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Add Status")
                    }
                }
                2 -> {
                    FloatingActionButton(
                        onClick = {
                            if (contacts.isNotEmpty()) {
                                viewModel.startVoiceCall(contacts.first())
                            } else {
                                viewModel.showAddContactDialog(true)
                            }
                        },
                        containerColor = WaLightTealPrimary,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.testTag("fab_new_call")
                    ) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = "New Call")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                0 -> ChatsTab(
                    myUserId = uiState.myUserId,
                    contacts = contacts,
                    searchQuery = uiState.searchQuery,
                    onContactClick = { viewModel.openConversation(it) },
                    onAddContactClick = { viewModel.showAddContactDialog(true) },
                    onDeleteConversation = { contact, deleteContact -> viewModel.deleteConversation(contact, deleteContact) },
                    onClearConversationMessages = { contact -> viewModel.clearConversationMessages(contact) },
                    onTogglePinContact = { contact -> viewModel.togglePinContact(contact) },
                    onStartVoiceCall = { contact -> viewModel.startVoiceCall(contact) },
                    onStartVideoCall = { contact -> viewModel.startVideoCall(contact) }
                )
                1 -> StatusTab(
                    myDisplayName = uiState.myDisplayName,
                    stories = statusStories,
                    onAddStoryClick = { viewModel.showNewStoryDialog(true) },
                    onStoryClick = { viewModel.viewStory(it) }
                )
                else -> CallsTab(
                    callLogs = callLogs,
                    contacts = contacts,
                    onStartVoiceCall = { viewModel.startVoiceCall(it) },
                    onStartVideoCall = { viewModel.startVideoCall(it) }
                )
            }
        }
    }

    // Add Contact Dialog
    if (uiState.showAddContactDialog) {
        AddContactDialog(
            onDismiss = { viewModel.showAddContactDialog(false) },
            onAdd = { id, name, about ->
                viewModel.addContact(id, name, about)
            }
        )
    }

    // User ID Quick View & Copy Dialog
    if (showUserIdDialog) {
        val clipboardManager = LocalClipboardManager.current
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showUserIdDialog = false },
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = WaLightTealPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Your GhostChat User ID",
                        color = WaLightTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Share this ID with your friend so they can add you and start chatting or calling:",
                        color = WaLightTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.myUserId,
                                    color = WaLightTealPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!currentUser?.email.isNullOrBlank()) {
                                    Text(
                                        text = currentUser?.email ?: "",
                                        color = WaLightTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(uiState.myUserId))
                                    Toast.makeText(context, "User ID copied! Share with friend.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("copy_user_id_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy User ID",
                                    tint = WaLightTealPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "💡 Dusra user '+' par click karke ye ID dale to aap dono ka real-time chat connect ho jayega.",
                        color = WaLightTextSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showUserIdDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = WaLightTealPrimary)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    // New Story Dialog
    if (uiState.showNewStoryDialog) {
        NewStoryDialog(
            onDismiss = { viewModel.showNewStoryDialog(false) },
            onPost = { caption, bgHex, storyType, mediaPayload, linkUrl ->
                viewModel.postStory(caption, bgHex, storyType, mediaPayload, linkUrl)
            }
        )
    }

    // Emergency Nuke Confirmation Dialog
    if (uiState.showNukeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showNukeDialog(false) },
            containerColor = Color.White,
            title = {
                Text("Emergency Panic Wipe?", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will immediately and permanently erase all chat logs, contacts, call history, and credentials. The app will revert to a completely fresh calculator disguise.",
                    color = WaLightTextPrimary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.nukeAllData {
                            onLockToCalculator()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Nuke All Data", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showNukeDialog(false) }) {
                    Text("Cancel", color = WaLightTextSecondary)
                }
            }
        )
    }

    // Vault & Stealth Settings ModalBottomSheet (Accessible via top-right 3-dots Option button)
    if (showVaultSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showVaultSettingsSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = WaLightTealPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vault & Stealth Settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = WaLightTextPrimary
                        )
                    }
                    IconButton(onClick = { showVaultSettingsSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = WaLightTextSecondary)
                    }
                }
                HorizontalDivider(color = Color(0xFFE2E8F0))
                StealthSettingsScreen(
                    currentSecretCode = viewModel.getSecretCode(),
                    currentDecoyCode = viewModel.getDecoyCode(),
                    currentUserId = uiState.myUserId,
                    currentDisplayName = uiState.myDisplayName,
                    currentDestructSec = viewModel.getDefaultDestructSeconds(),
                    currentUser = currentUser,
                    onGoogleSignIn = onGoogleSignIn,
                    onSignOut = onSignOut,
                    onSaveSettings = { secret, decoy, uid, name, sec ->
                        viewModel.updateStealthSettings(secret, decoy, uid, name, sec)
                    },
                    onNukeClick = {
                        showVaultSettingsSheet = false
                        viewModel.showNukeDialog(true)
                    }
                )
            }
        }
    }
}

@Composable
fun IncomingCallScreen(
    incomingCall: FirestoreCallSignal,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val isVideo = incomingCall.callType.equals("VIDEO", ignoreCase = true)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "incomingPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isVideo) listOf(Color(0xFF0F172A), Color(0xFF020617))
                    else listOf(WaLightTealPrimary, Color(0xFF0F172A))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isVideo) "GhostChat Incoming Video Call" else "GhostChat Incoming Voice Call",
                        color = Color(0xFF22C55E),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = incomingCall.callerName.ifBlank { incomingCall.callerId },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "ID: ${incomingCall.callerId}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Ringing... • End-to-End Encrypted",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 15.sp
                )
            }

            // Pulsing Avatar
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E).copy(alpha = 0.25f))
                )
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF15803D),
                    modifier = Modifier.size(120.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (incomingCall.callerName.ifBlank { incomingCall.callerId }).take(1).uppercase(),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Accept / Decline Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline (Red Button)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onDecline,
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("decline_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Decline", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                // Accept (Green Button)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onAccept,
                        containerColor = Color(0xFF16A34A),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("accept_call_button")
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = "Accept",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Accept", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
