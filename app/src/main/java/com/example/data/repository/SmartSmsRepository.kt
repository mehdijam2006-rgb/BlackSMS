package com.example.data.repository

import com.example.data.local.AppSettings
import com.example.data.local.DeliveryStatus
import com.example.data.local.FilterRuleDao
import com.example.data.local.FilterRuleEntity
import com.example.data.local.ListEntryDao
import com.example.data.local.ListEntryEntity
import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.data.local.SettingsPreferences
import com.example.data.local.ThreadSummary
import com.example.domain.engine.BlockedExample
import com.example.domain.engine.SmsFilterEngine
import com.example.domain.model.BlockSource
import com.example.domain.model.FilterDecision
import com.example.domain.model.ListEntryType
import com.example.domain.model.ListType
import com.example.domain.model.MatchType
import com.example.domain.model.PersianTextNormalizer
import com.example.domain.model.RuleTarget
import com.example.domain.model.SenderClassifier
import com.example.domain.model.SenderType
import com.example.domain.model.SmartFilterLevel
import com.example.domain.model.SmsPartCalculator
import com.example.sms.DeviceSmsRecord
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

data class ProcessedMessageResult(
    val messageId: Long,
    val decision: FilterDecision,
    val senderDisplay: String,
    val body: String
)

class SmartSmsRepository(
    private val messageDao: MessageDao,
    private val filterRuleDao: FilterRuleDao,
    private val listEntryDao: ListEntryDao,
    private val settingsPrefs: SettingsPreferences
) {

    val inboxThreads: Flow<List<ThreadSummary>> = messageDao.observeInboxMessages().map { messages ->
        messages
            .groupBy { it.threadKey }
            .map { (threadKey, threadMessages) ->
                val sorted = threadMessages.sortedWith(
                    compareByDescending<MessageEntity> { it.timestamp }.thenByDescending { it.id }
                )
                val latest = sorted.first()
                ThreadSummary(
                    threadKey = threadKey,
                    senderDisplay = SenderClassifier.displaySender(latest.senderRaw),
                    senderRaw = latest.senderRaw,
                    senderType = latest.senderType,
                    lastBody = latest.body.ifBlank { if (latest.isMms) "[MMS]" else "" },
                    lastTimestamp = latest.timestamp,
                    unreadCount = threadMessages.count { !it.isRead && !it.isOutgoing },
                    messageCount = threadMessages.size,
                    isArchived = threadMessages.all { it.isArchived },
                    isPinned = threadMessages.any { it.isPinned },
                    lastSimSlot = latest.simSlot,
                    hasMms = threadMessages.any { it.isMms }
                )
            }
            .sortedWith(
                compareByDescending<ThreadSummary> { it.isPinned }
                    .thenByDescending { it.lastTimestamp }
            )
    }

    val blockedMessages: Flow<List<MessageEntity>> = messageDao.observeBlockedMessages()
    val unreadInboxCount: Flow<Int> = messageDao.observeUnreadInboxCount()
    val blockedCount: Flow<Int> = messageDao.observeBlockedCount()
    val allRules: Flow<List<FilterRuleEntity>> = filterRuleDao.observeAllRules()
    val allListEntries: Flow<List<ListEntryEntity>> = listEntryDao.observeAllEntries()
    val settings: Flow<AppSettings> = settingsPrefs.settingsFlow

    fun observeThreadMessages(threadKey: String): Flow<List<MessageEntity>> =
        messageDao.observeThreadMessages(threadKey)

    /**
     * Initializes default filter switches only — NO demo/sample messages are seeded.
     */
    suspend fun ensureSeeded() {
        val currentSettings = settingsPrefs.getSettingsOnce()
        if (filterRuleDao.getRuleCount() > 0) {
            if (!currentSettings.hasSeededInitialData) settingsPrefs.markSeeded()
            return
        }

        val starterRules = listOf(
            FilterRuleEntity(
                name = "auto_promo_senders",
                ruleTarget = RuleTarget.SENDER,
                matchType = MatchType.BRAND_OR_PROMO_SENDER,
                pattern = "AD-SMS, AD-, PROMO, TABLIGH, OFFER",
                exceptionPattern = "کد تایید, رمز پویا",
                priority = 95,
                isEnabled = true
            ),
            FilterRuleEntity(
                name = "auto_unknown_senders",
                ruleTarget = RuleTarget.SENDER,
                matchType = MatchType.UNKNOWN_OR_PRIVATE,
                pattern = "",
                priority = 90,
                isEnabled = true
            )
        )
        filterRuleDao.insertRules(starterRules)
        settingsPrefs.markSeeded()
    }

    /**
     * Syncs real SMS/MMS records read from the device's Telephony provider.
     * Deduplicates by BOTH systemSmsId and (dedupHash + isOutgoing + ±120s window)
     * so sent/received messages NEVER appear twice.
     */
    suspend fun syncDeviceRecords(records: List<DeviceSmsRecord>): Int {
        if (records.isEmpty()) return 0
        val existingIds = messageDao.getAllSyncedSystemIds().toHashSet()
        val rules = filterRuleDao.getAllRulesOnce()
        val listEntries = listEntryDao.getAllEntriesOnce()
        val currentSettings = settingsPrefs.getSettingsOnce()
        val recentBodies = messageDao.getRecentIncomingBodies().map { it.body }
        val learnedBlocked = messageDao.getRecentBlockedMessages()
            .map { BlockedExample(it.senderRaw, it.body) }
            .toMutableList()

        var importedCount = 0
        for (rec in records.sortedBy { it.timestamp }) {
            if (rec.systemId in existingIds) continue
            val threadKey = SenderClassifier.extractThreadKey(rec.senderRaw)
            val senderType = SenderClassifier.classify(rec.senderRaw)
            val hash = computeDedupHash(threadKey, rec.body)

            // Secondary dedup check: if already inserted via SmsDeliverReceiver or sendSms, attach systemSmsId instead of duplicating
            val existingMatch = messageDao.findMatchingMessage(
                dedupHash = hash,
                isOutgoing = rec.isOutgoing,
                minTimestamp = rec.timestamp - 120_000L,
                maxTimestamp = rec.timestamp + 120_000L
            )
            if (existingMatch != null) {
                if (existingMatch.systemSmsId == null) {
                    messageDao.attachSystemSmsId(existingMatch.id, rec.systemId)
                }
                existingIds.add(rec.systemId)
                continue
            }

            if (rec.isOutgoing) {
                messageDao.insertMessage(
                    MessageEntity(
                        systemSmsId = rec.systemId,
                        threadKey = threadKey,
                        senderRaw = rec.senderRaw.trim(),
                        senderType = senderType,
                        body = rec.body,
                        timestamp = rec.timestamp,
                        isOutgoing = true,
                        isRead = true,
                        isArchived = false,
                        isBlocked = false,
                        deliveryStatus = DeliveryStatus.SENT,
                        partCount = SmsPartCalculator.calculateParts(rec.body),
                        isMms = rec.isMms,
                        attachmentUri = rec.attachmentUri,
                        dedupHash = hash
                    )
                )
            } else {
                val decision = SmsFilterEngine.evaluate(
                    senderRaw = rec.senderRaw,
                    body = rec.body,
                    rules = rules,
                    listEntries = listEntries,
                    smartLevel = currentSettings.smartFilterLevel,
                    recentIncomingBodies = recentBodies,
                    learnedBlockedExamples = learnedBlocked
                )
                messageDao.insertMessage(
                    MessageEntity(
                        systemSmsId = rec.systemId,
                        threadKey = threadKey,
                        senderRaw = rec.senderRaw.trim(),
                        senderType = senderType,
                        body = rec.body,
                        timestamp = rec.timestamp,
                        isOutgoing = false,
                        isRead = rec.isRead,
                        isArchived = false,
                        isBlocked = decision.shouldBlock,
                        blockSource = decision.blockSource,
                        blockReason = if (decision.shouldBlock) decision.reasonFa else null,
                        matchedRuleId = decision.matchedRuleId,
                        smartScore = decision.smartAnalysis.score,
                        smartSignals = decision.smartAnalysis.signals.joinToString(" | "),
                        deliveryStatus = DeliveryStatus.NONE,
                        partCount = SmsPartCalculator.calculateParts(rec.body),
                        isMms = rec.isMms,
                        attachmentUri = rec.attachmentUri,
                        dedupHash = hash
                    )
                )
                if (decision.shouldBlock) {
                    learnedBlocked.add(0, BlockedExample(rec.senderRaw, rec.body))
                }
                decision.matchedRuleId?.let { filterRuleDao.incrementBlockedCount(it) }
                decision.matchedListEntryId?.let { listEntryDao.incrementHitCount(it) }
            }
            existingIds.add(rec.systemId)
            importedCount++
        }
        return importedCount
    }

    suspend fun evaluatePreview(senderRaw: String, body: String): FilterDecision {
        val rules = filterRuleDao.getAllRulesOnce()
        val listEntries = listEntryDao.getAllEntriesOnce()
        val settings = settingsPrefs.getSettingsOnce()
        val recentBodies = messageDao.getRecentIncomingBodies().map { it.body }
        val learnedBlocked = messageDao.getRecentBlockedMessages().map { BlockedExample(it.senderRaw, it.body) }
        return SmsFilterEngine.evaluate(
            senderRaw = senderRaw,
            body = body,
            rules = rules,
            listEntries = listEntries,
            smartLevel = settings.smartFilterLevel,
            recentIncomingBodies = recentBodies,
            learnedBlockedExamples = learnedBlocked
        )
    }

    suspend fun processAndStoreIncomingMessage(
        senderRaw: String,
        body: String,
        isMms: Boolean = false,
        attachmentUri: String? = null,
        customTimestamp: Long? = null,
        systemSmsId: Long? = null
    ): ProcessedMessageResult? {
        val trimmedBody = body.trim()
        if (trimmedBody.isEmpty() && attachmentUri == null) return null

        val ts = customTimestamp ?: System.currentTimeMillis()
        val threadKey = SenderClassifier.extractThreadKey(senderRaw)
        val senderType = SenderClassifier.classify(senderRaw)
        val hash = computeDedupHash(threadKey, trimmedBody)

        if (messageDao.countRecentDuplicate(hash, ts - 60_000L) > 0) {
            return null
        }

        val rules = filterRuleDao.getAllRulesOnce()
        val listEntries = listEntryDao.getAllEntriesOnce()
        val settings = settingsPrefs.getSettingsOnce()
        val recentBodies = messageDao.getRecentIncomingBodies().map { it.body }
        val learnedBlocked = messageDao.getRecentBlockedMessages().map { BlockedExample(it.senderRaw, it.body) }

        val decision = SmsFilterEngine.evaluate(
            senderRaw = senderRaw,
            body = trimmedBody,
            rules = rules,
            listEntries = listEntries,
            smartLevel = settings.smartFilterLevel,
            recentIncomingBodies = recentBodies,
            learnedBlockedExamples = learnedBlocked
        )

        val message = MessageEntity(
            systemSmsId = systemSmsId,
            threadKey = threadKey,
            senderRaw = senderRaw.trim(),
            senderType = senderType,
            body = trimmedBody,
            timestamp = ts,
            isOutgoing = false,
            isRead = false,
            isArchived = false,
            isBlocked = decision.shouldBlock,
            blockSource = decision.blockSource,
            blockReason = if (decision.shouldBlock) decision.reasonFa else null,
            matchedRuleId = decision.matchedRuleId,
            smartScore = decision.smartAnalysis.score,
            smartSignals = decision.smartAnalysis.signals.joinToString(" | "),
            simSlot = 1,
            deliveryStatus = DeliveryStatus.NONE,
            partCount = SmsPartCalculator.calculateParts(trimmedBody),
            isMms = isMms || attachmentUri != null,
            attachmentUri = attachmentUri,
            dedupHash = hash
        )
        val insertedId = messageDao.insertMessage(message)

        decision.matchedRuleId?.let { filterRuleDao.incrementBlockedCount(it) }
        decision.matchedListEntryId?.let { listEntryDao.incrementHitCount(it) }

        return ProcessedMessageResult(
            messageId = insertedId,
            decision = decision,
            senderDisplay = SenderClassifier.displaySender(senderRaw),
            body = trimmedBody
        )
    }

    suspend fun recordOutgoingMessage(
        recipientRaw: String,
        body: String,
        attachmentUri: String? = null,
        customTimestamp: Long? = null,
        simSlot: Int = 1,
        deliveryStatus: DeliveryStatus = DeliveryStatus.SENDING,
        systemSmsId: Long? = null
    ): Long {
        val trimmedBody = body.trim()
        if (trimmedBody.isEmpty() && attachmentUri == null) return -1L
        val ts = customTimestamp ?: System.currentTimeMillis()
        val threadKey = SenderClassifier.extractThreadKey(recipientRaw)
        val senderType = SenderClassifier.classify(recipientRaw)

        return messageDao.insertMessage(
            MessageEntity(
                systemSmsId = systemSmsId,
                threadKey = threadKey,
                senderRaw = recipientRaw.trim(),
                senderType = senderType,
                body = trimmedBody,
                timestamp = ts,
                isOutgoing = true,
                isRead = true,
                isArchived = false,
                isBlocked = false,
                simSlot = simSlot,
                deliveryStatus = deliveryStatus,
                partCount = SmsPartCalculator.calculateParts(trimmedBody),
                isMms = attachmentUri != null,
                attachmentUri = attachmentUri,
                dedupHash = computeDedupHash(threadKey, trimmedBody)
            )
        )
    }

    suspend fun attachSystemSmsId(messageId: Long, systemSmsId: Long) {
        if (messageId > 0L) {
            messageDao.attachSystemSmsId(messageId, systemSmsId)
        }
    }

    suspend fun updateDeliveryStatus(messageId: Long, status: DeliveryStatus) {
        if (messageId > 0L) {
            messageDao.updateDeliveryStatus(messageId, status)
        }
    }

    suspend fun restoreBlockedMessage(messageId: Long, alsoWhitelistSender: Boolean) {
        val msg = messageDao.getMessageById(messageId) ?: return
        messageDao.restoreBlockedMessage(messageId)
        if (alsoWhitelistSender && msg.senderRaw.isNotBlank()) {
            addSenderToWhitelist(msg.senderRaw)
        }
    }

    suspend fun addSenderToWhitelist(senderRaw: String) {
        val trimmed = senderRaw.trim()
        if (trimmed.isEmpty()) return
        val type = SenderClassifier.classify(trimmed)
        val entryType = if (type == SenderType.ALPHANUMERIC) {
            ListEntryType.ALPHANUMERIC_SENDER
        } else {
            ListEntryType.PHONE_NUMBER
        }
        val existing = listEntryDao.getAllEntriesOnce().any {
            it.listType == ListType.WHITELIST && it.value.equals(trimmed, ignoreCase = true)
        }
        if (!existing) {
            listEntryDao.insertEntry(
                ListEntryEntity(
                    listType = ListType.WHITELIST,
                    entryType = entryType,
                    value = trimmed
                )
            )
        }
        val threadKey = SenderClassifier.extractThreadKey(trimmed)
        messageDao.restoreAllFromThread(threadKey)
    }

    suspend fun addSenderToBlacklist(
        senderRaw: String,
        moveExistingToBlocked: Boolean = true,
        blockReason: String? = null
    ) {
        val trimmed = senderRaw.trim()
        if (trimmed.isEmpty()) return
        val type = SenderClassifier.classify(trimmed)
        val entryType = if (type == SenderType.ALPHANUMERIC) {
            ListEntryType.ALPHANUMERIC_SENDER
        } else {
            ListEntryType.PHONE_NUMBER
        }
        val existing = listEntryDao.getAllEntriesOnce().any {
            it.listType == ListType.BLACKLIST && it.value.equals(trimmed, ignoreCase = true)
        }
        if (!existing) {
            listEntryDao.insertEntry(
                ListEntryEntity(
                    listType = ListType.BLACKLIST,
                    entryType = entryType,
                    value = trimmed
                )
            )
        }
        if (moveExistingToBlocked) {
            val threadKey = SenderClassifier.extractThreadKey(trimmed)
            val reason = blockReason ?: "گزارش اسپم و مسدودسازی فرستنده ($trimmed)"
            messageDao.blockThreadMessages(threadKey, BlockSource.BLACKLIST, reason)
        }
    }

    suspend fun blockSingleMessage(messageId: Long, alsoBlacklistSender: Boolean) {
        val msg = messageDao.getMessageById(messageId) ?: return
        val reason = if (alsoBlacklistSender) {
            "گزارش اسپم و مسدودسازی فرستنده (${msg.senderRaw})"
        } else {
            "انتقال دستی پیام به اسپم"
        }
        messageDao.blockSingleMessage(messageId, BlockSource.BLACKLIST, reason)
        if (alsoBlacklistSender && msg.senderRaw.isNotBlank()) {
            addSenderToBlacklist(msg.senderRaw, moveExistingToBlocked = true, blockReason = reason)
        }
    }

    suspend fun addBlockedPhrase(phraseRaw: String) {
        val trimmed = phraseRaw.trim()
        if (trimmed.isEmpty()) return
        val existing = listEntryDao.getAllEntriesOnce().any {
            it.listType == ListType.BLACKLIST &&
                (it.entryType == ListEntryType.PHRASE || it.entryType == ListEntryType.WORD) &&
                it.value.equals(trimmed, ignoreCase = true)
        }
        if (!existing) {
            listEntryDao.insertEntry(
                ListEntryEntity(
                    listType = ListType.BLACKLIST,
                    entryType = ListEntryType.PHRASE,
                    value = trimmed
                )
            )
        }
    }

    suspend fun toggleBuiltInRule(matchType: MatchType, enabled: Boolean) {
        val existing = filterRuleDao.getAllRulesOnce().firstOrNull { it.matchType == matchType }
        if (existing != null) {
            filterRuleDao.setRuleEnabled(existing.id, enabled)
        } else {
            filterRuleDao.insertRule(
                FilterRuleEntity(
                    name = matchType.name,
                    ruleTarget = matchType.target,
                    matchType = matchType,
                    pattern = if (matchType == MatchType.BRAND_OR_PROMO_SENDER) "AD-SMS, AD-, PROMO, TABLIGH, OFFER" else "",
                    priority = 90,
                    isEnabled = enabled
                )
            )
        }
    }

    suspend fun deleteBlockedMessages(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            messageDao.deleteBlockedMessagesByIds(ids)
        }
    }

    suspend fun deleteSingleMessage(messageId: Long) = messageDao.deleteSingleMessage(messageId)
    suspend fun markThreadRead(threadKey: String) = messageDao.markThreadRead(threadKey)
    suspend fun markAllInboxRead() = messageDao.markAllInboxRead()
    suspend fun setThreadReadState(threadKey: String, isRead: Boolean) = messageDao.setThreadReadState(threadKey, isRead)
    suspend fun setThreadArchived(threadKey: String, archived: Boolean) = messageDao.setThreadArchived(threadKey, archived)
    suspend fun setThreadPinned(threadKey: String, pinned: Boolean) = messageDao.setThreadPinned(threadKey, pinned)
    suspend fun deleteInboxThread(threadKey: String) = messageDao.deleteInboxThread(threadKey)
    suspend fun deleteListEntry(entryId: Long) = listEntryDao.deleteEntryById(entryId)

    suspend fun setSmartFilterLevel(level: SmartFilterLevel) = settingsPrefs.setSmartFilterLevel(level)
    suspend fun setAppLanguage(language: AppLanguage) = settingsPrefs.setAppLanguage(language)
    suspend fun setNotificationsEnabled(enabled: Boolean) = settingsPrefs.setNotificationsEnabled(enabled)
    suspend fun setDeliveryReportsEnabled(enabled: Boolean) = settingsPrefs.setDeliveryReportsEnabled(enabled)
    suspend fun setLastSeenBlockedCount(count: Int) = settingsPrefs.setLastSeenBlockedCount(count)
    suspend fun setThemeMode(mode: AppThemeMode) = settingsPrefs.setThemeMode(mode)

    private fun computeDedupHash(threadKey: String, body: String): String {
        val raw = "$threadKey|${PersianTextNormalizer.normalizePersian(body)}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }
}
