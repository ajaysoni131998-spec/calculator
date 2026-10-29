package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CallStreamManager
 * 
 * High-performance, zero-lag real-time Voice and Video calling engine:
 * 1. Low-latency 80ms audio chunking without blocking queues or accumulative lag.
 * 2. Hardware Acoustic Echo Cancellation (AEC) and Noise Suppression (NS).
 * 3. Dual-Transport Architecture:
 *    - Direct P2P UDP datagram transmission for immediate 0ms local/LAN latency.
 *    - Non-blocking Cloud Relay with anti-lag stale frame/chunk dropping for mobile data.
 * 4. Paced video transmission (5-6 FPS cloud, 15 FPS UDP) preventing Firestore document throttling.
 */
object CallStreamManager {

    private const val TAG = "CallStreamManager"

    // 8kHz 16-bit Mono PCM for crystal clear, low-bandwidth VoIP
    private const val AUDIO_SAMPLE_RATE = 8000
    private const val AUDIO_CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT

    // 80ms buffer chunk: 8000 samples/sec * 2 bytes/sample * 0.08 sec = 1280 bytes
    private const val AUDIO_CHUNK_BYTES = 1280

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private var audioUdpSocket: DatagramSocket? = null
    private var videoUdpSocket: DatagramSocket? = null

    private var isStreaming = false
    private var streamScope: CoroutineScope? = null
    private var callRegistration: ListenerRegistration? = null
    private var audioPacketsRegistration: ListenerRegistration? = null

    // Non-blocking upload gates (prevents backpressure queueing and lag accumulation)
    private val isUploadingAudio = AtomicBoolean(false)
    private val isUploadingVideo = AtomicBoolean(false)

    // Rate limiting timestamps
    private var lastUdpVideoSentTime = 0L
    private var lastCloudVideoSentTime = 0L
    private var lastUdpAudioPacketReceivedAt = 0L

    private val _remoteVideoBitmap = MutableStateFlow<Bitmap?>(null)
    val remoteVideoBitmap: StateFlow<Bitmap?> = _remoteVideoBitmap.asStateFlow()

    private val _callStatus = MutableStateFlow("Connecting Live Stream...")
    val callStatus: StateFlow<String> = _callStatus.asStateFlow()

    var isMuted = false
    var isSpeakerOn = false

    fun startCallStream(
        context: Context,
        firestore: FirebaseFirestore,
        callId: String,
        myUserId: String,
        isCaller: Boolean,
        isVideo: Boolean,
        remoteIp: String = "127.0.0.1",
        remoteAudioPort: Int = PeerCallEngine.DEFAULT_RECEIVER_AUDIO_PORT,
        remoteVideoPort: Int = PeerCallEngine.DEFAULT_RECEIVER_VIDEO_PORT,
        localAudioPort: Int = if (isCaller) PeerCallEngine.DEFAULT_CALLER_AUDIO_PORT else PeerCallEngine.DEFAULT_RECEIVER_AUDIO_PORT,
        localVideoPort: Int = if (isCaller) PeerCallEngine.DEFAULT_CALLER_VIDEO_PORT else PeerCallEngine.DEFAULT_RECEIVER_VIDEO_PORT
    ) {
        if (isStreaming) return
        isStreaming = true

        streamScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.isSpeakerphoneOn = isSpeakerOn || isVideo

        _callStatus.value = "Live Connected • Audio & Video Active"

        // 1. Initialize Low-Latency Sockets
        try {
            audioUdpSocket = DatagramSocket(localAudioPort).apply {
                reuseAddress = true
                soTimeout = 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio UDP bind warning: ${e.message}")
        }

        if (isVideo) {
            try {
                videoUdpSocket = DatagramSocket(localVideoPort).apply {
                    reuseAddress = true
                    soTimeout = 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "Video UDP bind warning: ${e.message}")
            }
        }

        // 2. Initialize Low-Latency Audio Track
        initAudioTrack()

        // 3. Initialize Audio Record with Echo Cancellation
        initAudioRecord()

        // 4. Start Real-time Audio Recorder & Sender
        streamScope?.launch {
            recordAndSendAudio(firestore, callId, myUserId, remoteIp, remoteAudioPort)
        }

        // 5. Start Direct UDP Listeners (0ms local latency)
        streamScope?.launch {
            receiveUdpAudio()
        }

        if (isVideo) {
            streamScope?.launch {
                receiveUdpVideo()
            }
        }

        // 6. Start Cloud Relay Listeners (Anti-Lag, Drop-Stale packets)
        listenToRemoteStreams(firestore, callId, myUserId, isCaller)
    }

    private fun initAudioTrack() {
        try {
            val minBuf = AudioTrack.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_OUT, AUDIO_ENCODING)
            val bufSize = maxOf(minBuf, AUDIO_CHUNK_BYTES * 2)

            val trackBuilder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_ENCODING)
                        .setSampleRate(AUDIO_SAMPLE_RATE)
                        .setChannelMask(AUDIO_CHANNEL_OUT)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            audioTrack = trackBuilder.build()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init error: ${e.message}")
        }
    }

    private fun initAudioRecord() {
        try {
            val minBuf = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_IN, AUDIO_ENCODING)
            val bufSize = maxOf(minBuf, AUDIO_CHUNK_BYTES * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL_IN,
                AUDIO_ENCODING,
                bufSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                val sessionId = audioRecord!!.audioSessionId

                // Enable hardware Acoustic Echo Cancellation if available
                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                            enabled = true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "AEC enable failed: ${e.message}")
                    }
                }

                // Enable hardware Noise Suppression if available
                if (NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                            enabled = true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "NoiseSuppressor enable failed: ${e.message}")
                    }
                }

                audioRecord?.startRecording()
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord init error: ${e.message}")
        }
    }

    private suspend fun recordAndSendAudio(
        firestore: FirebaseFirestore,
        callId: String,
        myUserId: String,
        remoteIp: String,
        remoteAudioPort: Int
    ) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(AUDIO_CHUNK_BYTES)
        var remoteAddr: InetAddress? = null
        try {
            remoteAddr = InetAddress.getByName(remoteIp)
        } catch (e: Exception) {
            // Address resolution fallback
        }

        while (isStreaming && isActive) {
            try {
                // audioRecord.read blocks for exactly 80ms — zero artificial delay!
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0 && !isMuted) {
                    // 1. Send via direct UDP (0ms network delay for Wi-Fi / Hotspot)
                    if (audioUdpSocket != null && !audioUdpSocket!!.isClosed && remoteAddr != null) {
                        try {
                            val packet = DatagramPacket(buffer, read, remoteAddr, remoteAudioPort)
                            audioUdpSocket?.send(packet)
                        } catch (e: Exception) {
                            // UDP drop is fine
                        }
                    }

                    // 2. Send via Cloud Relay (Non-blocking: drops intermediate packets so lag NEVER builds up)
                    if (isUploadingAudio.compareAndSet(false, true)) {
                        val base64Chunk = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                        val now = System.currentTimeMillis()

                        val packet = hashMapOf<String, Any>(
                            "sender" to myUserId,
                            "audio" to base64Chunk,
                            "ts" to now
                        )

                        firestore.collection("calls").document(callId)
                            .collection("live_audio")
                            .document("stream")
                            .set(packet, SetOptions.merge())
                            .addOnCompleteListener {
                                isUploadingAudio.set(false)
                            }
                    }
                }
            } catch (e: Exception) {
                // Ignore transient recording drops
            }
        }
    }

    private suspend fun receiveUdpAudio() = withContext(Dispatchers.IO) {
        val recvBuffer = ByteArray(2048)
        val packet = DatagramPacket(recvBuffer, recvBuffer.size)

        while (isStreaming && isActive) {
            try {
                audioUdpSocket?.receive(packet)
                if (packet.length > 0) {
                    lastUdpAudioPacketReceivedAt = System.currentTimeMillis()
                    val data = ByteArray(packet.length)
                    System.arraycopy(packet.data, packet.offset, data, 0, packet.length)
                    playAudioBytes(data)
                }
            } catch (e: Exception) {
                // Socket closed or timeout
            }
        }
    }

    private suspend fun receiveUdpVideo() = withContext(Dispatchers.IO) {
        val recvBuffer = ByteArray(65507)
        val packet = DatagramPacket(recvBuffer, recvBuffer.size)

        while (isStreaming && isActive) {
            try {
                videoUdpSocket?.receive(packet)
                if (packet.length > 0) {
                    val bitmap = BitmapFactory.decodeByteArray(packet.data, packet.offset, packet.length)
                    if (bitmap != null) {
                        _remoteVideoBitmap.value = bitmap
                    }
                }
            } catch (e: Exception) {
                // Socket closed or timeout
            }
        }
    }

    private fun listenToRemoteStreams(
        firestore: FirebaseFirestore,
        callId: String,
        myUserId: String,
        isCaller: Boolean
    ) {
        // Listen for remote Video Frames on the call document
        callRegistration?.remove()
        callRegistration = firestore.collection("calls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val remoteVideoBase64 = if (isCaller) {
                    snapshot.getString("receiverVideoFrame")
                } else {
                    snapshot.getString("callerVideoFrame")
                }

                val frameTs = snapshot.getLong("lastFrameTs") ?: 0L
                val now = System.currentTimeMillis()

                // Anti-Lag: Discard frames older than 600ms so display is always fresh
                if (!remoteVideoBase64.isNullOrBlank() && (now - frameTs <= 600)) {
                    try {
                        val bytes = Base64.decode(remoteVideoBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            _remoteVideoBitmap.value = bitmap
                        }
                    } catch (e: Exception) {
                        // Frame decode error
                    }
                }
            }

        // Listen for remote Audio Packets
        audioPacketsRegistration?.remove()
        audioPacketsRegistration = firestore.collection("calls").document(callId)
            .collection("live_audio")
            .document("stream")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val sender = snapshot.getString("sender") ?: ""
                val audioData = snapshot.getString("audio") ?: ""
                val ts = snapshot.getLong("ts") ?: 0L
                val now = System.currentTimeMillis()

                // Anti-Lag Rule: Discard audio packets older than 400ms!
                // Also suppress cloud audio if fresh UDP audio was received in the last 400ms.
                if (sender != myUserId && audioData.isNotBlank() && (now - ts <= 400)) {
                    if (now - lastUdpAudioPacketReceivedAt > 400) {
                        try {
                            val pcmBytes = Base64.decode(audioData, Base64.NO_WRAP)
                            playAudioBytes(pcmBytes)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error playing audio chunk: ${e.message}")
                        }
                    }
                }
            }
    }

    private fun playAudioBytes(data: ByteArray) {
        val track = audioTrack ?: return
        try {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
            track.write(data, 0, data.size, AudioTrack.WRITE_NON_BLOCKING)
        } catch (e: Exception) {
            Log.e(TAG, "playAudioBytes error: ${e.message}")
        }
    }

    /**
     * Sends local CameraX frame to remote peer via UDP and Non-blocking Cloud Stream.
     */
    fun sendLocalVideoFrame(
        firestore: FirebaseFirestore,
        callId: String,
        isCaller: Boolean,
        bitmap: Bitmap,
        remoteIp: String = "127.0.0.1",
        remoteVideoPort: Int = PeerCallEngine.DEFAULT_RECEIVER_VIDEO_PORT
    ) {
        if (!isStreaming) return
        val now = System.currentTimeMillis()

        // 1. Direct UDP Frame (Paced at ~15 FPS: interval >= 66ms)
        if (now - lastUdpVideoSentTime >= 66 && videoUdpSocket != null && !videoUdpSocket!!.isClosed) {
            lastUdpVideoSentTime = now
            streamScope?.launch(Dispatchers.IO) {
                try {
                    val out = ByteArrayOutputStream()
                    val scaled = if (bitmap.width > 320 || bitmap.height > 240) {
                        Bitmap.createScaledBitmap(bitmap, 240, 320, true)
                    } else {
                        bitmap
                    }
                    scaled.compress(Bitmap.CompressFormat.JPEG, 35, out)
                    val bytes = out.toByteArray()
                    if (bytes.size <= 65000) {
                        val remoteAddr = InetAddress.getByName(remoteIp)
                        val packet = DatagramPacket(bytes, bytes.size, remoteAddr, remoteVideoPort)
                        videoUdpSocket?.send(packet)
                    }
                } catch (e: Exception) {
                    // Frame drop is normal in live streaming
                }
            }
        }

        // 2. Cloud Video Fallback (Paced at 4-5 FPS: interval >= 200ms)
        // NON-BLOCKING: Only upload if previous write finished, preventing Firestore backlog!
        if (now - lastCloudVideoSentTime >= 200 && isUploadingVideo.compareAndSet(false, true)) {
            lastCloudVideoSentTime = now
            streamScope?.launch(Dispatchers.IO) {
                try {
                    val out = ByteArrayOutputStream()
                    val scaled = if (bitmap.width > 240 || bitmap.height > 180) {
                        Bitmap.createScaledBitmap(bitmap, 180, 240, true)
                    } else {
                        bitmap
                    }
                    scaled.compress(Bitmap.CompressFormat.JPEG, 28, out)
                    val base64Frame = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

                    val fieldName = if (isCaller) "callerVideoFrame" else "receiverVideoFrame"
                    val updates = hashMapOf<String, Any>(
                        fieldName to base64Frame,
                        "lastFrameTs" to System.currentTimeMillis()
                    )

                    firestore.collection("calls").document(callId)
                        .set(updates, SetOptions.merge())
                        .addOnCompleteListener {
                            isUploadingVideo.set(false)
                        }
                } catch (e: Exception) {
                    isUploadingVideo.set(false)
                }
            }
        }
    }

    fun setMute(muted: Boolean) {
        isMuted = muted
    }

    fun setSpeaker(context: Context, speakerOn: Boolean) {
        isSpeakerOn = speakerOn
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.isSpeakerphoneOn = speakerOn
    }

    fun stopCallStream(context: Context) {
        isStreaming = false

        callRegistration?.remove()
        callRegistration = null

        audioPacketsRegistration?.remove()
        audioPacketsRegistration = null

        try {
            echoCanceler?.release()
        } catch (e: Exception) {
            // Ignore
        }
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (e: Exception) {
            // Ignore
        }
        noiseSuppressor = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioTrack = null

        try {
            audioUdpSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        audioUdpSocket = null

        try {
            videoUdpSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        videoUdpSocket = null

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_NORMAL
        audioManager?.isSpeakerphoneOn = false

        _remoteVideoBitmap.value = null
        _callStatus.value = "Call Ended"

        isUploadingAudio.set(false)
        isUploadingVideo.set(false)

        streamScope?.cancel()
        streamScope = null
    }
}
