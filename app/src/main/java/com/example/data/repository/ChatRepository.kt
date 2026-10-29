package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatRepository(
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val callLogDao: CallLogDao,
    private val statusStoryDao: StatusStoryDao,
    private val firestore: FirebaseFirestore,
    private val appContext: Context? = null
) {
    constructor(
        context: Context,
        contactDao: ContactDao,
        messageDao: MessageDao,
        callLogDao: CallLogDao,
        statusStoryDao: StatusStoryDao
    ) : this(
        contactDao = contactDao,
        messageDao = messageDao,
        callLogDao = callLogDao,
        statusStoryDao = statusStoryDao,
        firestore = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        appContext = context.applicationContext
    )

    val allContacts: Flow<List<ContactEntity>> = contactDao.getAllContacts()
    val allCallLogs: Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()
    val allStories: Flow<List<StatusStoryEntity>> = statusStoryDao.getAllStories()

    private var cloudListenerRegistration: ListenerRegistration? = null

    var activeOpenConversationId: String? = null

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    suspend fun markConversationRead(conversationId: String, myUserId: String = "") {
        val now = System.currentTimeMillis()
        val clean = conversationId.trim()
        val withHash = if (clean.startsWith("#")) clean else "#$clean"
        val withoutHash = clean.removePrefix("#")

        messageDao.markConversationAsReadNormalized(clean, withHash, withoutHash, now)

        // Update read status in Firestore cloud so sender's device updates ticks to blue in real-time
        try {
            val senderIdClean = conversationId.trim()
            val myIdClean = myUserId.trim()

            val senderVariants = listOf(senderIdClean, senderIdClean.removePrefix("#"), "#${senderIdClean.removePrefix("#")}").distinct()
            val myVariants = if (myIdClean.isNotBlank()) {
                listOf(myIdClean, myIdClean.removePrefix("#"), "#${myIdClean.removePrefix("#")}").distinct()
            } else emptyList()

            firestore.collection("messages")
                .get()
                .addOnSuccessListener { snapshots ->
                    if (snapshots == null || snapshots.isEmpty) return@addOnSuccessListener
                    val batch = firestore.batch()
                    var updateCount = 0

                    for (doc in snapshots.documents) {
                        val sId = doc.getString("senderId")?.trim() ?: continue
                        val rId = doc.getString("receiverId")?.trim() ?: continue
                        val status = doc.getString("status") ?: "SENT"

                        val isFromSender = senderVariants.any { it.equals(sId, ignoreCase = true) }
                        val isToMe = if (myVariants.isNotEmpty()) {
                            myVariants.any { it.equals(rId, ignoreCase = true) }
                        } else true

                        if (isFromSender && isToMe && status != "READ") {
                            batch.update(
                                doc.reference,
                                mapOf(
                                    "status" to "READ",
                                    "readTimestamp" to FieldValue.serverTimestamp()
                                )
                            )
                            updateCount++
                        }
                    }

                    if (updateCount > 0) {
                        batch.commit().addOnSuccessListener {
                            Log.d("ChatRepository", "Updated $updateCount messages to READ in Firestore")
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error updating conversation read status in Firestore", e)
        }
    }

    suspend fun getReadDisappearingMessages(): List<MessageEntity> {
        return messageDao.getReadDisappearingMessages()
    }

    suspend fun markMessageDestroyed(messageId: String) {
        messageDao.markDestroyed(messageId)
        // Also erase from Firestore cloud
        try {
            firestore.collection("cloud_messages").document(messageId).update("isDestroyed", true)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error updating cloud message destroyed", e)
        }
    }

    suspend fun purgeDestroyed() {
        messageDao.purgeDestroyedMessages()
    }

    suspend fun sendMessage(
        myUserId: String,
        conversationId: String,
        text: String,
        mediaType: String = "NONE",
        mediaPayload: String = "",
        destructSeconds: Int = 10,
        isViewOnce: Boolean = false
    ): MessageEntity {
        val msgId = UUID.randomUUID().toString()
        val msg = MessageEntity(
            id = msgId,
            conversationId = conversationId,
            senderId = "me",
            text = text,
            mediaType = mediaType,
            mediaPayload = mediaPayload,
            timestamp = System.currentTimeMillis(),
            status = "SENT",
            readTimestamp = null,
            isDisappearing = destructSeconds > 0,
            destructSeconds = destructSeconds,
            isViewOnce = isViewOnce,
            isDestroyed = false
        )
        messageDao.insertMessage(msg)

        // Push directly to Firestore central "messages" collection for Device A & B real-time sync
        val firestorePayload = hashMapOf<String, Any>(
            "senderId" to myUserId.trim(),
            "receiverId" to conversationId.trim(),
            "text" to text.trim(),
            "mediaType" to mediaType,
            "mediaPayload" to mediaPayload,
            "status" to "SENT",
            "destructSeconds" to destructSeconds,
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection("messages").document(msgId).set(firestorePayload)
            .addOnSuccessListener {
                Log.d("ChatRepository", "Message synced to Firestore 'messages' collection: $msgId")
            }
            .addOnFailureListener { e ->
                Log.e("ChatRepository", "Firestore write failed: ${e.message}", e)
            }

        return msg
    }

    suspend fun receiveMessage(
        conversationId: String,
        text: String,
        mediaType: String = "NONE",
        mediaPayload: String = "",
        destructSeconds: Int = 10
    ): MessageEntity {
        val msg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = conversationId,
            text = text,
            mediaType = mediaType,
            mediaPayload = mediaPayload,
            timestamp = System.currentTimeMillis(),
            status = "READ",
            readTimestamp = System.currentTimeMillis(),
            isDisappearing = destructSeconds > 0,
            destructSeconds = destructSeconds,
            isViewOnce = false,
            isDestroyed = false
        )
        messageDao.insertMessage(msg)
        return msg
    }

    private val cloudListeners = mutableListOf<ListenerRegistration>()

    fun startListeningToCloudMessages(myUserId: String, coroutineScope: CoroutineScope) {
        startListeningToCloudMessages(listOf(myUserId), coroutineScope)
    }

    fun startListeningToCloudMessages(myIdentifiers: List<String>, coroutineScope: CoroutineScope) {
        stopListeningToCloud()
        val distinctIds = myIdentifiers
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()

        Log.d("ChatRepository", "Starting Firestore real-time listener for user identifiers: $distinctIds")

        // Central Firestore "messages" collection snapshot listener
        val reg = firestore.collection("messages")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("ChatRepository", "Firestore messages snapshot listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                coroutineScope.launch(Dispatchers.IO) {
                    for (doc in snapshots.documents) {
                        val id = doc.id
                        val senderId = doc.getString("senderId")?.trim() ?: continue
                        val receiverId = doc.getString("receiverId")?.trim() ?: continue
                        val text = doc.getString("text") ?: ""
                        val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time
                            ?: System.currentTimeMillis()

                        val senderLower = senderId.lowercase()
                        val receiverLower = receiverId.lowercase()

                        val isSentByMe = distinctIds.any { it == senderLower || it == senderLower.removePrefix("#") }
                        val isReceivedByMe = distinctIds.any { it == receiverLower || it == receiverLower.removePrefix("#") }

                        if (!isSentByMe && !isReceivedByMe) {
                            // Message is not meant for this device
                            continue
                        }

                        val localConversationId = if (isSentByMe) receiverId else senderId
                        val localSenderId = if (isSentByMe) "me" else senderId

                        val existingMsg = messageDao.getMessageById(id)
                        if (existingMsg != null && existingMsg.isDestroyed) {
                            continue
                        }

                        // Ensure contact exists locally so chat appears in Chats tab
                        val contactIdToEnsure = if (isSentByMe) receiverId else senderId
                        val existingContact = contactDao.getContactById(contactIdToEnsure)
                        if (existingContact == null) {
                            addContact(
                                id = contactIdToEnsure,
                                name = contactIdToEnsure,
                                about = "Real-time Peer on GhostChat"
                            )
                        }

                        val isNewIncoming = isReceivedByMe && (existingMsg == null)

                        // Insert or update message in Room Database
                        // When isMe is true -> senderId is "me" -> Renders on RIGHT (Green bubble #D9FDD3)
                        // When isMe is false -> senderId is senderId -> Renders on LEFT (White bubble #FFFFFF)
                        val openId = activeOpenConversationId
                        val isOpenNow = openId != null && (
                            openId.equals(localConversationId, ignoreCase = true) ||
                            openId.removePrefix("#").equals(localConversationId.removePrefix("#"), ignoreCase = true)
                        )
                        val cloudMediaType = doc.getString("mediaType") ?: "NONE"
                        val cloudMediaPayload = doc.getString("mediaPayload") ?: ""
                        val cloudDestructSec = doc.getLong("destructSeconds")?.toInt() ?: 10
                        val cloudStatus = doc.getString("status") ?: "SENT"

                        val computedStatus = if (isSentByMe) {
                            cloudStatus
                        } else if (isOpenNow) {
                            "READ"
                        } else {
                            if (cloudStatus == "READ") "READ" else "DELIVERED"
                        }

                        // Determine readTimestamp strictly:
                        // ONLY set when message is READ (when recipient opens the chat).
                        // If status is SENT or DELIVERED, readTimestamp MUST be null so the countdown timer DOES NOT start!
                        val resolvedReadTimestamp = when {
                            existingMsg?.readTimestamp != null -> existingMsg.readTimestamp
                            computedStatus == "READ" -> {
                                doc.getTimestamp("readTimestamp")?.toDate()?.time ?: System.currentTimeMillis()
                            }
                            else -> null
                        }

                        // If recipient is receiving and chat is currently open, mark READ in Firestore cloud
                        if (isReceivedByMe && isOpenNow && cloudStatus != "READ") {
                            try {
                                firestore.collection("messages").document(id).update(
                                    mapOf(
                                        "status" to "READ",
                                        "readTimestamp" to FieldValue.serverTimestamp()
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e("ChatRepository", "Error updating message READ status in Firestore", e)
                            }
                        } else if (isReceivedByMe && !isOpenNow && cloudStatus == "SENT") {
                            // If recipient is receiving and message was just sent, mark DELIVERED in Firestore cloud
                            try {
                                firestore.collection("messages").document(id).update("status", "DELIVERED")
                            } catch (e: Exception) {
                                Log.e("ChatRepository", "Error updating message DELIVERED status in Firestore", e)
                            }
                        }

                        val localMsg = MessageEntity(
                            id = id,
                            conversationId = localConversationId,
                            senderId = localSenderId,
                            text = text,
                            mediaType = cloudMediaType,
                            mediaPayload = cloudMediaPayload,
                            timestamp = timestamp,
                            status = computedStatus,
                            readTimestamp = resolvedReadTimestamp,
                            isDisappearing = true,
                            destructSeconds = cloudDestructSec,
                            isViewOnce = false,
                            isDestroyed = false
                        )
                        messageDao.insertMessage(localMsg)

                        if (isNewIncoming) {
                            appContext?.let { ctx ->
                                com.example.data.notification.StealthNotificationHelper.showStealthMathNotification(
                                    ctx,
                                    localConversationId
                                )
                            }
                        }
                    }
                }
            }
        cloudListeners.add(reg)

        // Real-time Firestore "statuses" collection listener (Syncs stories across Device A and Device B)
        val statusReg = firestore.collection("statuses")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                coroutineScope.launch(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    for (doc in snapshot.documents) {
                        val sId = doc.id
                        val aId = doc.getString("authorId") ?: ""
                        val aName = doc.getString("authorName") ?: aId
                        val cap = doc.getString("caption") ?: ""
                        val bg = doc.getString("backgroundColorHex") ?: "#128C7E"
                        val sType = doc.getString("storyType") ?: "TEXT"
                        val mPayload = doc.getString("mediaPayload") ?: ""
                        val lUrl = doc.getString("linkUrl") ?: ""
                        val ts = doc.getLong("timestamp")
                            ?: doc.getTimestamp("timestamp")?.toDate()?.time
                            ?: now

                        // Disappearing status: vanishes after 24 hours
                        if (now - ts < 86400000L) {
                            val story = StatusStoryEntity(
                                id = sId,
                                authorId = aId,
                                authorName = aName,
                                caption = cap,
                                backgroundColorHex = bg,
                                storyType = sType,
                                mediaPayload = mPayload,
                                linkUrl = lUrl,
                                timestamp = ts
                            )
                            statusStoryDao.insertStory(story)
                        }
                    }
                }
            }
        cloudListeners.add(statusReg)
    }

    fun stopListeningToCloud() {
        cloudListeners.forEach { it.remove() }
        cloudListeners.clear()
        cloudListenerRegistration?.remove()
        cloudListenerRegistration = null
    }

    suspend fun addContact(id: String, name: String, about: String = "Available"): ContactEntity {
        val colors = listOf("#00A884", "#25D366", "#128C7E", "#34B7F1", "#7B1FA2", "#E91E63", "#FF9800")
        val randomColor = colors.random()
        val contact = ContactEntity(
            id = id,
            name = name,
            avatarColorHex = randomColor,
            about = about,
            isOnline = true
        )
        contactDao.insertContact(contact)
        return contact
    }

    suspend fun logCall(
        contactId: String,
        contactName: String,
        isVideo: Boolean,
        isIncoming: Boolean,
        isMissed: Boolean,
        durationSeconds: Int
    ) {
        val log = CallLogEntity(
            id = UUID.randomUUID().toString(),
            contactId = contactId,
            contactName = contactName,
            isVideo = isVideo,
            isIncoming = isIncoming,
            isMissed = isMissed,
            durationSeconds = durationSeconds,
            timestamp = System.currentTimeMillis()
        )
        callLogDao.insertCallLog(log)
    }

    suspend fun postStory(
        authorId: String,
        authorName: String,
        caption: String,
        bgHex: String,
        storyType: String = "TEXT",
        mediaPayload: String = "",
        linkUrl: String = ""
    ) {
        val storyId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val story = StatusStoryEntity(
            id = storyId,
            authorId = authorId,
            authorName = authorName,
            caption = caption,
            backgroundColorHex = bgHex,
            storyType = storyType,
            mediaPayload = mediaPayload,
            linkUrl = linkUrl,
            timestamp = now
        )
        // 1. Insert into local SQLite
        statusStoryDao.insertStory(story)

        // 2. Sync to Firestore central "statuses" collection so all devices see it in real-time
        val storyPayload = hashMapOf<String, Any>(
            "id" to storyId,
            "authorId" to authorId.trim(),
            "authorName" to authorName.trim(),
            "caption" to caption.trim(),
            "backgroundColorHex" to bgHex,
            "storyType" to storyType,
            "mediaPayload" to mediaPayload,
            "linkUrl" to linkUrl.trim(),
            "timestamp" to now
        )
        firestore.collection("statuses").document(storyId).set(storyPayload)
            .addOnSuccessListener {
                Log.d("ChatRepository", "Story synced to Firestore: $storyId")
            }
            .addOnFailureListener { e ->
                Log.e("ChatRepository", "Failed to sync story to Firestore: ${e.message}")
            }
    }

    suspend fun seedInitialDataIfEmpty() {
        val existing = contactDao.getContactById("#VIKRAM")
        if (existing != null) return

        val seedContacts = listOf(
            ContactEntity(
                id = "#VIKRAM",
                name = "Vikram (Encrypted Peer)",
                avatarColorHex = "#00A884",
                about = "🔐 Zero trace channel active",
                isOnline = true,
                isPinned = true,
                lastSeenText = "Online"
            ),
            ContactEntity(
                id = "#PRIYA",
                name = "Dr. Priya Roy",
                avatarColorHex = "#128C7E",
                about = "Calls only on stealth mode",
                isOnline = true,
                isPinned = false,
                lastSeenText = "Online"
            ),
            ContactEntity(
                id = "#CIPHER",
                name = "Cipher Protocol Bot",
                avatarColorHex = "#34B7F1",
                about = "Auto-destruct echo node | Ask anything",
                isOnline = true,
                isPinned = false,
                lastSeenText = "Online"
            )
        )
        contactDao.insertAllContacts(seedContacts)

        val now = System.currentTimeMillis()
        val seedMessages = listOf(
            MessageEntity(
                id = "m1",
                conversationId = "#VIKRAM",
                senderId = "#VIKRAM",
                text = "Bhai, all messages disappear automatically in 1 minute! Try sending anything 🤫",
                mediaType = "NONE",
                timestamp = now - 120000,
                status = "READ",
                readTimestamp = now - 10000,
                isDisappearing = true,
                destructSeconds = 60
            )
        )
        messageDao.insertMessages(seedMessages)
    }

    suspend fun purgeAllData() {
        contactDao.deleteAll()
        messageDao.deleteAllMessages()
        callLogDao.deleteAll()
        statusStoryDao.deleteAll()
    }

    suspend fun deleteConversation(contactId: String, myUserId: String, deleteContact: Boolean = true) {
        messageDao.deleteMessagesForConversation(contactId)
        if (deleteContact) {
            contactDao.deleteContactById(contactId)
        }
        FirebaseManager.deleteConversationMessages(firestore, myUserId, contactId)
    }

    suspend fun clearConversationMessages(contactId: String, myUserId: String) {
        messageDao.deleteMessagesForConversation(contactId)
        FirebaseManager.deleteConversationMessages(firestore, myUserId, contactId)
    }

    suspend fun togglePinContact(contactId: String, currentPinned: Boolean) {
        contactDao.updatePinStatus(contactId, !currentPinned)
    }

    fun initiateCall(
        callId: String,
        callerId: String,
        callerName: String,
        receiverId: String,
        callType: String,
        sdpOffer: String = "",
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        FirebaseManager.initiateCall(
            firestore = firestore,
            callId = callId,
            callerId = callerId,
            callerName = callerName,
            receiverId = receiverId,
            callType = callType,
            sdpOffer = sdpOffer,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    fun updateCallStatus(callId: String, status: String, sdpAnswer: String = "") {
        FirebaseManager.updateCallStatus(firestore, callId, status, sdpAnswer)
    }

    fun sendIceCandidate(callId: String, isCaller: Boolean, candidateJson: String) {
        FirebaseManager.sendIceCandidate(firestore, callId, isCaller, candidateJson)
    }

    fun listenForIncomingCalls(
        myIdentifiers: List<String>,
        onIncomingCall: (FirestoreCallSignal?) -> Unit
    ): ListenerRegistration {
        return FirebaseManager.listenForIncomingCalls(firestore, myIdentifiers) { signal ->
            appContext?.let { ctx ->
                if (signal != null) {
                    com.example.data.notification.StealthNotificationHelper.showStealthCallNotification(ctx, signal)
                } else {
                    com.example.data.notification.StealthNotificationHelper.cancelCallNotification(ctx)
                }
            }
            onIncomingCall(signal)
        }
    }

    fun listenToCall(
        callId: String,
        onStatusChanged: (status: String, sdpAnswer: String, remoteIp: String, remoteAudioPort: Int, remoteVideoPort: Int) -> Unit
    ): ListenerRegistration {
        return FirebaseManager.listenToCall(firestore, callId, onStatusChanged)
    }
}
