package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.sms.DefaultSmsHelper
import com.example.ui.components.AppIcons
import com.example.ui.components.ComposeSmsSheet
import com.example.ui.components.PersianFormatters
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalStrings
import com.example.ui.i18n.PersianStrings
import com.example.ui.navigation.BlockedRoute
import com.example.ui.navigation.ConversationRoute
import com.example.ui.navigation.InboxRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.screens.BlockedScreen
import com.example.ui.screens.ConversationScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SmartSmsTheme
import com.example.ui.viewmodel.SmartSmsViewModel
import kotlinx.coroutines.launch

private data class TopNavItem<T : Any>(
    val route: T,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSmsApp(
    initialComposeRecipient: String? = null,
    initialComposeBody: String? = null,
    initialThreadKey: String? = null,
    onConsumeComposeIntent: () -> Unit = {},
    onConsumeOpenThreadIntent: () -> Unit = {},
    viewModel: SmartSmsViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadInboxCount.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedCount.collectAsStateWithLifecycle()
    val unseenBlockedCount by viewModel.unseenBlockedCount.collectAsStateWithLifecycle()
    val threads by viewModel.filteredThreads.collectAsStateWithLifecycle()
    val selectedThreadKeys by viewModel.selectedThreadKeys.collectAsStateWithLifecycle()
    val inboxSearch by viewModel.inboxSearch.collectAsStateWithLifecycle()
    val inboxFilter by viewModel.inboxFilter.collectAsStateWithLifecycle()
    val blockedMessages by viewModel.filteredBlockedMessages.collectAsStateWithLifecycle()
    val blockedSearch by viewModel.blockedSearch.collectAsStateWithLifecycle()
    val blockedFilter by viewModel.blockedFilter.collectAsStateWithLifecycle()
    val selectedBlockedIds by viewModel.selectedBlockedIds.collectAsStateWithLifecycle()
    val hasSmsPermissions by viewModel.hasSmsPermissions.collectAsStateWithLifecycle()
    val hasContactsPermission by viewModel.hasContactsPermission.collectAsStateWithLifecycle()
    val isDefaultSmsApp by viewModel.isDefaultSmsApp.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val activeSims by viewModel.activeSims.collectAsStateWithLifecycle()
    val contactLookup by viewModel.contactLookup.collectAsStateWithLifecycle()

    val blockedSenders by viewModel.blockedSenderEntries.collectAsStateWithLifecycle()
    val blockedPhrases by viewModel.blockedPhraseEntries.collectAsStateWithLifecycle()
    val allowedSenders by viewModel.allowedSenderEntries.collectAsStateWithLifecycle()
    val autoBlockUnknown by viewModel.autoBlockUnknown.collectAsStateWithLifecycle()
    val autoBlockPromoText by viewModel.autoBlockPromoText.collectAsStateWithLifecycle()

    val strings = if (settings.appLanguage == AppLanguage.EN) EnglishStrings else PersianStrings
    val layoutDirection = if (settings.appLanguage == AppLanguage.EN) LayoutDirection.Ltr else LayoutDirection.Rtl

    var showComposeSheet by remember { mutableStateOf(false) }
    var isInboxSearchOpen by rememberSaveable { mutableStateOf(false) }
    var composeRecipient by remember { mutableStateOf<String?>(null) }
    var composeBody by remember { mutableStateOf<String?>(null) }
    var promptedOnLaunch by rememberSaveable { mutableStateOf(false) }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            viewModel.refreshSystemStateAndSync(notifyUser = true)
        }
    )

    val requestDefaultSms: () -> Unit = {
        DefaultSmsHelper.createRequestDefaultSmsIntent(context)?.let { intent ->
            defaultSmsLauncher.launch(intent)
        }
    }

    val allPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            viewModel.refreshSystemStateAndSync(notifyUser = true)
            if (!DefaultSmsHelper.isDefaultSmsApp(context)) {
                requestDefaultSms()
            }
        }
    )

    val requestAllPermissions: () -> Unit = {
        allPermissionsLauncher.launch(DefaultSmsHelper.requiredRuntimePermissions())
    }

    // Prompt immediately on install/launch for SMS + Contacts permissions and Default SMS role
    LaunchedEffect(Unit) {
        if (!promptedOnLaunch) {
            promptedOnLaunch = true
            if (!DefaultSmsHelper.hasAllCorePermissions(context)) {
                requestAllPermissions()
            } else if (!DefaultSmsHelper.isDefaultSmsApp(context)) {
                requestDefaultSms()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemStateAndSync(notifyUser = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(initialComposeRecipient, initialComposeBody) {
        if (!initialComposeRecipient.isNullOrBlank() || !initialComposeBody.isNullOrBlank()) {
            composeRecipient = initialComposeRecipient
            composeBody = initialComposeBody
            showComposeSheet = true
            onConsumeComposeIntent()
        }
    }

    LaunchedEffect(initialThreadKey) {
        if (!initialThreadKey.isNullOrBlank()) {
            navController.navigate(ConversationRoute(initialThreadKey)) {
                launchSingleTop = true
            }
            onConsumeOpenThreadIntent()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navItems = remember(strings) {
        listOf(
            TopNavItem(InboxRoute, strings.navInbox, AppIcons.Inbox, AppIcons.Inbox, "nav_inbox"),
            TopNavItem(BlockedRoute, strings.navBlocked, AppIcons.Block, AppIcons.Block, "nav_blocked"),
            TopNavItem(SettingsRoute, strings.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val isConversationScreen = currentDest?.hasRoute(ConversationRoute::class) == true
    val isInboxScreen = currentDest?.hasRoute(InboxRoute::class) == true || currentDest == null
    val isBlockedScreen = currentDest?.hasRoute(BlockedRoute::class) == true

    LaunchedEffect(isInboxScreen) {
        if (!isInboxScreen && isInboxSearchOpen) {
            isInboxSearchOpen = false
        }
    }

    LaunchedEffect(isBlockedScreen, blockedCount) {
        if (isBlockedScreen || blockedCount < settings.lastSeenBlockedCount) {
            viewModel.markBlockedFolderSeen(blockedCount)
        }
    }

    val topTitle = when {
        currentDest?.hasRoute(BlockedRoute::class) == true -> strings.titleBlocked
        currentDest?.hasRoute(SettingsRoute::class) == true -> strings.titleSettings
        else -> strings.appName
    }

    SmartSmsTheme(themeMode = settings.themeMode) {
        CompositionLocalProvider(
            LocalLayoutDirection provides layoutDirection,
            LocalStrings provides strings
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = if (isConversationScreen) WindowInsets(0) else WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        if (!isConversationScreen) {
                            TopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(listOf(NeonCyan, ElectricIndigo))
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Shield,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        Text(
                                            text = topTitle,
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                },
                                actions = {
                                    if (isInboxScreen) {
                                        if (unreadCount > 0) {
                                            IconButton(
                                                onClick = viewModel::markAllInboxRead,
                                                modifier = Modifier.testTag("inbox_mark_all_read_btn")
                                            ) {
                                                Icon(
                                                    imageVector = AppIcons.DoneAll,
                                                    contentDescription = strings.menuMarkAllRead,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        if (hasSmsPermissions) {
                                            IconButton(
                                                onClick = { viewModel.syncDeviceMessages(notifyUser = true) },
                                                enabled = !isSyncing,
                                                modifier = Modifier.testTag("inbox_sync_btn")
                                            ) {
                                                if (isSyncing) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = strings.syncSmsBtn,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            )
                        }
                    },
                    floatingActionButton = {
                        if (!isConversationScreen && isInboxScreen && !isInboxSearchOpen) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FloatingActionButton(
                                    onClick = { isInboxSearchOpen = true },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.testTag("fab_inbox_search")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = strings.searchInboxHint
                                    )
                                }
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        composeRecipient = null
                                        composeBody = null
                                        showComposeSheet = true
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    text = { Text(strings.fabCompose) },
                                    modifier = Modifier.testTag("fab_compose_sms")
                                )
                            }
                        }
                    },
                    bottomBar = {
                        if (!isConversationScreen && !useRail) {
                            NavigationBar {
                                navItems.forEach { item ->
                                    val selected = currentDest?.hasRoute(item.route::class) == true
                                    val badgeCount = when (item.route) {
                                        InboxRoute -> unreadCount
                                        BlockedRoute -> unseenBlockedCount
                                        else -> 0
                                    }
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    if (badgeCount > 0) {
                                                        Badge {
                                                            Text(
                                                                PersianFormatters.formatDigits(
                                                                    badgeCount.toString(),
                                                                    strings.language
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label
                                                )
                                            }
                                        },
                                        label = { Text(item.label, maxLines = 1) },
                                        modifier = Modifier.testTag(item.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (!isConversationScreen && useRail) {
                            NavigationRail {
                                navItems.forEach { item ->
                                    val selected = currentDest?.hasRoute(item.route::class) == true
                                    val badgeCount = when (item.route) {
                                        InboxRoute -> unreadCount
                                        BlockedRoute -> unseenBlockedCount
                                        else -> 0
                                    }
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    if (badgeCount > 0) {
                                                        Badge {
                                                            Text(
                                                                PersianFormatters.formatDigits(
                                                                    badgeCount.toString(),
                                                                    strings.language
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label
                                                )
                                            }
                                        },
                                        label = { Text(item.label) },
                                        modifier = Modifier.testTag(item.testTag)
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = InboxRoute,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable<InboxRoute> {
                                InboxScreen(
                                    threads = threads,
                                    selectedThreadKeys = selectedThreadKeys,
                                    searchQuery = inboxSearch,
                                    isSearchOpen = isInboxSearchOpen,
                                    activeFilter = inboxFilter,
                                    hasCorePermissions = hasSmsPermissions && hasContactsPermission,
                                    isDefaultSmsApp = isDefaultSmsApp,
                                    onRequestPermissions = requestAllPermissions,
                                    onRequestDefaultSms = requestDefaultSms,
                                    onSearchChange = viewModel::setInboxSearch,
                                    onCloseSearch = { isInboxSearchOpen = false },
                                    onFilterChange = viewModel::setInboxFilter,
                                    onOpenConversation = { threadKey ->
                                        navController.navigate(ConversationRoute(threadKey))
                                    },
                                    onToggleSelectThread = viewModel::toggleThreadSelection,
                                    onClearSelection = viewModel::clearThreadSelection,
                                    onBatchArchive = { viewModel.batchArchiveSelectedThreads(true) },
                                    onBatchMarkRead = viewModel::batchMarkSelectedRead,
                                    onBatchBlock = { viewModel.batchBlockSelectedThreads(threads) },
                                    onBatchDelete = viewModel::batchDeleteSelectedThreads,
                                    onToggleRead = { key, currentlyUnread ->
                                        viewModel.setThreadReadState(key, isRead = currentlyUnread)
                                    },
                                    onTogglePin = viewModel::setThreadPinned,
                                    onToggleArchive = viewModel::setThreadArchived,
                                    onBlockSender = viewModel::blockSenderQuick,
                                    onDeleteThread = viewModel::deleteInboxThread,
                                    onCopyOtpFeedback = { code ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(strings.msgCopiedOtp(code))
                                        }
                                    }
                                )
                            }

                            composable<BlockedRoute> {
                                BlockedScreen(
                                    blockedMessages = blockedMessages,
                                    searchQuery = blockedSearch,
                                    activeFilter = blockedFilter,
                                    selectedIds = selectedBlockedIds,
                                    onSearchChange = viewModel::setBlockedSearch,
                                    onFilterChange = viewModel::setBlockedFilter,
                                    onToggleSelect = viewModel::toggleBlockedSelection,
                                    onSelectAll = viewModel::selectAllBlocked,
                                    onClearSelection = viewModel::clearBlockedSelection,
                                    onRestoreMessage = viewModel::restoreBlockedMessage,
                                    onDeleteSingle = viewModel::deleteSingleBlockedMessage,
                                    onDeleteSelected = viewModel::deleteSelectedBlockedMessages,
                                    onShowMessage = { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable<SettingsRoute> {
                                SettingsScreen(
                                    settings = settings,
                                    isDefaultSmsApp = isDefaultSmsApp,
                                    hasSmsPermissions = hasSmsPermissions && hasContactsPermission,
                                    isSyncing = isSyncing,
                                    blockedSenders = blockedSenders,
                                    blockedPhrases = blockedPhrases,
                                    allowedSenders = allowedSenders,
                                    autoBlockUnknown = autoBlockUnknown,
                                    autoBlockPromoText = autoBlockPromoText,
                                    onSetLanguage = viewModel::setAppLanguage,
                                    onSetThemeMode = viewModel::setThemeMode,
                                    onRequestDefaultSms = requestDefaultSms,
                                    onRequestPermissions = requestAllPermissions,
                                    onSyncSms = { viewModel.syncDeviceMessages(notifyUser = true) },
                                    onSetSmartLevel = viewModel::setSmartFilterLevel,
                                    onAddBlockedSender = viewModel::blockSenderQuick,
                                    onAddBlockedPhrase = viewModel::addBlockedPhrase,
                                    onDeleteFilterEntry = viewModel::deleteFilterEntry,
                                    onToggleAutoBlockUnknown = viewModel::toggleAutoBlockUnknown,
                                    onToggleAutoBlockPromoText = viewModel::toggleAutoBlockPromoText,
                                    onSetNotificationsEnabled = viewModel::setNotificationsEnabled,
                                    onSetDeliveryReportsEnabled = viewModel::setDeliveryReportsEnabled
                                )
                            }

                            composable<ConversationRoute> { entry ->
                                val route = entry.toRoute<ConversationRoute>()
                                val threadMessages by viewModel.observeThreadMessages(route.threadKey)
                                    .collectAsStateWithLifecycle(initialValue = emptyList())
                                val firstSender = threadMessages.firstOrNull()?.senderRaw ?: route.threadKey
                                val resolvedContact = remember(firstSender, contactLookup) {
                                    viewModel.resolveContactForSender(firstSender)
                                }
                                ConversationScreen(
                                    threadKey = route.threadKey,
                                    messages = threadMessages,
                                    resolvedContact = resolvedContact,
                                    activeSims = activeSims,
                                    onBack = { navController.popBackStack() },
                                    onMarkRead = { sender ->
                                        viewModel.markThreadRead(route.threadKey, sender)
                                    },
                                    onSendSms = viewModel::sendSms,
                                    onWhitelistSender = viewModel::whitelistSender,
                                    onBlockSenderAndExit = { sender ->
                                        viewModel.blockSenderQuick(sender)
                                        navController.popBackStack()
                                    },
                                    onBlockSingleMessage = viewModel::blockSingleMessage,
                                    onDeleteSingleMessage = viewModel::deleteSingleMessage,
                                    onForwardText = { textToForward ->
                                        composeRecipient = null
                                        composeBody = textToForward
                                        showComposeSheet = true
                                    },
                                    onShowMessage = { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showComposeSheet) {
                ComposeSmsSheet(
                    contacts = contacts,
                    activeSims = activeSims,
                    hasContactsPermission = hasContactsPermission,
                    initialBody = composeBody,
                    initialRecipient = composeRecipient,
                    onRequestPermissions = requestAllPermissions,
                    onDismiss = {
                        showComposeSheet = false
                        composeRecipient = null
                        composeBody = null
                    },
                    onSendSms = viewModel::sendSms
                )
            }
        }
    }
}
