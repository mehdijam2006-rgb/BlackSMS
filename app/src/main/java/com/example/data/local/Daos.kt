package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.domain.model.BlockSource
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE isBlocked = 0 ORDER BY timestamp DESC, id DESC")
    fun observeInboxMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isBlocked = 1 ORDER BY timestamp DESC, id DESC")
    fun observeBlockedMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadKey = :threadKey AND isBlocked = 0 ORDER BY timestamp ASC, id ASC")
    fun observeThreadMessages(threadKey: String): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE isBlocked = 0 AND isRead = 0 AND isOutgoing = 0")
    fun observeUnreadInboxCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE isBlocked = 1")
    fun observeBlockedCount(): Flow<Int>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): MessageEntity?

    @Query("SELECT systemSmsId FROM messages WHERE systemSmsId IS NOT NULL")
    suspend fun getAllSyncedSystemIds(): List<Long>

    @Query(
        "SELECT * FROM messages WHERE dedupHash = :dedupHash AND isOutgoing = :isOutgoing " +
            "AND timestamp BETWEEN :minTimestamp AND :maxTimestamp LIMIT 1"
    )
    suspend fun findMatchingMessage(
        dedupHash: String,
        isOutgoing: Boolean,
        minTimestamp: Long,
        maxTimestamp: Long
    ): MessageEntity?

    @Query("UPDATE messages SET systemSmsId = :systemSmsId WHERE id = :id")
    suspend fun attachSystemSmsId(id: Long, systemSmsId: Long)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :id")
    suspend fun updateDeliveryStatus(id: Long, status: DeliveryStatus)

    @Query("SELECT * FROM messages WHERE isBlocked = 0 AND isOutgoing = 0 ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentIncomingBodies(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isBlocked = 1 AND isOutgoing = 0 ORDER BY timestamp DESC LIMIT 150")
    suspend fun getRecentBlockedMessages(): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE dedupHash = :hash AND timestamp >= :minTimestamp")
    suspend fun countRecentDuplicate(hash: String, minTimestamp: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET isRead = 1 WHERE threadKey = :threadKey AND isBlocked = 0")
    suspend fun markThreadRead(threadKey: String)

    @Query("UPDATE messages SET isRead = 1 WHERE isBlocked = 0")
    suspend fun markAllInboxRead()

    @Query("UPDATE messages SET isRead = :isRead WHERE threadKey = :threadKey AND isBlocked = 0")
    suspend fun setThreadReadState(threadKey: String, isRead: Boolean)

    @Query("UPDATE messages SET isArchived = :archived, isPinned = CASE WHEN :archived = 1 THEN 0 ELSE isPinned END WHERE threadKey = :threadKey AND isBlocked = 0")
    suspend fun setThreadArchived(threadKey: String, archived: Boolean)

    @Query("UPDATE messages SET isPinned = :pinned WHERE threadKey = :threadKey AND isBlocked = 0")
    suspend fun setThreadPinned(threadKey: String, pinned: Boolean)

    @Query("UPDATE messages SET isBlocked = 1, isArchived = 0, isPinned = 0, blockSource = :source, blockReason = :reason WHERE threadKey = :threadKey AND isBlocked = 0")
    suspend fun blockThreadMessages(threadKey: String, source: BlockSource, reason: String)

    @Query("UPDATE messages SET isBlocked = 1, isArchived = 0, isPinned = 0, blockSource = :source, blockReason = :reason WHERE id = :messageId")
    suspend fun blockSingleMessage(messageId: Long, source: BlockSource, reason: String)

    @Query("UPDATE messages SET isBlocked = 0, isRead = 0, blockSource = NULL, blockReason = NULL WHERE id = :messageId")
    suspend fun restoreBlockedMessage(messageId: Long)

    @Query("UPDATE messages SET isBlocked = 0, isRead = 0, blockSource = NULL, blockReason = NULL WHERE threadKey = :threadKey AND isBlocked = 1")
    suspend fun restoreAllFromThread(threadKey: String)

    @Query("DELETE FROM messages WHERE id IN (:ids) AND isBlocked = 1")
    suspend fun deleteBlockedMessagesByIds(ids: List<Long>)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteSingleMessage(messageId: Long)

    @Query("DELETE FROM messages WHERE threadKey = :threadKey AND isBlocked = 0")
    suspend fun deleteInboxThread(threadKey: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}

@Dao
interface FilterRuleDao {
    @Query("SELECT * FROM filter_rules ORDER BY priority DESC, id ASC")
    fun observeAllRules(): Flow<List<FilterRuleEntity>>

    @Query("SELECT * FROM filter_rules ORDER BY priority DESC, id ASC")
    suspend fun getAllRulesOnce(): List<FilterRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: FilterRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<FilterRuleEntity>)

    @Update
    suspend fun updateRule(rule: FilterRuleEntity)

    @Query("UPDATE filter_rules SET isEnabled = :enabled WHERE id = :ruleId")
    suspend fun setRuleEnabled(ruleId: Long, enabled: Boolean)

    @Query("UPDATE filter_rules SET blockedCount = blockedCount + 1 WHERE id = :ruleId")
    suspend fun incrementBlockedCount(ruleId: Long)

    @Query("DELETE FROM filter_rules WHERE id = :ruleId")
    suspend fun deleteRuleById(ruleId: Long)

    @Query("SELECT COUNT(*) FROM filter_rules")
    suspend fun getRuleCount(): Int
}

@Dao
interface ListEntryDao {
    @Query("SELECT * FROM list_entries ORDER BY createdAt DESC")
    fun observeAllEntries(): Flow<List<ListEntryEntity>>

    @Query("SELECT * FROM list_entries ORDER BY createdAt DESC")
    suspend fun getAllEntriesOnce(): List<ListEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: ListEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<ListEntryEntity>)

    @Query("UPDATE list_entries SET hitCount = hitCount + 1 WHERE id = :entryId")
    suspend fun incrementHitCount(entryId: Long)

    @Query("DELETE FROM list_entries WHERE id = :entryId")
    suspend fun deleteEntryById(entryId: Long)
}
