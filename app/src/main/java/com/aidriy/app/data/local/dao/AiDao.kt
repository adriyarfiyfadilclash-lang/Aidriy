package com.aidriy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aidriy.app.data.local.entity.AiConversationEntity
import com.aidriy.app.data.local.entity.AiMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConversation(conversation: AiConversationEntity)

    @Query("UPDATE ai_conversations SET updated_at = :updatedAt, title = :title WHERE id = :id")
    suspend fun touchConversation(id: String, title: String, updatedAt: Long): Int

    @Query("SELECT * FROM ai_conversations WHERE id = :id")
    suspend fun getConversation(id: String): AiConversationEntity?

    @Query("SELECT * FROM ai_conversations ORDER BY updated_at DESC")
    fun observeConversations(): Flow<List<AiConversationEntity>>

    @Query("DELETE FROM ai_conversations WHERE id = :id")
    suspend fun deleteConversation(id: String): Int

    @Query("DELETE FROM ai_conversations")
    suspend fun deleteAllConversations()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(message: AiMessageEntity)

    @Query("DELETE FROM ai_messages WHERE id = :id")
    suspend fun deleteMessage(id: String): Int

    @Query("SELECT * FROM ai_messages WHERE conversation_id = :conversationId ORDER BY created_at ASC, rowid ASC")
    fun observeMessages(conversationId: String): Flow<List<AiMessageEntity>>

    @Query("SELECT * FROM ai_messages WHERE conversation_id = :conversationId ORDER BY created_at ASC, rowid ASC")
    suspend fun getMessages(conversationId: String): List<AiMessageEntity>
}
