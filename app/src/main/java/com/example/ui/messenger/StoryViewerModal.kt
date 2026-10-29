package com.example.ui.messenger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.StatusStoryEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

@Composable
fun StoryViewerModal(
    story: StatusStoryEntity,
    onDismiss: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val uriHandler = LocalUriHandler.current

    // Auto-progress bar: 6 seconds per story
    LaunchedEffect(story.id) {
        progress = 0f
        val steps = 60
        for (i in 1..steps) {
            delay(100)
            progress = i.toFloat() / steps
        }
        onDismiss()
    }

    val storyBg = try {
        Color(android.graphics.Color.parseColor(story.backgroundColorHex))
    } catch (e: Exception) {
        WaTeal
    }

    // Decode image bitmap if it's an image story
    val imageBitmap = remember(story.mediaPayload, story.storyType) {
        if (story.storyType == "IMAGE" && story.mediaPayload.isNotBlank()) {
            try {
                val bytes = Base64.decode(story.mediaPayload, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (story.storyType == "IMAGE") Color.Black else storyBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .clickable { onDismiss() }
    ) {
        // If image story, render full image in background
        if (story.storyType == "IMAGE" && imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Status Image",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Story Progress Bar + Author info
            Column {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = story.authorName.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = story.authorName,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (story.storyType) {
                                        "IMAGE" -> Icons.Default.Image
                                        "VIDEO" -> Icons.Default.PlayArrow
                                        "LINK" -> Icons.Default.Share
                                        else -> Icons.Default.Edit
                                    },
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${story.storyType.lowercase().replaceFirstChar { it.uppercase() }} Status • 24h Disappearing",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            // Center Content (Type-specific)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (story.storyType) {
                    "TEXT" -> {
                        Text(
                            text = story.caption,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                    "LINK" -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .clickable {
                                    if (story.linkUrl.isNotBlank()) {
                                        try {
                                            val validUrl = if (!story.linkUrl.startsWith("http://") && !story.linkUrl.startsWith("https://")) {
                                                "https://${story.linkUrl}"
                                            } else story.linkUrl
                                            uriHandler.openUri(validUrl)
                                        } catch (e: Exception) {
                                            // Ignore open failure
                                        }
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = WaLightTealPrimary,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = story.caption.ifBlank { "Shared Link" },
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = story.linkUrl,
                                    color = WaTicksBlue,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        if (story.linkUrl.isNotBlank()) {
                                            try {
                                                val validUrl = if (!story.linkUrl.startsWith("http://") && !story.linkUrl.startsWith("https://")) {
                                                    "https://${story.linkUrl}"
                                                } else story.linkUrl
                                                uriHandler.openUri(validUrl)
                                            } catch (e: Exception) {
                                                // Ignore
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WaLightTealPrimary),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Link", color = Color.White)
                                }
                            }
                        }
                    }
                    "VIDEO" -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(220.dp)
                                .border(1.5.dp, WaLightTealPrimary, RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = WaLightTealPrimary,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(36.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = story.caption.ifBlank { "Video Status" },
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("▶ Video Playing", color = WaEmerald, fontSize = 12.sp)
                            }
                        }
                    }
                    else -> {
                        // Image with caption already shown
                    }
                }
            }

            // Bottom Caption & Dismiss Pill
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (story.caption.isNotBlank() && story.storyType == "IMAGE") {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = story.caption,
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Text(
                    text = "Tap anywhere to dismiss",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewStoryDialog(
    onDismiss: () -> Unit,
    onPost: (caption: String, bgHex: String, storyType: String, mediaPayload: String, linkUrl: String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Text, 1: Image, 2: Video, 3: Link
    var caption by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    var mediaPayload by remember { mutableStateOf("") }
    var pickedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoName by remember { mutableStateOf("") }

    val colors = listOf("#075E54", "#128C7E", "#25D366", "#0288D1", "#7B1FA2", "#C2185B", "#E65100")
    var selectedColor by remember { mutableStateOf(colors[1]) }

    // Android Zero-Permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() }
                if (bytes != null) {
                    val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (originalBitmap != null) {
                        val scaled = if (originalBitmap.width > 720 || originalBitmap.height > 720) {
                            val ratio = minOf(720f / originalBitmap.width, 720f / originalBitmap.height)
                            Bitmap.createScaledBitmap(
                                originalBitmap,
                                (originalBitmap.width * ratio).toInt(),
                                (originalBitmap.height * ratio).toInt(),
                                true
                            )
                        } else originalBitmap

                        pickedImageBitmap = scaled
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        mediaPayload = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    }
                }
            } catch (e: Exception) {
                // Ignore pick error
            }
        }
    }

    // Video Picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            videoName = uri.lastPathSegment ?: "video.mp4"
            mediaPayload = "VIDEO_SELECTED"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = "New Disappearing Status",
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
                // Tab Row for Status Types (Text, Image, Video, Link)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Text", modifier = Modifier.size(16.dp))
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Photo", modifier = Modifier.size(16.dp))
                    }
                    SegmentedButton(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Video", modifier = Modifier.size(16.dp))
                    }
                    SegmentedButton(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Link", modifier = Modifier.size(16.dp))
                    }
                }

                when (selectedTab) {
                    0 -> {
                        // Text Status
                        OutlinedTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            placeholder = { Text("What's on your mind? (Vanishes in 24h)") },
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaLightTealPrimary,
                                focusedTextColor = WaLightTextPrimary,
                                unfocusedTextColor = WaLightTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("status_text_input")
                        )

                        Text("Select Background Color", color = WaLightTextSecondary, fontSize = 12.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            colors.forEach { hex ->
                                val col = Color(android.graphics.Color.parseColor(hex))
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .clickable { selectedColor = hex }
                                ) {
                                    if (selectedColor == hex) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                                .align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Image Status
                        if (pickedImageBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = pickedImageBitmap!!.asImageBitmap(),
                                    contentDescription = "Selected Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Button(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                ) {
                                    Text("Change", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = WaLightTealPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose Photo from Gallery", color = WaLightTealPrimary)
                            }
                        }

                        OutlinedTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            placeholder = { Text("Add a caption to your photo...") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaLightTealPrimary,
                                focusedTextColor = WaLightTextPrimary,
                                unfocusedTextColor = WaLightTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    2 -> {
                        // Video Status
                        if (videoName.isNotBlank()) {
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = WaLightTealPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(videoName, maxLines = 1, fontSize = 13.sp, color = WaLightTextPrimary, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { videoName = ""; mediaPayload = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray)
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = WaLightTealPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Video from Device", color = WaLightTealPrimary)
                            }
                        }

                        OutlinedTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            placeholder = { Text("Add video title/caption...") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaLightTealPrimary,
                                focusedTextColor = WaLightTextPrimary,
                                unfocusedTextColor = WaLightTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    3 -> {
                        // Link Status
                        OutlinedTextField(
                            value = linkUrl,
                            onValueChange = { linkUrl = it },
                            placeholder = { Text("https://example.com or any link...") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = WaLightTealPrimary) },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaLightTealPrimary,
                                focusedTextColor = WaLightTextPrimary,
                                unfocusedTextColor = WaLightTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("status_link_input")
                        )

                        OutlinedTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            placeholder = { Text("Add title or description for link...") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaLightTealPrimary,
                                focusedTextColor = WaLightTextPrimary,
                                unfocusedTextColor = WaLightTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isReady = when (selectedTab) {
                0 -> caption.isNotBlank()
                1 -> mediaPayload.isNotBlank() || caption.isNotBlank()
                2 -> mediaPayload.isNotBlank() || caption.isNotBlank()
                3 -> linkUrl.isNotBlank()
                else -> false
            }

            Button(
                onClick = {
                    val storyType = when (selectedTab) {
                        1 -> "IMAGE"
                        2 -> "VIDEO"
                        3 -> "LINK"
                        else -> "TEXT"
                    }
                    onPost(caption, selectedColor, storyType, mediaPayload, linkUrl)
                },
                enabled = isReady,
                colors = ButtonDefaults.buttonColors(containerColor = WaLightTealPrimary),
                modifier = Modifier.testTag("post_status_confirm_button")
            ) {
                Text("Post Status", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = WaLightTextSecondary)
            }
        }
    )
}
