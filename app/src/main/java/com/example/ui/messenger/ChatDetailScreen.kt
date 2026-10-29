package com.example.ui.messenger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ai.PrivacyAnalysis
import com.example.data.local.ContactEntity
import com.example.data.local.MessageEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun encodeImageUriToBase64(context: Context, uri: Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (original == null) return ""
        val maxDim = 640
        val scale = minOf(1f, maxDim.toFloat() / maxOf(original.width, original.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
        } else original
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    }
}

fun extractVideoThumbnailBase64(context: Context, uri: Uri): String {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val frame = retriever.frameAtTime ?: retriever.getFrameAtTime(1000000)
        retriever.release()
        if (frame == null) return ""
        val maxDim = 480
        val scale = minOf(1f, maxDim.toFloat() / maxOf(frame.width, frame.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
        } else frame
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    contact: ContactEntity,
    messages: List<MessageEntity>,
    remainingSecondsMap: Map<String, Int>,
    myUserId: String = "",
    onBack: () -> Unit,
    onSendMessage: (text: String, mediaType: String, mediaPayload: String, isViewOnce: Boolean) -> Unit,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onQuickLock: () -> Unit,
    onCamouflageMessage: ((originalText: String, style: String, onResult: (String) -> Unit) -> Unit)? = null,
    onScanPrivacy: ((text: String, onResult: (PrivacyAnalysis) -> Unit) -> Unit)? = null,
    onGetSmartReplies: ((lastMsg: String, onResult: (List<String>) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var showEmojiBar by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecordDuration by remember { mutableStateOf(0) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isViewOnceForPick by remember { mutableStateOf(false) }
    var selectedMediaForFullscreen by remember { mutableStateOf<MessageEntity?>(null) }

    // Media Pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val base64 = encodeImageUriToBase64(context, uri)
            if (base64.isNotBlank()) {
                onSendMessage(
                    if (isViewOnceForPick) "🔒 View Once Photo" else "📷 Photo",
                    "IMAGE",
                    base64,
                    isViewOnceForPick
                )
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val thumbnailBase64 = extractVideoThumbnailBase64(context, uri)
            onSendMessage("🎥 Video", "VIDEO", thumbnailBase64, false)
        }
    }

    // AI Privacy & Camouflage state
    var showAiModal by remember { mutableStateOf(false) }
    var aiPrivacyAnalysis by remember { mutableStateOf<PrivacyAnalysis?>(null) }
    var smartReplies by remember { mutableStateOf<List<String>>(emptyList()) }
    var isAiGenerating by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    // Auto-scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Voice record timer simulation
    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            voiceRecordDuration = 0
            while (isRecordingVoice) {
                delay(1000)
                voiceRecordDuration++
            }
        }
    }

    Scaffold(
        containerColor = WaLightChatBackground,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { /* Contact info */ }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.name.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = contact.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = contact.id,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = " • 10s Destroy",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onStartVideoCall,
                        modifier = Modifier.testTag("chat_video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Call",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onStartVoiceCall,
                        modifier = Modifier.testTag("chat_voice_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Voice Call",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onQuickLock,
                        modifier = Modifier.testTag("chat_quick_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Quick Lock to Calculator",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WaLightTopBar
                )
            )
        }
    ) { innerPadding ->
        // Uses safeDrawing bottom inset:
        // When keyboard is down -> positions flush above gesture navigation bar.
        // When keyboard is up -> positions flush exactly on top of the keyboard with NO gap!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Privacy notice banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = WaLightFlameWarning,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Messages vanish 10 seconds after reading • 100% ephemeral",
                        color = WaLightTextSecondary,
                        fontSize = 11.sp
                    )
                }

                // Chat Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        // STRICT RULE: If message was sent from this device (senderId == "me" or matches myUserId),
                        // it appears on the RIGHT side (#D9FDD3).
                        // If received from the other device (senderId != "me"),
                        // it appears on the LEFT side (#FFFFFF).
                        val isMe = msg.senderId == "me" || 
                                   (myUserId.isNotBlank() && msg.senderId.equals(myUserId.trim(), ignoreCase = true))
                        val remainingSec = remainingSecondsMap[msg.id]

                        MessageBubbleItem(
                            message = msg,
                            isMe = isMe,
                            remainingSeconds = remainingSec,
                            timeText = timeFormat.format(Date(msg.timestamp)),
                            onMediaClick = { selectedMediaForFullscreen = it }
                        )
                    }
                }

                // AI Smart Quick Reply Suggestions (Collapsible)
                if (smartReplies.isNotEmpty()) {
                    Surface(
                        color = Color.White,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(smartReplies) { reply ->
                                SuggestionChip(
                                    onClick = {
                                        inputText = reply
                                        smartReplies = emptyList()
                                    },
                                    label = { Text(reply, fontSize = 12.sp, color = WaLightTealPrimary) }
                                )
                            }
                        }
                    }
                }

                // Quick Emoji Bar (Collapsible)
                AnimatedVisibility(
                    visible = showEmojiBar,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = Color.White,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            val quickEmojis = listOf("❤️", "😂", "🔥", "👍", "🤫", "🤐", "🔒", "🕵️‍♂️", "💥", "⚡", "🚀")
                            quickEmojis.forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 22.sp,
                                    modifier = Modifier
                                        .clickable {
                                            inputText += emoji
                                        }
                                        .padding(3.dp)
                                )
                            }
                        }
                    }
                }

                // Attachment Drawer
                AnimatedVisibility(
                    visible = showAttachmentMenu,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AttachmentOption(
                                icon = Icons.Default.Image,
                                label = "Photo",
                                color = Color(0xFFE91E63),
                                onClick = {
                                    showAttachmentMenu = false
                                    isViewOnceForPick = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            AttachmentOption(
                                icon = Icons.Default.PlayCircle,
                                label = "Video",
                                color = Color(0xFF9C27B0),
                                onClick = {
                                    showAttachmentMenu = false
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                }
                            )
                            AttachmentOption(
                                icon = Icons.Default.VisibilityOff,
                                label = "View Once",
                                color = Color(0xFFFF5722),
                                onClick = {
                                    showAttachmentMenu = false
                                    isViewOnceForPick = true
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            AttachmentOption(
                                icon = Icons.Default.LocationOn,
                                label = "Location",
                                color = Color(0xFF009688),
                                onClick = {
                                    showAttachmentMenu = false
                                    onSendMessage("📍 Stealth Coordinates: 28.6139° N, 77.2090° E", "LOCATION", "coords", false)
                                }
                            )
                            AttachmentOption(
                                icon = Icons.Default.Headphones,
                                label = "Audio Note",
                                color = Color(0xFFFF9800),
                                onClick = {
                                    showAttachmentMenu = false
                                    isRecordingVoice = true
                                }
                            )
                        }
                    }
                }

                // COMPACT Sleek Input Row (Halved width/bulkiness as requested)
                Surface(
                    color = Color.White,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRecordingVoice) {
                        // Voice Recording UI
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recording: ${voiceRecordDuration}s (Encrypted Voice Note)",
                                color = WaLightTextPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { isRecordingVoice = false },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = Color.Red, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    isRecordingVoice = false
                                    onSendMessage("🎙️ Voice Note (${voiceRecordDuration}s)", "AUDIO", "0:${voiceRecordDuration.toString().padStart(2, '0')}", false)
                                },
                                modifier = Modifier.size(34.dp).testTag("send_voice_note_button")
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send Voice", tint = WaLightTealPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        // Sleek Compact Input Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Sleek Pill Container for text & action icons
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 38.dp, max = 100.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = { showEmojiBar = !showEmojiBar },
                                        modifier = Modifier.size(32.dp).testTag("emoji_toggle_button")
                                    ) {
                                        Icon(
                                            imageVector = if (showEmojiBar) Icons.Default.Keyboard else Icons.Default.EmojiEmotions,
                                            contentDescription = "Emojis",
                                            tint = WaLightTextSecondary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    // Compact BasicTextField with half vertical footprint
                                    BasicTextField(
                                        value = inputText,
                                        onValueChange = { inputText = it },
                                        textStyle = TextStyle(
                                            color = WaLightTextPrimary,
                                            fontSize = 14.sp
                                        ),
                                        maxLines = 4,
                                        cursorBrush = SolidColor(WaLightTealPrimary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                            .testTag("message_input_field"),
                                        decorationBox = { innerTextField ->
                                            if (inputText.isEmpty()) {
                                                Text(
                                                    text = "Disappearing message...",
                                                    color = WaLightTextSecondary,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )

                                    // AI Privacy & Camouflage Button
                                    IconButton(
                                        onClick = {
                                            showAiModal = true
                                        },
                                        modifier = Modifier.size(32.dp).testTag("ai_features_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "GhostAI Privacy & Camouflage",
                                            tint = WaLightTealPrimary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { showAttachmentMenu = !showAttachmentMenu },
                                        modifier = Modifier.size(32.dp).testTag("attach_menu_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AttachFile,
                                            contentDescription = "Attach",
                                            tint = WaLightTextSecondary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Send or Mic button (Sleek 38dp circle)
                            if (inputText.isNotBlank()) {
                                FilledIconButton(
                                    onClick = {
                                        val text = inputText
                                        inputText = ""
                                        showEmojiBar = false
                                        onSendMessage(text, "NONE", "", false)
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = WaLightTealPrimary),
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("send_message_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                FilledIconButton(
                                    onClick = { isRecordingVoice = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = WaLightTealPrimary),
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("mic_record_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Record Voice Note",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // AI Privacy & Camouflage Modal Bottom Sheet
    if (showAiModal) {
        ModalBottomSheet(
            onDismissRequest = { showAiModal = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = WaLightTealPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GhostAI Privacy & Camouflage",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaLightTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Camouflage secret text into innocent disguises so no one looking at the screen can tell.",
                    fontSize = 12.sp,
                    color = WaLightTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isAiGenerating) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = WaLightTealPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = "DISGUISE CURRENT MESSAGE AS:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WaLightTextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isAiGenerating = true
                            onCamouflageMessage?.invoke(inputText.ifBlank { "Meet me tonight" }, "HOMEWORK") { result ->
                                isAiGenerating = false
                                inputText = result
                                showAiModal = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📐 Math HW", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            isAiGenerating = true
                            onCamouflageMessage?.invoke(inputText.ifBlank { "Important secret plan" }, "GROCERY") { result ->
                                isAiGenerating = false
                                inputText = result
                                showAiModal = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🛒 Grocery", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isAiGenerating = true
                            onCamouflageMessage?.invoke(inputText.ifBlank { "Confidential update" }, "WORK") { result ->
                                isAiGenerating = false
                                inputText = result
                                showAiModal = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📊 Slide Sync", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            isAiGenerating = true
                            onCamouflageMessage?.invoke(inputText.ifBlank { "Call me now" }, "CIPHER") { result ->
                                isAiGenerating = false
                                inputText = result
                                showAiModal = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🔐 Cipher", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(12.dp))

                // Privacy Scanner Button
                OutlinedButton(
                    onClick = {
                        isAiGenerating = true
                        onScanPrivacy?.invoke(inputText) { analysis ->
                            isAiGenerating = false
                            aiPrivacyAnalysis = analysis
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = WaLightTealPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run AI Privacy Leak Scanner", color = WaLightTealPrimary)
                }

                if (aiPrivacyAnalysis != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = if (aiPrivacyAnalysis!!.riskLevel == "SAFE") Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, if (aiPrivacyAnalysis!!.riskLevel == "SAFE") Color(0xFFBBF7D0) else Color(0xFFFECACA)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = aiPrivacyAnalysis!!.warning,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (aiPrivacyAnalysis!!.riskLevel == "SAFE") Color(0xFF15803D) else Color(0xFFB91C1C)
                            )
                            if (aiPrivacyAnalysis!!.stealthSuggestion.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = aiPrivacyAnalysis!!.stealthSuggestion,
                                    fontSize = 11.sp,
                                    color = WaLightTextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Suggest Smart Replies Button
                OutlinedButton(
                    onClick = {
                        isAiGenerating = true
                        val lastMsg = messages.lastOrNull { it.senderId != "me" }?.text ?: "Hello"
                        onGetSmartReplies?.invoke(lastMsg) { replies ->
                            isAiGenerating = false
                            smartReplies = replies
                            showAiModal = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.ChatBubbleOutline, contentDescription = null, tint = WaLightTealPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Suggest Smart Replies", color = WaLightTealPrimary)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (selectedMediaForFullscreen != null) {
        FullscreenMediaViewerDialog(
            message = selectedMediaForFullscreen!!,
            remainingSeconds = remainingSecondsMap[selectedMediaForFullscreen!!.id],
            onDismiss = { selectedMediaForFullscreen = null }
        )
    }
}

@Composable
fun AttachmentOption(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, color = WaLightTextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MessageBubbleItem(
    message: MessageEntity,
    isMe: Boolean,
    remainingSeconds: Int?,
    timeText: String,
    onMediaClick: ((MessageEntity) -> Unit)? = null
) {
    val bubbleColor = if (isMe) WaLightBubbleOutgoing else WaLightBubbleIncoming
    val textColor = WaLightTextPrimary

    val imageBitmap = remember(message.mediaPayload) {
        try {
            if ((message.mediaType == "IMAGE" || message.mediaType == "VIDEO") && message.mediaPayload.isNotBlank()) {
                val bytes = Base64.decode(message.mediaPayload, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isMe) 14.dp else 2.dp,
                bottomEnd = if (isMe) 2.dp else 14.dp
            ),
            shadowElevation = 0.5.dp,
            border = if (!isMe) BorderStroke(0.5.dp, Color(0xFFE2E8F0)) else null,
            modifier = Modifier
                .widthIn(min = 100.dp, max = 290.dp)
                .testTag(if (isMe) "message_bubble_me" else "message_bubble_other")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                // PHOTO ATTACHMENT RENDERING
                if (message.mediaType == "IMAGE") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.05f))
                            .clickable { onMediaClick?.invoke(message) }
                    ) {
                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Attached Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = WaLightTealPrimary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        if (message.isViewOnce) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "1 🔒 View Once",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // VIDEO ATTACHMENT RENDERING
                if (message.mediaType == "VIDEO") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .clickable { onMediaClick?.invoke(message) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Video Thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "🎥 Video Preview",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Other media types (Audio, Location)
                if (message.mediaType != "NONE" && message.mediaType != "IMAGE" && message.mediaType != "VIDEO" && message.mediaType.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val mediaIcon = when (message.mediaType) {
                            "AUDIO" -> Icons.Default.Audiotrack
                            "LOCATION" -> Icons.Default.LocationOn
                            else -> Icons.Default.AttachFile
                        }
                        Icon(
                            imageVector = mediaIcon,
                            contentDescription = null,
                            tint = WaLightTealPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (message.isViewOnce) "🔒 View-Once" else "Encrypted Media",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaLightTealPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Message Text / Caption
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Footer Row: Disappearing Timer + Timestamp + Read Checkmarks
                Row(
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ephemeral Countdown timer badge
                    if (remainingSeconds != null && remainingSeconds > 0) {
                        Surface(
                            color = if (remainingSeconds <= 15) Color(0xFFFFEBEE) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = if (remainingSeconds <= 15) Color(0xFFD32F2F) else Color(0xFF64748B),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${remainingSeconds}s",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remainingSeconds <= 15) Color(0xFFD32F2F) else Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    Text(
                        text = timeText,
                        color = WaLightTextSecondary,
                        fontSize = 10.sp
                    )

                    if (isMe) {
                        Spacer(modifier = Modifier.width(3.dp))
                        when (message.status) {
                            "READ" -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Read",
                                    tint = WaLightTicksBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            "DELIVERED" -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = Color(0xFF8696A0),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            else -> { // "SENT"
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Sent",
                                    tint = Color(0xFF8696A0),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fullscreen Interactive Media Viewer Dialog
 */
@Composable
fun FullscreenMediaViewerDialog(
    message: MessageEntity,
    remainingSeconds: Int?,
    onDismiss: () -> Unit
) {
    var isPlayingVideo by remember { mutableStateOf(true) }
    var videoProgress by remember { mutableFloatStateOf(0.35f) }

    val imageBitmap = remember(message.mediaPayload) {
        try {
            if (message.mediaPayload.isNotBlank()) {
                val bytes = Base64.decode(message.mediaPayload, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Main Media Content
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Full Media",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 60.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (message.mediaType == "VIDEO") Icons.Default.Videocam else Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            // Video Playback Simulation Controls
            if (message.mediaType == "VIDEO") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { isPlayingVideo = !isPlayingVideo },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlayingVideo) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlayingVideo) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Video Scrubber bar at bottom
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("0:08", color = Color.White, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = videoProgress,
                            onValueChange = { videoProgress = it },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = WaLightTealPrimary,
                                activeTrackColor = WaLightTealPrimary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("0:24", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }

            // Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                if (remainingSeconds != null && remainingSeconds > 0) {
                    Surface(
                        color = Color.Red.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Self-destructs in ${remainingSeconds}s",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Surface(
                        color = WaLightTealPrimary,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (message.isViewOnce) "🔒 View-Once" else "🔐 Encrypted",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
