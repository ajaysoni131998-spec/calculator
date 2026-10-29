package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String, // User ID, e.g. "#VIKRAM" or "@sarah"
    val name: String,
    val avatarColorHex: String = "#00A884",
    val about: String = "Hey there! I am using GhostChat.",
    val isOnline: Boolean = true,
    val isPinned: Boolean = false,
    val lastSeenText: String = "Online",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String, // "me" or contact id
    val text: String,
    val mediaType: String = "NONE", // "NONE", "IMAGE", "AUDIO", "LOCATION"
    val mediaPayload: String = "", // preview caption or duration
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "READ", // "SENT", "DELIVERED", "READ"
    val readTimestamp: Long? = null,
    val isDisappearing: Boolean = true,
    val destructSeconds: Int = 10, // Default 10 seconds self-destruct as requested
    val isViewOnce: Boolean = false,
    val isDestroyed: Boolean = false
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val contactName: String,
    val isVideo: Boolean,
    val isIncoming: Boolean,
    val isMissed: Boolean,
    val durationSeconds: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "status_stories")
data class StatusStoryEntity(
    @PrimaryKey val id: String,
    val authorId: String,
    val authorName: String,
    val caption: String,
    val backgroundColorHex: String = "#128C7E",
    val storyType: String = "TEXT", // "TEXT", "IMAGE", "VIDEO", "LINK"
    val mediaPayload: String = "",   // Base64 image or video tag
    val linkUrl: String = "",        // Clickable URL for link stories
    val timestamp: Long = System.currentTimeMillis()
)
