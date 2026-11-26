package com.example.ui.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppSettings
import com.example.data.local.DeliveryStatus
import com.example.data.local.ListEntryEntity
import com.example.data.local.MessageEntity
import com.example.data.local.SettingsPreferences
import com.example.data.local.SmartSmsDatabase
import com.example.data.local.ThreadSummary
import com.example.data.repository.SmartSmsRepository
import com.example.domain.model.BlockSource
import com.example.domain.model.ListEntryType
import com.example.domain.model.ListType
import com.example.domain.model.MatchType
import com.example.domain.model.SenderType
import com.example.domain.model.SmartFilterLevel
import com.example.sms.DefaultSmsHelper
import com.example.sms.DeviceContact
import com.example.sms.DeviceContactsManager
import com.example.sms.DeviceSmsManager
import com.example.sms.ResolvedContact
import com.example.sms.SimCardInfo
import com.example.ui.components.SystemSmsLauncher
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.PersianStrings
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class InboxFilterTab { ALL, UNREAD, ARCHIVED }
enum class BlockedFilterTab { ALL, SMART, MANUAL, ALPHANUMERIC }

class SmartSmsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = SmartSmsDatabase.getInstance(application)
    private val settingsPrefs = SettingsPreferences(application)
    private val repository = SmartSmsRepository(
        messageDao = database.messageDao(),
        filterRuleDao = database.filterRuleDao(),
        listEntryDao = database.listEntryDao(),
        settingsPrefs = settingsPrefs
    )

    private val _inboxSearch = MutableStateFlow("")
    val inboxSearch: StateFlow<String> = _inboxSearch.asStateFlow()

    private val _inboxFilter = MutableStateFlow(InboxFilterTab.ALL)
    val inboxFilter: StateFlow<InboxFilterTab> = _inboxFilter.asStateFlow()

    private val _selectedThreadKeys = MutableStateFlow<Set<String>>(emptySet())
    val selectedThreadKeys: StateFlow<Set<String>> = _selectedThreadKeys.asStateFlow()

    private val _blockedSearch = MutableStateFlow("")
    val blockedSearch: StateFlow<String> = _blockedSearch.asStateFlow()

    private val _blockedFilter = MutableStateFlow(BlockedFilterTab.ALL)
    val blockedFilter: StateFlow<BlockedFilterTab> = _blockedFilter.asStateFlow()

    private val _selectedBlockedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedBlockedIds: StateFlow<Set<Long>> = _selectedBlockedIds.asStateFlow()

    private val _hasSmsPermissions = MutableStateFlow(DefaultSmsHelper.hasAllSmsPermissions(appContext))
    val hasSmsPermissions: StateFlow<Boolean> = _hasSmsPermissions.asStateFlow()

    private val _hasContactsPermission = MutableStateFlow(DefaultSmsHelper.hasContactsPermission(appContext))
    val hasContactsPermission: StateFlow<Boolean> = _hasContactsPermission.asStateFlow()

    private val _isDefaultSmsApp = MutableStateFlow(DefaultSmsHelper.isDefaultSmsApp(appContext))
    val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _contacts = MutableStateFlow<List<DeviceContact>>(emptyList())
    val contacts: StateFlow<List<DeviceContact>> = _contacts.asStateFlow()

    private val _activeSims = MutableStateFlow<List<SimCardInfo>>(emptyList())
    val activeSims: StateFlow<List<SimCardInfo>> = _activeSims.asStateFlow()

    val contactLookup: StateFlow<Map<String, ResolvedContact>> = _contacts
        .map { DeviceContactsManager.buildLookupMap(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private var smsObserverRegistered = false
    private val smsContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            syncDeviceMessages(notifyUser = false)
        }
    }

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    val unreadInboxCount: StateFlow<Int> = repository.unreadInboxCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val blockedCount: StateFlow<Int> = repository.blockedCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val unseenBlockedCount: StateFlow<Int> = combine(
        repository.blockedCount,
        settings
    ) { count, appSettings ->
        (count - appSettings.lastSeenBlockedCount).coerceAtLeast(0)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val blockedSenderEntries: StateFlow<List<ListEntryEntity>> = repository.allListEntries
        .map { list ->
            list.filter {
                it.listType == ListType.BLACKLIST &&
                    (it.entryType == ListEntryType.PHONE_NUMBER || it.entryType == ListEntryType.ALPHANUMERIC_SENDER)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedPhraseEntries: StateFlow<List<ListEntryEntity>> = repository.allListEntries
        .map { list ->
            list.filter {
                it.listType == ListType.BLACKLIST &&
                    (it.entryType == ListEntryType.PHRASE || it.entryType == ListEntryType.WORD || it.entryType == ListEntryType.PATTERN_REGEX)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allowedSenderEntries: StateFlow<List<ListEntryEntity>> = repository.allListEntries
        .map { list -> list.filter { it.listType == ListType.WHITELIST } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val autoBlockUnknown: StateFlow<Boolean> = repository.allRules
        .map { rules -> rules.firstOrNull { it.matchType == MatchType.UNKNOWN_OR_PRIVATE }?.isEnabled ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoBlockPromoText: StateFlow<Boolean> = repository.allRules
        .map { rules -> rules.firstOrNull { it.matchType == MatchType.BRAND_OR_PROMO_SENDER }?.isEnabled ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val filteredThreads: StateFlow<List<ThreadSummary>> = combine(
        repository.inboxThreads,
        contactLookup,
        _inboxSearch,
        _inboxFilter
    ) { threads, lookup, query, tab ->
        val trimmedQuery = query.trim().lowercase()
        threads
            .map { t ->
                val resolved = DeviceContactsManager.resolveSender(t.senderRaw, lookup)
                if (resolved != null) {
                    t.copy(contactName = resolved.name, contactPhotoUri = resolved.photoUri)
                } else {
                    t
                }
            }
            .filter { thread ->
                val tabMatch = when (tab) {
                    InboxFilterTab.ALL -> !thread.isArchived
                    InboxFilterTab.UNREAD -> !thread.isArchived && thread.unreadCount > 0
                    InboxFilterTab.ARCHIVED -> thread.isArchived
                }
                val queryMatch = if (trimmedQuery.isEmpty()) {
                    true
                } else {
                    thread.displayTitle.lowercase().contains(trimmedQuery) ||
                        thread.senderDisplay.lowercase().contains(trimmedQuery) ||
                        thread.lastBody.lowercase().contains(trimmedQuery) ||
                        thread.threadKey.lowercase().contains(trimmedQuery)
                }
                tabMatch && queryMatch
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredBlockedMessages: StateFlow<List<MessageEntity>> = combine(
        repository.blockedMessages,
        _blockedSearch,
        _blockedFilter
    ) { messages, query, tab ->
        val trimmedQuery = query.trim().lowercase()
        messages.filter { msg ->
            val tabMatch = when (tab) {
                BlockedFilterTab.ALL -> true
                BlockedFilterTab.SMART -> msg.blockSource == BlockSource.SMART_ENGINE
                BlockedFilterTab.MANUAL -> msg.blockSource == BlockSource.MANUAL_RULE || msg.blockSource == BlockSource.BLACKLIST
                BlockedFilterTab.ALPHANUMERIC -> msg.senderType == SenderType.ALPHANUMERIC || msg.senderType == SenderType.PRIVATE_OR_INVALID
            }
            val queryMatch = if (trimmedQuery.isEmpty()) {
                true
            } else {
                msg.senderRaw.lowercase().contains(trimmedQuery) ||
                    msg.body.lowercase().contains(trimmedQuery) ||
                    (msg.blockReason?.lowercase()?.contains(trimmedQuery) == true)
            }
            tabMatch && queryMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            refreshSystemStateAndSync(notifyUser = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (smsObserverRegistered) {
            try {
                appContext.contentResolver.unregisterContentObserver(smsContentObserver)
            } catch (_: Exception) {
            }
        }
    }

    private fun strings() = if (settings.value.appLanguage == AppLanguage.EN) EnglishStrings else PersianStrings

    fun resolveContactForSender(senderRaw: String): ResolvedContact? =
        DeviceContactsManager.resolveSender(senderRaw, contactLookup.value)

    fun refreshSystemStateAndSync(notifyUser: Boolean = false) {
        _hasSmsPermissions.value = DefaultSmsHelper.hasAllSmsPermissions(appContext)
        _hasContactsPermission.value = DefaultSmsHelper.hasContactsPermission(appContext)
        _isDefaultSmsApp.value = DefaultSmsHelper.isDefaultSmsApp(appContext)
        _activeSims.value = DeviceSmsManager.getActiveSims(appContext)

        if (_hasContactsPermission.value) {
            loadContacts()
        }
        if (DefaultSmsHelper.hasReadSmsPermission(appContext)) {
            registerSmsObserverIfNeeded()
            syncDeviceMessages(notifyUser)
        }
    }

    private fun registerSmsObserverIfNeeded() {
        if (smsObserverRegistered) return
        try {
            appContext.contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true,
                smsContentObserver
            )
            smsObserverRegistered = true
        } catch (_: Exception) {
        }
    }

    fun loadContacts() {
        if (!DefaultSmsHelper.hasContactsPermission(appContext)) return
        viewModelScope.launch {
            _contacts.value = DeviceContactsManager.loadContacts(appContext)
        }
    }

    fun syncDeviceMessages(notifyUser: Boolean = true) {
        if (_isSyncing.value || !DefaultSmsHelper.hasReadSmsPermission(appContext)) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val records = DeviceSmsManager.readDeviceMessages(appContext)
                val imported = repository.syncDeviceRecords(records)
                if (notifyUser && imported > 0) {
                    _snackbarMessages.emit(strings().msgSyncedCount(imported))
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun observeThreadMessages(threadKey: String): Flow<List<MessageEntity>> =
        repository.observeThreadMessages(threadKey)

    fun setInboxSearch(query: String) { _inboxSearch.value = query }
    fun setInboxFilter(tab: InboxFilterTab) { _inboxFilter.value = tab }
    fun setBlockedSearch(query: String) { _blockedSearch.value = query }
    fun setBlockedFilter(tab: BlockedFilterTab) { _blockedFilter.value = tab }

    fun toggleThreadSelection(threadKey: String) {
        val current = _selectedThreadKeys.value
        _selectedThreadKeys.value = if (threadKey in current) current - threadKey else current + threadKey
    }

    fun clearThreadSelection() { _selectedThreadKeys.value = emptySet() }

    fun toggleBlockedSelection(messageId: Long) {
        val current = _selectedBlockedIds.value
        _selectedBlockedIds.value = if (messageId in current) current - messageId else current + messageId
    }

    fun selectAllBlocked(ids: List<Long>) { _selectedBlockedIds.value = ids.toSet() }
    fun clearBlockedSelection() { _selectedBlockedIds.value = emptySet() }

    fun sendSms(
        recipientRaw: String,
        body: String,
        attachmentUri: String? = null,
        selectedSim: SimCardInfo? = null
    ) {
        viewModelScope.launch {
            val simSlotNumber = (selectedSim?.slotIndex ?: 0) + 1
            val messageId = repository.recordOutgoingMessage(
                recipientRaw = recipientRaw,
                body = body,
                attachmentUri = attachmentUri,
                simSlot = simSlotNumber,
                deliveryStatus = DeliveryStatus.SENDING
            )
            if (messageId <= 0L) return@launch

            val deliveryReports = settings.value.deliveryReportsEnabled
            val outcome = withContext(Dispatchers.IO) {
                DeviceSmsManager.sendRealSms(
                    context = appContext,
                    recipient = recipientRaw,
                    body = body,
                    messageId = messageId,
                    subscriptionId = selectedSim?.subscriptionId,
                    requestDeliveryReport = deliveryReports
                )
            }
            if (outcome.systemSmsId != null) {
                repository.attachSystemSmsId(messageId, outcome.systemSmsId)
            }
            if (outcome.sentDirectly) {
                _snackbarMessages.emit(strings().msgSentReal)
            } else {
                repository.updateDeliveryStatus(messageId, DeliveryStatus.FAILED)
                SystemSmsLauncher.launchSystemSmsComposer(appContext, recipientRaw, body)
                _snackbarMessages.emit(strings().msgSendFailed)
            }
        }
    }

    fun markThreadRead(threadKey: String, senderRaw: String = "") {
        viewModelScope.launch {
            repository.markThreadRead(threadKey)
            if (senderRaw.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    DeviceSmsManager.markSenderReadInSystem(appContext, senderRaw)
                }
            }
        }
    }

    fun markAllInboxRead() {
        viewModelScope.launch {
            repository.markAllInboxRead()
            _snackbarMessages.emit(strings().msgAllMarkedRead)
        }
    }

    fun setThreadReadState(threadKey: String, isRead: Boolean) {
        viewModelScope.launch { repository.setThreadReadState(threadKey, isRead) }
    }

    fun setThreadArchived(threadKey: String, archived: Boolean) {
        viewModelScope.launch {
            repository.setThreadArchived(threadKey, archived)
            _snackbarMessages.emit(if (archived) strings().msgArchived else strings().msgUnarchived)
        }
    }

    fun setThreadPinned(threadKey: String, pinned: Boolean) {
        viewModelScope.launch {
            repository.setThreadPinned(threadKey, pinned)
            _snackbarMessages.emit(if (pinned) strings().msgPinned else strings().msgUnpinned)
        }
    }

    fun deleteInboxThread(threadKey: String) {
        viewModelScope.launch {
            repository.deleteInboxThread(threadKey)
            _snackbarMessages.emit(strings().msgThreadDeleted)
        }
    }

    fun deleteSingleMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteSingleMessage(messageId)
            _snackbarMessages.emit(strings().msgMessageDeleted)
        }
    }

    fun blockSingleMessage(messageId: Long, alsoBlacklistSender: Boolean) {
        viewModelScope.launch {
            repository.blockSingleMessage(messageId, alsoBlacklistSender)
            _snackbarMessages.emit(strings().msgMovedToSpam)
        }
    }

    fun batchArchiveSelectedThreads(archive: Boolean = true) {
        val keys = _selectedThreadKeys.value.toList()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            keys.forEach { repository.setThreadArchived(it, archive) }
            _selectedThreadKeys.value = emptySet()
            _snackbarMessages.emit(strings().msgArchived)
        }
    }

    fun batchMarkSelectedRead() {
        val keys = _selectedThreadKeys.value.toList()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            keys.forEach { repository.markThreadRead(it) }
            _selectedThreadKeys.value = emptySet()
        }
    }

    fun batchBlockSelectedThreads(threads: List<ThreadSummary>) {
        val keys = _selectedThreadKeys.value
        if (keys.isEmpty()) return
        viewModelScope.launch {
            val targets = threads.filter { it.threadKey in keys }
            targets.forEach { thread ->
                repository.addSenderToBlacklist(thread.senderRaw.ifBlank { thread.senderDisplay }, moveExistingToBlocked = true)
            }
            _selectedThreadKeys.value = emptySet()
            _snackbarMessages.emit(strings().msgMovedToSpam)
        }
    }

    fun batchDeleteSelectedThreads() {
        val keys = _selectedThreadKeys.value.toList()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            keys.forEach { repository.deleteInboxThread(it) }
            _selectedThreadKeys.value = emptySet()
            _snackbarMessages.emit(strings().msgThreadDeleted)
        }
    }

    fun restoreBlockedMessage(messageId: Long, alsoWhitelistSender: Boolean) {
        viewModelScope.launch {
            repository.restoreBlockedMessage(messageId, alsoWhitelistSender)
            val s = strings()
            _snackbarMessages.emit(if (alsoWhitelistSender) s.msgRestoredAndAllowed else s.msgRestored)
        }
    }

    fun whitelistSender(senderRaw: String) {
        viewModelScope.launch {
            repository.addSenderToWhitelist(senderRaw)
            _snackbarMessages.emit(strings().msgSenderAllowed(senderRaw))
        }
    }

    fun blockSenderQuick(senderRaw: String) {
        viewModelScope.launch {
            repository.addSenderToBlacklist(senderRaw, moveExistingToBlocked = true)
            _snackbarMessages.emit(strings().msgSenderBlockedAndMoved(senderRaw))
        }
    }

    fun addBlockedPhrase(phrase: String) {
        viewModelScope.launch {
            repository.addBlockedPhrase(phrase)
            _snackbarMessages.emit(strings().msgFilterAdded)
        }
    }

    fun deleteFilterEntry(entryId: Long) {
        viewModelScope.launch {
            repository.deleteListEntry(entryId)
            _snackbarMessages.emit(strings().msgFilterRemoved)
        }
    }

    fun toggleAutoBlockUnknown(enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleBuiltInRule(MatchType.UNKNOWN_OR_PRIVATE, enabled)
        }
    }

    fun toggleAutoBlockPromoText(enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleBuiltInRule(MatchType.BRAND_OR_PROMO_SENDER, enabled)
        }
    }

    fun deleteSelectedBlockedMessages() {
        val ids = _selectedBlockedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteBlockedMessages(ids)
            _selectedBlockedIds.value = emptySet()
            _snackbarMessages.emit(strings().msgBlockedDeletedMany(ids.size))
        }
    }

    fun deleteSingleBlockedMessage(id: Long) {
        viewModelScope.launch {
            repository.deleteBlockedMessages(listOf(id))
            _selectedBlockedIds.value -= id
            _snackbarMessages.emit(strings().msgBlockedDeletedOne)
        }
    }

    fun setSmartFilterLevel(level: SmartFilterLevel) {
        viewModelScope.launch {
            repository.setSmartFilterLevel(level)
            val s = strings()
            val label = when (level) {
                SmartFilterLevel.OFF -> s.levelOffTitle
                SmartFilterLevel.NORMAL -> s.levelNormalTitle
                SmartFilterLevel.STRICT -> s.levelStrictTitle
            }
            _snackbarMessages.emit(s.msgLevelUpdated(label))
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setAppLanguage(language) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
    }

    fun setDeliveryReportsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setDeliveryReportsEnabled(enabled) }
    }

    fun markBlockedFolderSeen(count: Int = blockedCount.value) {
        viewModelScope.launch { repository.setLastSeenBlockedCount(count) }
    }
}
