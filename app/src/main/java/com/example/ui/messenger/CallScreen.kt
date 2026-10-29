package com.example.ui.messenger

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.repository.CallStreamManager
import com.example.data.repository.FirebaseManager
import com.example.data.repository.PeerCallEngine
import com.example.ui.theme.*

@Composable
fun CallScreen(
    callInfo: ActiveCallInfo,
    myUserId: String = "",
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit
) {
    val context = LocalContext.current
    val contact = callInfo.contact
    val isVideo = callInfo.isVideo
    val firestore = remember { FirebaseManager.getFirestore(context) }

    // Remote peer video and live stream status from CallStreamManager
    val remoteVideoBitmap by CallStreamManager.remoteVideoBitmap.collectAsState()
    val cloudStatus by CallStreamManager.callStatus.collectAsState()

    // Permissions check & launcher - Proactively request both Audio and Camera
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
        hasCameraPermission = results[Manifest.permission.CAMERA] ?: hasCameraPermission
    }

    LaunchedEffect(Unit) {
        val permissionsToAsk = mutableListOf<String>()
        if (!hasAudioPermission) permissionsToAsk.add(Manifest.permission.RECORD_AUDIO)
        if (!hasCameraPermission) permissionsToAsk.add(Manifest.permission.CAMERA)
        if (permissionsToAsk.isNotEmpty()) {
            permissionLauncher.launch(permissionsToAsk.toTypedArray())
        }
    }

    // Android Hardware Audio and Unified Zero-Lag Live Stream (P2P UDP + Non-blocking Cloud Relay)
    LaunchedEffect(callInfo.state, callInfo.remoteIp, hasAudioPermission) {
        if (callInfo.state == CallState.CONNECTED && hasAudioPermission) {
            val localAudioPort = if (callInfo.isIncoming) PeerCallEngine.DEFAULT_RECEIVER_AUDIO_PORT else PeerCallEngine.DEFAULT_CALLER_AUDIO_PORT
            val localVideoPort = if (callInfo.isIncoming) PeerCallEngine.DEFAULT_RECEIVER_VIDEO_PORT else PeerCallEngine.DEFAULT_CALLER_VIDEO_PORT

            CallStreamManager.startCallStream(
                context = context,
                firestore = firestore,
                callId = callInfo.callId,
                myUserId = myUserId,
                isCaller = !callInfo.isIncoming,
                isVideo = isVideo,
                remoteIp = callInfo.remoteIp,
                remoteAudioPort = callInfo.remoteAudioPort,
                remoteVideoPort = callInfo.remoteVideoPort,
                localAudioPort = localAudioPort,
                localVideoPort = localVideoPort
            )
        }
    }

    LaunchedEffect(callInfo.isMuted) {
        CallStreamManager.setMute(callInfo.isMuted)
    }

    LaunchedEffect(callInfo.isSpeakerOn, isVideo) {
        CallStreamManager.setSpeaker(context, callInfo.isSpeakerOn || isVideo)
    }

    DisposableEffect(Unit) {
        onDispose {
            CallStreamManager.stopCallStream(context)
        }
    }

    // Animated pulsing scale for voice calling rings
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val minutes = callInfo.durationSeconds / 60
    val seconds = callInfo.durationSeconds % 60
    val durationText = String.format("%02d:%02d", minutes, seconds)

    val statusText = when (callInfo.state) {
        CallState.CALLING -> "Ringing Peer..."
        CallState.CONNECTED -> "Live Connected • $durationText"
        CallState.ENDED -> "Call Ended"
        else -> cloudStatus.ifBlank { "Connecting..." }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isVideo) listOf(Color(0xFF0F172A), Color(0xFF020617))
                    else listOf(WaTeal, Color(0xFF0B141B))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (isVideo) {
            // Video Call View
            Box(modifier = Modifier.fillMaxSize()) {
                // If remote video bitmap is received from peer, display live video full screen!
                if (remoteVideoBitmap != null) {
                    Image(
                        bitmap = remoteVideoBitmap!!.asImageBitmap(),
                        contentDescription = "Peer Live Video Stream",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Waiting for video or audio-only fallback
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = WaEmeraldDark,
                                modifier = Modifier.size(110.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = contact.name.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = contact.name,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = WaEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Live P2P Video • $statusText",
                                    color = WaEmerald,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // PiP Local Camera Preview (Uses actual CameraX front camera preview & streams frames)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(width = 110.dp, height = 150.dp)
                        .border(1.5.dp, WaEmerald, RoundedCornerShape(12.dp))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (callInfo.isVideoEnabled && hasCameraPermission) {
                            CameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
                                onFrameCaptured = { bmp ->
                                    if (callInfo.state == CallState.CONNECTED) {
                                        CallStreamManager.sendLocalVideoFrame(
                                            firestore = firestore,
                                            callId = callInfo.callId,
                                            isCaller = !callInfo.isIncoming,
                                            bitmap = bmp,
                                            remoteIp = callInfo.remoteIp,
                                            remoteVideoPort = callInfo.remoteVideoPort
                                        )
                                    }
                                }
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.VideocamOff,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (!hasCameraPermission) "No Permission" else "Camera Off",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Voice Call View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = WaEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WebRTC Encrypted Voice Call",
                            color = WaEmerald,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = contact.name,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = contact.id,
                        color = WaTicksBlue,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = statusText,
                        color = WaTextSecondary,
                        fontSize = 16.sp
                    )
                }

                // Center Pulsing Avatar & Waveform
                Box(contentAlignment = Alignment.Center) {
                    // Pulsing outer halo
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(WaEmerald.copy(alpha = 0.15f))
                    )

                    // Middle ring
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(WaEmeraldDark.copy(alpha = 0.35f))
                    )

                    // Core Avatar
                    Surface(
                        shape = CircleShape,
                        color = WaEmeraldDark,
                        modifier = Modifier.size(120.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = contact.name.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Audio wave animation bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(30.dp)
                ) {
                    val bars = listOf(10, 24, 14, 28, 18, 12, 26, 16, 22, 12)
                    bars.forEach { h ->
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(h.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(WaEmerald)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(1.dp))
            }
        }

        // Bottom Call Action Controls
        Surface(
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speaker Button
                IconButton(
                    onClick = onToggleSpeaker,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (callInfo.isSpeakerOn) WaEmerald else Color.White.copy(alpha = 0.15f))
                        .testTag("call_speaker_button")
                ) {
                    Icon(
                        imageVector = if (callInfo.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        contentDescription = "Speaker",
                        tint = if (callInfo.isSpeakerOn) Color.Black else Color.White
                    )
                }

                if (isVideo) {
                    // Video camera toggle
                    IconButton(
                        onClick = onToggleCamera,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (callInfo.isVideoEnabled) Color.White.copy(alpha = 0.15f) else Color.Red)
                            .testTag("call_camera_toggle")
                    ) {
                        Icon(
                            imageVector = if (callInfo.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Toggle Video",
                            tint = Color.White
                        )
                    }
                }

                // Mute Mic Button
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (callInfo.isMuted) Color.Red else Color.White.copy(alpha = 0.15f))
                        .testTag("call_mute_button")
                ) {
                    Icon(
                        imageVector = if (callInfo.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = Color.White
                    )
                }

                // End Call Red Button
                FloatingActionButton(
                    onClick = onEndCall,
                    containerColor = WaCallEnd,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(62.dp)
                        .testTag("call_end_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
    onFrameCaptured: ((Bitmap) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = if (onFrameCaptured != null) {
                        androidx.camera.core.ImageAnalysis.Builder()
                            .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build().also { analysis ->
                                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                    try {
                                        val bitmap = imageProxy.toBitmap()
                                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                        val rotatedBitmap = if (rotationDegrees != 0) {
                                            val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                        } else {
                                            bitmap
                                        }
                                        onFrameCaptured(rotatedBitmap)
                                    } catch (e: Exception) {
                                        // Ignore frame drops
                                    } finally {
                                        imageProxy.close()
                                    }
                                }
                            }
                    } else null

                    cameraProvider.unbindAll()
                    if (imageAnalysis != null) {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } else {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    }
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Camera bind failed: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}
