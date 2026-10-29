package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * ============================================================================
 * FIREBASE CLOUD FIRESTORE REAL-TIME CHAT MANAGER
 * ============================================================================
 * 
 * This class handles:
 * 1. Firebase SDK Initialization (Default or Custom Config Keys).
 * 2. Sending messages directly to Firestore "messages" collection:
 *    - senderId   : String (e.g., "Device_A", "Phone_1", "User1")
 *    - receiverId : String (e.g., "Device_B", "Phone_2", "User2")
 *    - text       : String (Message body)
 *    - timestamp  : FieldValue.serverTimestamp()
 * 3. Listening to real-time message events via onSnapshot() (addSnapshotListener)
 *    so both Device A and Device B receive updates instantly without page refresh.
 * 4. Identifying whether a message is incoming (LEFT) or outgoing (RIGHT).
 */
data class FirestoreChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object FirebaseManager {

    private const val TAG = "FirebaseManager"
    const val COLLECTION_MESSAGES = "messages"

    @Volatile
    private var firestoreInstance: FirebaseFirestore? = null

    /**
     * Requirement 1: Firebase SDK Initialize Karne Ka Tareeqa
     * 
     * Agar aapko custom API keys / project credentials manual code me daalni hon,
     * to aap FirebaseOptions.Builder use karke manually initialize kar sakte hain.
     * Warna Google-Services.json automatic configuration use hoti hai.
     */
    fun initializeFirebaseCustom(
        context: Context,
        apiKey: String = "AIzaSyCN3Y8z8ehh1J0zm7TDevxwsYIx8QjCYiQ",
        applicationId: String = "1:397661697577:android:1710fab8ba3e52af070e38",
        projectId: String = "gen-lang-client-0017938541"
    ) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(applicationId)
                .setProjectId(projectId)
                .build()
            FirebaseApp.initializeApp(context, options)
            Log.d(TAG, "Firebase initialized with custom credentials for project: $projectId")
        }
    }

    /**
     * Provides a singleton FirebaseFirestore instance.
     * Uses the provisioned database ID or falls back to default.
     */
    fun getFirestore(context: Context): FirebaseFirestore {
        return firestoreInstance ?: synchronized(this) {
            firestoreInstance ?: run {
                val dbId = try {
                    context.getString(R.string.firestore_database_id)
                } catch (e: Exception) {
                    ""
                }

                val instance = if (dbId.isNotBlank()) {
                    try {
                        FirebaseFirestore.getInstance(dbId)
                    } catch (e: Exception) {
                        Log.w(TAG, "Named database failed, using default: ${e.message}")
                        FirebaseFirestore.getInstance()
                    }
                } else {
                    FirebaseFirestore.getInstance()
                }
                firestoreInstance = instance
                instance
            }
        }
    }

    /**
     * Requirement 2 & 3:
     * Firestore me "messages" collection me document add karein:
     * - senderId
     * - receiverId
     * - text
     * - timestamp (FieldValue.serverTimestamp())
     */
    fun sendMessage(
        firestore: FirebaseFirestore,
        senderId: String,
        receiverId: String,
        text: String,
        onSuccess: (documentId: String) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val messageMap = hashMapOf<String, Any>(
            "senderId" to senderId.trim(),
            "receiverId" to receiverId.trim(),
            "text" to text.trim(),
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection(COLLECTION_MESSAGES)
            .add(messageMap)
            .addOnSuccessListener { documentRef ->
                Log.d(TAG, "Message sent to Firestore! Doc ID: ${documentRef.id}")
                onSuccess(documentRef.id)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send message: ${e.message}", e)
                onFailure(e)
            }
    }

    /**
     * Requirement 4:
     * Real-time onSnapshot() (addSnapshotListener) listener implement karein
     * taaki naya message aate hi bina screen refresh kiye turant UI par display ho jaye.
     */
    fun attachRealtimeMessageListener(
        firestore: FirebaseFirestore,
        myUserId: String,
        conversationPeerId: String,
        onMessagesUpdated: (List<FirestoreChatMessage>) -> Unit
    ): ListenerRegistration {
        val myCleanId = myUserId.trim()
        val peerCleanId = conversationPeerId.trim()

        Log.d(TAG, "Attaching Firestore realtime listener between '$myCleanId' and '$peerCleanId'")

        return firestore.collection(COLLECTION_MESSAGES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore snapshot listener error: ${error.message}", error)
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val chatList = mutableListOf<FirestoreChatMessage>()
                for (doc in snapshot.documents) {
                    val sId = doc.getString("senderId") ?: ""
                    val rId = doc.getString("receiverId") ?: ""
                    val text = doc.getString("text") ?: ""
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time
                        ?: System.currentTimeMillis()

                    // Check if message belongs to this conversation
                    // Matches if (sender is Me and receiver is Peer) OR (sender is Peer and receiver is Me)
                    // Also supports case-insensitive match
                    val isOutgoing = sId.equals(myCleanId, ignoreCase = true) &&
                            rId.equals(peerCleanId, ignoreCase = true)
                    val isIncoming = sId.equals(peerCleanId, ignoreCase = true) &&
                            rId.equals(myCleanId, ignoreCase = true)

                    // Also support general broadcast or unassigned
                    if (isOutgoing || isIncoming || peerCleanId.isEmpty()) {
                        chatList.add(
                            FirestoreChatMessage(
                                id = doc.id,
                                senderId = sId,
                                receiverId = rId,
                                text = text,
                                timestamp = timestamp
                            )
                        )
                    }
                }

                // Sort by timestamp ascending
                chatList.sortBy { it.timestamp }
                onMessagesUpdated(chatList)
            }
    }

    const val COLLECTION_CALLS = "calls"

    /**
     * Requirement: Cross-device Real-Time Voice/Video Call Signaling
     * Device A initiates a call to Device B:
     * - Status: "RINGING"
     */
    /**
     * Requirement: WebRTC Peer-to-Peer Calling & Signaling
     * Initiate a Voice or Video Call in Firestore with WebRTC SDP support
     */
    fun initiateCall(
        firestore: FirebaseFirestore,
        callId: String,
        callerId: String,
        callerName: String,
        receiverId: String,
        callType: String, // "VOICE" or "VIDEO"
        sdpOffer: String = "",
        callerIp: String = PeerCallEngine.getLocalIpAddress(),
        callerAudioPort: Int = PeerCallEngine.DEFAULT_CALLER_AUDIO_PORT,
        callerVideoPort: Int = PeerCallEngine.DEFAULT_CALLER_VIDEO_PORT,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val callMap = hashMapOf<String, Any>(
            "callId" to callId,
            "callerId" to callerId.trim(),
            "callerName" to callerName.trim(),
            "receiverId" to receiverId.trim(),
            "callType" to callType,
            "status" to "RINGING",
            "sdpOffer" to sdpOffer,
            "sdpAnswer" to "",
            "callerIp" to callerIp,
            "callerAudioPort" to callerAudioPort,
            "callerVideoPort" to callerVideoPort,
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection(COLLECTION_CALLS).document(callId).set(callMap)
            .addOnSuccessListener {
                Log.d(TAG, "Call initiated in Firestore: $callId, Type: $callType, IP: $callerIp")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to initiate call: ${e.message}", e)
                onFailure(e)
            }
    }

    /**
     * Update status of call: "CONNECTED", "REJECTED", "ENDED"
     */
    fun updateCallStatus(
        firestore: FirebaseFirestore,
        callId: String,
        status: String,
        sdpAnswer: String = "",
        receiverIp: String = PeerCallEngine.getLocalIpAddress(),
        receiverAudioPort: Int = PeerCallEngine.DEFAULT_RECEIVER_AUDIO_PORT,
        receiverVideoPort: Int = PeerCallEngine.DEFAULT_RECEIVER_VIDEO_PORT,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (callId.isBlank()) return
        val updates = mutableMapOf<String, Any>("status" to status)
        if (sdpAnswer.isNotBlank()) {
            updates["sdpAnswer"] = sdpAnswer
        }
        if (status == "CONNECTED") {
            updates["receiverIp"] = receiverIp
            updates["receiverAudioPort"] = receiverAudioPort
            updates["receiverVideoPort"] = receiverVideoPort
        }
        firestore.collection(COLLECTION_CALLS).document(callId)
            .update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "Call status updated to $status for $callId")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update call status: ${e.message}", e)
                onFailure(e)
            }
    }

    /**
     * WebRTC ICE candidate exchange
     */
    fun sendIceCandidate(
        firestore: FirebaseFirestore,
        callId: String,
        isCaller: Boolean,
        candidateJson: String
    ) {
        if (callId.isBlank()) return
        val collectionName = if (isCaller) "caller_candidates" else "callee_candidates"
        val data = hashMapOf(
            "candidate" to candidateJson,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection(COLLECTION_CALLS).document(callId)
            .collection(collectionName).add(data)
    }

    /**
     * Listen for incoming calls for any of the device's user IDs.
     * Normalized matching handles:
     * - "#user" matching "user"
     * - "user@gmail.com" matching "user" or "user@gmail.com"
     * - Case-insensitive comparisons
     */
    fun listenForIncomingCalls(
        firestore: FirebaseFirestore,
        myIdentifiers: List<String>,
        onIncomingCall: (FirestoreCallSignal?) -> Unit
    ): ListenerRegistration {
        val cleanIds = myIdentifiers
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .flatMap { id ->
                listOf(
                    id,
                    id.removePrefix("#"),
                    id.substringBefore("@")
                )
            }.distinct()

        return firestore.collection(COLLECTION_CALLS)
            .whereEqualTo("status", "RINGING")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Incoming call listener error: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) {
                    onIncomingCall(null)
                    return@addSnapshotListener
                }

                var foundCall: FirestoreCallSignal? = null
                for (doc in snapshot.documents) {
                    val rId = doc.getString("receiverId")?.trim()?.lowercase() ?: ""
                    val cId = doc.getString("callerId")?.trim()?.lowercase() ?: ""

                    val rClean = rId.removePrefix("#")
                    val rUser = rId.substringBefore("@")
                    val cClean = cId.removePrefix("#")
                    val cUser = cId.substringBefore("@")

                    val isForMe = cleanIds.any { id ->
                        id == rId || id == rClean || id == rUser
                    }
                    val isByMe = cleanIds.any { id ->
                        id == cId || id == cClean || id == cUser
                    }

                    // Don't ring our own initiated calls
                    if (isForMe && !isByMe) {
                        val callId = doc.getString("callId") ?: doc.id
                        val callerId = doc.getString("callerId") ?: ""
                        val callerName = doc.getString("callerName") ?: callerId
                        val callType = doc.getString("callType") ?: "VOICE"
                        val status = doc.getString("status") ?: "RINGING"
                        val sdpOffer = doc.getString("sdpOffer") ?: ""
                        val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()

                        // Only consider calls made in the last 60 seconds
                        if (System.currentTimeMillis() - timestamp < 60000) {
                            foundCall = FirestoreCallSignal(
                                callId = callId,
                                callerId = callerId,
                                callerName = callerName,
                                receiverId = doc.getString("receiverId") ?: "",
                                callType = callType,
                                status = status,
                                sdpOffer = sdpOffer,
                                callerIp = doc.getString("callerIp") ?: "127.0.0.1",
                                callerAudioPort = doc.getLong("callerAudioPort")?.toInt() ?: PeerCallEngine.DEFAULT_CALLER_AUDIO_PORT,
                                callerVideoPort = doc.getLong("callerVideoPort")?.toInt() ?: PeerCallEngine.DEFAULT_CALLER_VIDEO_PORT,
                                timestamp = timestamp
                            )
                            break
                        }
                    }
                }
                onIncomingCall(foundCall)
            }
    }

    /**
     * Listen to state updates for an active call (e.g. peer connected, rejected, or ended)
     */
    fun listenToCall(
        firestore: FirebaseFirestore,
        callId: String,
        onStatusChanged: (status: String, sdpAnswer: String, remoteIp: String, remoteAudioPort: Int, remoteVideoPort: Int) -> Unit
    ): ListenerRegistration {
        return firestore.collection(COLLECTION_CALLS).document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val status = snapshot.getString("status") ?: "RINGING"
                val sdpAnswer = snapshot.getString("sdpAnswer") ?: ""
                val remoteIp = snapshot.getString("receiverIp") 
                    ?: snapshot.getString("callerIp") 
                    ?: "127.0.0.1"
                val remoteAudioPort = snapshot.getLong("receiverAudioPort")?.toInt()
                    ?: snapshot.getLong("callerAudioPort")?.toInt()
                    ?: PeerCallEngine.DEFAULT_RECEIVER_AUDIO_PORT
                val remoteVideoPort = snapshot.getLong("receiverVideoPort")?.toInt()
                    ?: snapshot.getLong("callerVideoPort")?.toInt()
                    ?: PeerCallEngine.DEFAULT_RECEIVER_VIDEO_PORT
                onStatusChanged(status, sdpAnswer, remoteIp, remoteAudioPort, remoteVideoPort)
            }
    }

    /**
     * Delete/Clear all messages for a conversation in Firestore
     */
    fun deleteConversationMessages(
        firestore: FirebaseFirestore,
        myUserId: String,
        peerId: String,
        onComplete: () -> Unit = {}
    ) {
        val myClean = myUserId.trim().lowercase()
        val peerClean = peerId.trim().lowercase()

        firestore.collection(COLLECTION_MESSAGES)
            .get()
            .addOnSuccessListener { snapshots ->
                val batch = firestore.batch()
                for (doc in snapshots.documents) {
                    val sId = doc.getString("senderId")?.trim()?.lowercase() ?: ""
                    val rId = doc.getString("receiverId")?.trim()?.lowercase() ?: ""
                    val isMatch = (sId == myClean && rId == peerClean) || (sId == peerClean && rId == myClean)
                    if (isMatch) {
                        batch.delete(doc.reference)
                    }
                }
                batch.commit().addOnCompleteListener { onComplete() }
            }
            .addOnFailureListener {
                onComplete()
            }
    }
}

data class FirestoreCallSignal(
    val callId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val receiverId: String = "",
    val callType: String = "VOICE", // "VOICE" or "VIDEO"
    val status: String = "RINGING",  // "RINGING", "CONNECTED", "REJECTED", "ENDED"
    val sdpOffer: String = "",
    val sdpAnswer: String = "",
    val callerIp: String = "127.0.0.1",
    val callerAudioPort: Int = PeerCallEngine.DEFAULT_CALLER_AUDIO_PORT,
    val callerVideoPort: Int = PeerCallEngine.DEFAULT_CALLER_VIDEO_PORT,
    val receiverIp: String = "127.0.0.1",
    val receiverAudioPort: Int = PeerCallEngine.DEFAULT_RECEIVER_AUDIO_PORT,
    val receiverVideoPort: Int = PeerCallEngine.DEFAULT_RECEIVER_VIDEO_PORT,
    val timestamp: Long = System.currentTimeMillis()
)
