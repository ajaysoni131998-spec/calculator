package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY isPinned DESC, createdAt DESC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllContacts(contacts: List<ContactEntity>)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContactById(id: String)

    @Query("UPDATE contacts SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinStatus(id: String, isPinned: Boolean)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND isDestroyed = 0 ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isDestroyed = 0 ORDER BY timestamp DESC")
    fun getAllActiveMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE isDestroyed = 0 AND isDisappearing = 1 AND readTimestamp IS NOT NULL")
    suspend fun getReadDisappearingMessages(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = 'READ', readTimestamp = :readTime WHERE conversationId = :conversationId AND readTimestamp IS NULL")
    suspend fun markConversationAsRead(conversationId: String, readTime: Long)

    @Query("UPDATE messages SET status = 'READ', readTimestamp = :readTime WHERE (LOWER(conversationId) = LOWER(:conversationId) OR LOWER(conversationId) = LOWER(:convWithHash) OR LOWER(conversationId) = LOWER(:convWithoutHash)) AND readTimestamp IS NULL")
    suspend fun markConversationAsReadNormalized(conversationId: String, convWithHash: String, convWithoutHash: String, readTime: Long)

    @Query("UPDATE messages SET isDestroyed = 1 WHERE id = :messageId")
    suspend fun markDestroyed(messageId: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE isDestroyed = 1")
    suspend fun purgeDestroyedMessages()

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity)

    @Query("DELETE FROM call_logs")
    suspend fun deleteAll()
}

@Dao
interface StatusStoryDao {
    @Query("SELECT * FROM status_stories ORDER BY timestamp DESC")
    fun getAllStories(): Flow<List<StatusStoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StatusStoryEntity)

    @Query("DELETE FROM status_stories")
    suspend fun deleteAll()
}
