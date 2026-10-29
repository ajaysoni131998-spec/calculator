package com.example.ui.messenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CallLogEntity
import com.example.data.local.ContactEntity
import com.example.data.local.MessageEntity
import com.example.data.local.StatusStoryEntity
import com.example.data.preferences.StealthPreferences
import com.example.data.repository.ChatRepository
import com.example.data.repository.FirestoreCallSignal
import com.example.data.ai.GhostAiManager
import com.example.data.ai.PrivacyAnalysis
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class CallState {
    IDLE,
    CALLING,
    CONNECTED,
    ENDED
}

data class ActiveCallInfo(
    val contact: ContactEntity,
    val isVideo: Boolean,
    val state: CallState = CallState.CALLING,
    val durationSeconds: Int = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val callId: String = "",
    val isIncoming: Boolean = false,
    val remoteIp: String = "127.0.0.1",
    val remoteAudioPort: Int = com.example.data.repository.PeerCallEngine.DEFAULT_RECEIVER_AUDIO_PORT,
    val remoteVideoPort: Int = com.example.data.repository.PeerCallEngine.DEFAULT_RECEIVER_VIDEO_PORT
)

data class MessengerUiState(
    val isDecoyMode: Boolean = false,
    val myUserId: String = "",
    val myDisplayName: String = "",
    val currentTab: Int = 0, // 0: CHATS, 1: STATUS, 2: CALLS, 3: SETTINGS
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val activeContact: ContactEntity? = null,
    val activeCall: ActiveCallInfo? = null,
    val incomingCall: FirestoreCallSignal? = null,
    val viewingStory: StatusStoryEntity? = null,
    val showAddContactDialog: Boolean = false,
    val showNewStoryDialog: Boolean = false,
    val showNukeConfirmDialog: Boolean = false,
    val remainingSecondsMap: Map<String, Int> = emptyMap()
)

class MessengerViewModel(
    private val repository: ChatRepository,
    private val preferences: StealthPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MessengerUiState(
            myUserId = preferences.myUserId,
            myDisplayName = preferences.myDisplayName
        )
    )
    val uiState = _uiState.asStateFlow()

    val contacts: StateFlow<List<ContactEntity>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callLogs: StateFlow<List<CallLogEntity>> = repository.allCallLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statusStories: StateFlow<List<StatusStoryEntity>> = repository.allStories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val activeMessages = _activeMessages.asStateFlow()

    private var activeConversationJob: Job? = null
    private var callTimerJob: Job? = null
    private var destructionTickerJob: Job? = null
    private var incomingCallRegistration: ListenerRegistration? = null
    private var activeCallRegistration: ListenerRegistration? = null

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }
        startDestructionTicker()
        repository.startListeningToCloudMessages(preferences.myUserId, viewModelScope)
        startListeningForIncomingCalls(listOf(preferences.myUserId))
    }

    private fun startListeningForIncomingCalls(myIdentifiers: List<String>) {
        incomingCallRegistration?.remove()
        incomingCallRegistration = repository.listenForIncomingCalls(myIdentifiers) { signal ->
            _uiState.value = _uiState.value.copy(incomingCall = signal)
        }
    }

    fun setDecoyMode(isDecoy: Boolean) {
        _uiState.value = _uiState.value.copy(
            isDecoyMode = isDecoy,
            myUserId = if (isDecoy) "#STUDY-NOTES" else preferences.myUserId,
            myDisplayName = if (isDecoy) "Class Notes" else preferences.myDisplayName
        )
    }

    fun setTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(currentTab = tabIndex)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleSearch() {
        val willSearch = !_uiState.value.isSearching
        _uiState.value = _uiState.value.copy(
            isSearching = willSearch,
            searchQuery = if (!willSearch) "" else _uiState.value.searchQuery
        )
    }

    fun openConversation(contact: ContactEntity) {
        _uiState.value = _uiState.value.copy(activeContact = contact)
        repository.activeOpenConversationId = contact.id
        activeConversationJob?.cancel()
        activeConversationJob = viewModelScope.launch {
            repository.markConversationRead(contact.id, preferences.myUserId)
            repository.getMessagesForConversation(contact.id).collect { messages ->
                _activeMessages.value = messages
            }
        }
    }

    fun closeConversation() {
        repository.activeOpenConversationId = null
        activeConversationJob?.cancel()
        _uiState.value = _uiState.value.copy(activeContact = null)
        _activeMessages.value = emptyList()
    }

    fun sendMessage(
        text: String,
        mediaType: String = "NONE",
        mediaPayload: String = "",
        isViewOnce: Boolean = false
    ) {
        val contact = _uiState.value.activeContact ?: return
        if (text.isBlank() && mediaType == "NONE") return

        val destructSec = preferences.defaultDestructSeconds // 60s (1 min) by default

        viewModelScope.launch {
            repository.sendMessage(
                myUserId = preferences.myUserId,
                conversationId = contact.id,
                text = text.trim(),
                mediaType = mediaType,
                mediaPayload = mediaPayload,
                destructSeconds = destructSec,
                isViewOnce = isViewOnce
            )

            // Only simulate bot replies if chatting with built-in test personas
            if (contact.id in listOf("#VIKRAM", "#PRIYA", "#CIPHER")) {
                delay(2500)
                simulatePeerResponse(contact, text)
            }
        }
    }

    private suspend fun simulatePeerResponse(contact: ContactEntity, sentText: String) {
        val replies = when (contact.id) {
            "#VIKRAM" -> listOf(
                "Got your message! Notice the 60s countdown? It will vanish automatically.",
                "Safe and secure! Zero logs stored on servers.",
                "Call me anytime using the top voice/video call buttons 👍",
                "Self-destruct timer working perfectly. See it burning away! 🔥"
            )
            "#PRIYA" -> listOf(
                "Received! Voice note quality is crystal clear.",
                "End-to-end encryption handshake confirmed 🛡️",
                "Remember to lock back to the calculator with the quick-lock icon!",
                "Check the status tab too, added a disappearing story."
            )
            "#CIPHER" -> listOf(
                "Cipher echo: Message verified. 60-second entropy purge active.",
                "Encrypted packet decoded: '$sentText'. Ephemeral memory active.",
                "Status: Node online. Ready for secure voice/video test call."
            )
            else -> listOf(
                "Hello! Received your message on GhostChat User ID: ${contact.id}.",
                "Nice to connect with you securely here!",
                "All chats will self-destruct within 1 minute of reading."
            )
        }
        val replyText = replies.random()
        repository.receiveMessage(
            conversationId = contact.id,
            text = replyText,
            destructSeconds = preferences.defaultDestructSeconds
        )
    }

    private fun startDestructionTicker() {
        destructionTickerJob?.cancel()
        destructionTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                checkAndPurgeExpiredMessages()
            }
        }
    }

    private suspend fun checkAndPurgeExpiredMessages() {
        val now = System.currentTimeMillis()
        val readDisappearing = repository.getReadDisappearingMessages()
        val updatedMap = mutableMapOf<String, Int>()

        for (msg in readDisappearing) {
            val readTime = msg.readTimestamp ?: continue
            val elapsedSec = ((now - readTime) / 1000).toInt()
            val remaining = msg.destructSeconds - elapsedSec

            if (remaining <= 0) {
                repository.markMessageDestroyed(msg.id)
            } else {
                updatedMap[msg.id] = remaining
            }
        }

        _uiState.value = _uiState.value.copy(remainingSecondsMap = updatedMap)
    }

    fun startVoiceCall(contact: ContactEntity) {
        startCall(contact, isVideo = false)
    }

    fun startVideoCall(contact: ContactEntity) {
        startCall(contact, isVideo = true)
    }

    private fun startCall(contact: ContactEntity, isVideo: Boolean) {
        val callId = UUID.randomUUID().toString()
        val sdpOffer = "v=0\r\no=- ${System.currentTimeMillis()} 2 IN IP4 127.0.0.1\r\ns=GhostChat-WebRTC\r\nt=0 0\r\nm=${if (isVideo) "video" else "audio"} 9 UDP/TLS/RTP/SAVPF 111"

        val callInfo = ActiveCallInfo(
            contact = contact,
            isVideo = isVideo,
            state = CallState.CALLING,
            callId = callId,
            isIncoming = false
        )
        _uiState.value = _uiState.value.copy(activeCall = callInfo)

        // Initiate WebRTC call signal in Firestore
        repository.initiateCall(
            callId = callId,
            callerId = preferences.myUserId,
            callerName = preferences.myDisplayName,
            receiverId = contact.id,
            callType = if (isVideo) "VIDEO" else "VOICE",
            sdpOffer = sdpOffer
        )

        // Listen for remote peer WebRTC answer/rejection
        activeCallRegistration?.remove()
        activeCallRegistration = repository.listenToCall(callId) { status, sdpAnswer, remoteIp, remoteAudioPort, remoteVideoPort ->
            when (status) {
                "CONNECTED" -> {
                    _uiState.value = _uiState.value.copy(
                        activeCall = _uiState.value.activeCall?.copy(
                            state = CallState.CONNECTED,
                            remoteIp = remoteIp,
                            remoteAudioPort = remoteAudioPort,
                            remoteVideoPort = remoteVideoPort
                        )
                    )
                    startCallTimer()
                }
                "REJECTED" -> {
                    _uiState.value = _uiState.value.copy(
                        activeCall = _uiState.value.activeCall?.copy(state = CallState.ENDED)
                    )
                    viewModelScope.launch {
                        delay(1200)
                        cleanUpCall()
                    }
                }
                "ENDED" -> {
                    _uiState.value = _uiState.value.copy(
                        activeCall = _uiState.value.activeCall?.copy(state = CallState.ENDED)
                    )
                    viewModelScope.launch {
                        delay(600)
                        cleanUpCall()
                    }
                }
            }
        }
    }

    fun acceptIncomingCall() {
        val incoming = _uiState.value.incomingCall ?: return
        val isVideo = incoming.callType.equals("VIDEO", ignoreCase = true)
        val sdpAnswer = "v=0\r\no=- ${System.currentTimeMillis()} 2 IN IP4 127.0.0.1\r\ns=GhostChat-WebRTC\r\nt=0 0\r\nm=${if (isVideo) "video" else "audio"} 9 UDP/TLS/RTP/SAVPF 111"

        val callerContact = contacts.value.find {
            it.id.equals(incoming.callerId, ignoreCase = true) ||
            it.id.equals("#${incoming.callerId}", ignoreCase = true) ||
            it.id.removePrefix("#").equals(incoming.callerId.removePrefix("#"), ignoreCase = true)
        } ?: ContactEntity(
            id = incoming.callerId,
            name = incoming.callerName.ifBlank { incoming.callerId },
            avatarColorHex = "#00A884",
            about = "Incoming Peer Call"
        )

        val remoteIp = incoming.callerIp.ifBlank { "127.0.0.1" }
        val remoteAudioPort = incoming.callerAudioPort
        val remoteVideoPort = incoming.callerVideoPort

        repository.updateCallStatus(incoming.callId, "CONNECTED", sdpAnswer = sdpAnswer)

        _uiState.value = _uiState.value.copy(
            incomingCall = null,
            activeCall = ActiveCallInfo(
                contact = callerContact,
                isVideo = isVideo,
                state = CallState.CONNECTED,
                callId = incoming.callId,
                isIncoming = true,
                remoteIp = remoteIp,
                remoteAudioPort = remoteAudioPort,
                remoteVideoPort = remoteVideoPort
            )
        )

        startCallTimer()

        activeCallRegistration?.remove()
        activeCallRegistration = repository.listenToCall(incoming.callId) { status, _, _, _, _ ->
            if (status == "ENDED") {
                _uiState.value = _uiState.value.copy(
                    activeCall = _uiState.value.activeCall?.copy(state = CallState.ENDED)
                )
                viewModelScope.launch {
                    delay(600)
                    cleanUpCall()
                }
            }
        }
    }

    fun declineIncomingCall() {
        val incoming = _uiState.value.incomingCall ?: return
        repository.updateCallStatus(incoming.callId, "REJECTED")
        _uiState.value = _uiState.value.copy(incomingCall = null)
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            var seconds = 0
            while (isActive && _uiState.value.activeCall?.state == CallState.CONNECTED) {
                delay(1000)
                seconds++
                _uiState.value = _uiState.value.copy(
                    activeCall = _uiState.value.activeCall?.copy(durationSeconds = seconds)
                )
            }
        }
    }

    fun toggleMute() {
        val current = _uiState.value.activeCall ?: return
        _uiState.value = _uiState.value.copy(
            activeCall = current.copy(isMuted = !current.isMuted)
        )
    }

    fun toggleSpeaker() {
        val current = _uiState.value.activeCall ?: return
        _uiState.value = _uiState.value.copy(
            activeCall = current.copy(isSpeakerOn = !current.isSpeakerOn)
        )
    }

    fun toggleCamera() {
        val current = _uiState.value.activeCall ?: return
        _uiState.value = _uiState.value.copy(
            activeCall = current.copy(isVideoEnabled = !current.isVideoEnabled)
        )
    }

    fun endCall() {
        val call = _uiState.value.activeCall ?: return
        callTimerJob?.cancel()

        if (call.callId.isNotBlank()) {
            repository.updateCallStatus(call.callId, "ENDED")
        }

        viewModelScope.launch {
            repository.logCall(
                contactId = call.contact.id,
                contactName = call.contact.name,
                isVideo = call.isVideo,
                isIncoming = call.isIncoming,
                isMissed = false,
                durationSeconds = call.durationSeconds
            )
        }

        _uiState.value = _uiState.value.copy(
            activeCall = call.copy(state = CallState.ENDED)
        )

        viewModelScope.launch {
            delay(800)
            cleanUpCall()
        }
    }

    private fun cleanUpCall() {
        activeCallRegistration?.remove()
        activeCallRegistration = null
        callTimerJob?.cancel()
        _uiState.value = _uiState.value.copy(activeCall = null)
    }

    fun camouflageMessage(original: String, style: String = "HOMEWORK", onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = GhostAiManager.camouflageMessage(original, style)
            onResult(result)
        }
    }

    fun scanPrivacy(text: String, onResult: (PrivacyAnalysis) -> Unit) {
        viewModelScope.launch {
            val analysis = GhostAiManager.scanMessagePrivacy(text)
            onResult(analysis)
        }
    }

    fun getSmartReplies(lastMessage: String, onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            val replies = GhostAiManager.generateSmartReplies(lastMessage)
            onResult(replies)
        }
    }

    fun deleteConversation(contact: ContactEntity, deleteContact: Boolean = true) {
        viewModelScope.launch {
            repository.deleteConversation(contact.id, preferences.myUserId, deleteContact)
            if (_uiState.value.activeContact?.id == contact.id) {
                closeConversation()
            }
        }
    }

    fun clearConversationMessages(contact: ContactEntity) {
        viewModelScope.launch {
            repository.clearConversationMessages(contact.id, preferences.myUserId)
            if (_uiState.value.activeContact?.id == contact.id) {
                _activeMessages.value = emptyList()
            }
        }
    }

    fun togglePinContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.togglePinContact(contact.id, contact.isPinned)
        }
    }

    fun showAddContactDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddContactDialog = show)
    }

    fun onUserSignedIn(email: String?, displayName: String?) {
        if (!email.isNullOrBlank()) {
            preferences.myUserId = email.trim()
        }
        if (!displayName.isNullOrBlank()) {
            preferences.myDisplayName = displayName.trim()
        }
        _uiState.value = _uiState.value.copy(
            myUserId = preferences.myUserId,
            myDisplayName = preferences.myDisplayName
        )
        val identifiers = listOfNotNull(preferences.myUserId, email)
        repository.startListeningToCloudMessages(identifiers, viewModelScope)
        startListeningForIncomingCalls(identifiers)
    }

    fun addContact(id: String, name: String, about: String) {
        val trimmed = id.trim()
        if (trimmed.isBlank()) return
        val formattedId = if (trimmed.contains("@") || trimmed.startsWith("#") || trimmed.startsWith("@")) {
            trimmed
        } else {
            "#$trimmed"
        }
        val contactName = if (name.isNotBlank()) name.trim() else formattedId
        viewModelScope.launch {
            repository.addContact(formattedId, contactName, about.trim())
            _uiState.value = _uiState.value.copy(showAddContactDialog = false)
        }
    }

    fun showNewStoryDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showNewStoryDialog = show)
    }

    fun postStory(
        caption: String,
        bgHex: String,
        storyType: String = "TEXT",
        mediaPayload: String = "",
        linkUrl: String = ""
    ) {
        if (caption.isBlank() && mediaPayload.isBlank() && linkUrl.isBlank()) return
        viewModelScope.launch {
            repository.postStory(
                authorId = preferences.myUserId,
                authorName = preferences.myDisplayName,
                caption = caption,
                bgHex = bgHex,
                storyType = storyType,
                mediaPayload = mediaPayload,
                linkUrl = linkUrl
            )
            _uiState.value = _uiState.value.copy(showNewStoryDialog = false)
        }
    }

    fun viewStory(story: StatusStoryEntity?) {
        _uiState.value = _uiState.value.copy(viewingStory = story)
    }

    fun showNukeDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showNukeConfirmDialog = show)
    }

    fun nukeAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.purgeAllData()
            preferences.clearAllData()
            _uiState.value = _uiState.value.copy(showNukeConfirmDialog = false)
            onComplete()
        }
    }

    fun updateStealthSettings(
        newSecretCode: String,
        newDecoyCode: String,
        newUserId: String,
        newDisplayName: String,
        destructSec: Int
    ) {
        if (newSecretCode.isNotBlank()) preferences.secretCode = newSecretCode
        if (newDecoyCode.isNotBlank()) preferences.decoyCode = newDecoyCode
        if (newUserId.isNotBlank()) preferences.myUserId = newUserId
        if (newDisplayName.isNotBlank()) preferences.myDisplayName = newDisplayName
        preferences.defaultDestructSeconds = destructSec

        _uiState.value = _uiState.value.copy(
            myUserId = preferences.myUserId,
            myDisplayName = preferences.myDisplayName
        )
        repository.startListeningToCloudMessages(preferences.myUserId, viewModelScope)
        startListeningForIncomingCalls(listOf(preferences.myUserId))
    }

    fun getSecretCode(): String = preferences.secretCode
    fun getDecoyCode(): String = preferences.decoyCode
    fun getDefaultDestructSeconds(): Int = preferences.defaultDestructSeconds

    override fun onCleared() {
        super.onCleared()
        repository.stopListeningToCloud()
        incomingCallRegistration?.remove()
        activeCallRegistration?.remove()
        destructionTickerJob?.cancel()
        activeConversationJob?.cancel()
        callTimerJob?.cancel()
    }
}
