package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.BlockSource
import com.example.domain.model.ListEntryType
import com.example.domain.model.ListType
import com.example.domain.model.MatchType
import com.example.domain.model.RuleTarget
import com.example.domain.model.SenderType

enum class DeliveryStatus {
    NONE,
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["threadKey"]),
        Index(value = ["isBlocked"]),
        Index(value = ["dedupHash"]),
        Index(value = ["systemSmsId"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemSmsId: Long? = null,
    val threadKey: String,
    val senderRaw: String,
    val senderType: SenderType,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean = false,
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    val isBlocked: Boolean = false,
    val blockSource: BlockSource? = null,
    val blockReason: String? = null,
    val matchedRuleId: Long? = null,
    val smartScore: Int = 0,
    val smartSignals: String = "",
    val simSlot: Int = 1,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,
    val partCount: Int = 1,
    val isMms: Boolean = false,
    val attachmentUri: String? = null,
    val dedupHash: String = ""
)

@Entity(tableName = "filter_rules")
data class FilterRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ruleTarget: RuleTarget,
    val matchType: MatchType,
    val pattern: String,
    val exceptionPattern: String = "",
    val caseSensitive: Boolean = false,
    val whitespaceSensitive: Boolean = false,
    val exactMatch: Boolean = false,
    val priority: Int = 10,
    val isEnabled: Boolean = true,
    val blockedCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "list_entries")
data class ListEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listType: ListType,
    val entryType: ListEntryType,
    val value: String,
    val note: String = "",
    val hitCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class ThreadSummary(
    val threadKey: String,
    val senderDisplay: String,
    val senderRaw: String,
    val senderType: SenderType,
    val contactName: String? = null,
    val contactPhotoUri: String? = null,
    val lastBody: String,
    val lastTimestamp: Long,
    val unreadCount: Int,
    val messageCount: Int,
    val isArchived: Boolean,
    val isPinned: Boolean,
    val lastSimSlot: Int,
    val hasMms: Boolean,
    val matchedSnippet: String? = null
) {
    val displayTitle: String
        get() = contactName?.takeIf { it.isNotBlank() } ?: senderDisplay
}
