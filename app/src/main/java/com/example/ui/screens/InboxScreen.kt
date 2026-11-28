package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ThreadSummary
import com.example.domain.model.OtpExtractor
import com.example.domain.model.SenderType
import com.example.ui.components.AppIcons
import com.example.ui.components.EmptyStateBanner
import com.example.ui.components.PersianFormatters
import com.example.ui.components.SenderAvatar
import com.example.ui.components.StickerIconBadge
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoseCrimson
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TangerineOrange
import com.example.ui.viewmodel.InboxFilterTab

@Composable
fun InboxScreen(
    threads: List<ThreadSummary>,
    selectedThreadKeys: Set<String>,
    searchQuery: String,
    isSearchOpen: Boolean,
    activeFilter: InboxFilterTab,
    hasCorePermissions: Boolean,
    isDefaultSmsApp: Boolean,
    showSimBadge: Boolean = false,
    onRequestPermissions: () -> Unit,
    onRequestDefaultSms: () -> Unit,
    onSearchChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onFilterChange: (InboxFilterTab) -> Unit,
    onOpenConversation: (String) -> Unit,
    onToggleSelectThread: (String) -> Unit,
    onClearSelection: () -> Unit,
    onBatchArchive: () -> Unit,
    onBatchMarkRead: () -> Unit,
    onBatchBlock: () -> Unit,
    onBatchDelete: () -> Unit,
    onToggleRead: (String, Boolean) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onToggleArchive: (String, Boolean) -> Unit,
    onBlockSender: (String) -> Unit,
    onDeleteThread: (String) -> Unit,
    onCopyOtpFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val searchFocusRequester = remember { FocusRequester() }
    var pendingDeleteThreadKey by remember { mutableStateOf<String?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = isSearchOpen) {
        onSearchChange("")
        onCloseSearch()
    }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            searchFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            // Contextual multi-select bar with colorful sticker action badges
            AnimatedVisibility(visible = selectedThreadKeys.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClearSelection) {
                                Icon(Icons.Default.Close, contentDescription = strings.cancel)
                            }
                            Text(
                                text = PersianFormatters.formatDigits(
                                    strings.selectedCountLabel(selectedThreadKeys.size),
                                    strings.language
                                ),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = onBatchMarkRead) {
                                StickerIconBadge(
                                    icon = AppIcons.DoneAll,
                                    tint = SafeEmerald,
                                    contentDescription = strings.menuMarkRead
                                )
                            }
                            IconButton(onClick = onBatchArchive) {
                                StickerIconBadge(
                                    icon = AppIcons.Archive,
                                    tint = ElectricIndigo,
                                    contentDescription = strings.menuArchive
                                )
                            }
                            IconButton(onClick = onBatchBlock) {
                                StickerIconBadge(
                                    icon = AppIcons.Block,
                                    tint = TangerineOrange,
                                    contentDescription = strings.menuBlockSender
                                )
                            }
                            IconButton(
                                onClick = { showBatchDeleteConfirm = true },
                                modifier = Modifier.testTag("inbox_batch_delete_btn")
                            ) {
                                StickerIconBadge(
                                    icon = Icons.Default.Delete,
                                    tint = RoseCrimson,
                                    contentDescription = strings.menuDeleteThread
                                )
                            }
                        }
                    }
                }
            }

            // Permissions & Default SMS Setup Card (only shown when missing permissions or not default)
            AnimatedVisibility(visible = !hasCorePermissions || !isDefaultSmsApp) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.45f)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("inbox_setup_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = strings.defaultSmsMissingTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = strings.defaultSmsMissingBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!hasCorePermissions) {
                                Button(
                                    onClick = onRequestPermissions,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("inbox_grant_perm_btn")
                                ) {
                                    Text(strings.grantPermissionBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (!isDefaultSmsApp) {
                                OutlinedButton(
                                    onClick = onRequestDefaultSms,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("inbox_set_default_btn")
                                ) {
                                    Text(strings.setAsDefaultBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    InboxFilterTab.ALL to strings.filterAll,
                    InboxFilterTab.UNREAD to strings.filterUnread,
                    InboxFilterTab.ARCHIVED to strings.filterArchived
                )
                tabs.forEach { (tab, label) ->
                    FilterChip(
                        selected = activeFilter == tab,
                        onClick = { onFilterChange(tab) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("inbox_filter_${tab.name.lowercase()}")
                    )
                }
            }

            if (threads.isEmpty()) {
                EmptyStateBanner(
                    title = strings.emptyInboxTitle,
                    subtitle = strings.emptyInboxSubtitle
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(threads, key = { it.threadKey }) { thread ->
                        ThreadCard(
                            thread = thread,
                            isSelected = thread.threadKey in selectedThreadKeys,
                            selectionMode = selectedThreadKeys.isNotEmpty(),
                            showSimBadge = showSimBadge || thread.lastSimSlot > 1,
                            onTap = {
                                if (selectedThreadKeys.isNotEmpty()) {
                                    onToggleSelectThread(thread.threadKey)
                                } else {
                                    onOpenConversation(thread.threadKey)
                                }
                            },
                            onLongPress = { onToggleSelectThread(thread.threadKey) },
                            onToggleRead = { onToggleRead(thread.threadKey, thread.unreadCount > 0) },
                            onTogglePin = { onTogglePin(thread.threadKey, !thread.isPinned) },
                            onToggleArchive = { onToggleArchive(thread.threadKey, !thread.isArchived) },
                            onBlockSender = { onBlockSender(thread.senderRaw.ifBlank { thread.senderDisplay }) },
                            onDelete = { pendingDeleteThreadKey = thread.threadKey },
                            onCopyOtp = { code ->
                                clipboard.setText(AnnotatedString(code))
                                onCopyOtpFeedback(code)
                            }
                        )
                    }
                }
            }
        }

        // Bottom thumb-friendly Search bar opened by the bottom Search Lens button
        AnimatedVisibility(
            visible = isSearchOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                color = MaterialTheme.colorScheme.surface
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(strings.searchInboxHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                onSearchChange("")
                                onCloseSearch()
                            },
                            modifier = Modifier.testTag("inbox_search_close_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = strings.close)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                        .testTag("inbox_search_input")
                )
            }
        }
    }

    pendingDeleteThreadKey?.let { keyToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteThreadKey = null },
            icon = {
                StickerIconBadge(
                    icon = Icons.Default.Delete,
                    tint = RoseCrimson,
                    size = 42.dp,
                    iconSize = 24.dp
                )
            },
            title = { Text(strings.confirmDeleteThreadTitle) },
            text = { Text(strings.confirmDeleteThreadBody) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteThread(keyToDelete)
                        pendingDeleteThreadKey = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseCrimson,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_delete_thread_btn")
                ) {
                    Text(strings.deleteForever)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteThreadKey = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            icon = {
                StickerIconBadge(
                    icon = Icons.Default.Delete,
                    tint = RoseCrimson,
                    size = 42.dp,
                    iconSize = 24.dp
                )
            },
            title = {
                Text(
                    PersianFormatters.formatDigits(
                        strings.confirmDeleteBatchThreadsTitle(selectedThreadKeys.size),
                        strings.language
                    )
                )
            },
            text = { Text(strings.confirmDeleteBatchThreadsBody) },
            confirmButton = {
                Button(
                    onClick = {
                        onBatchDelete()
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseCrimson,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_batch_delete_threads_btn")
                ) {
                    Text(strings.deleteForever)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadCard(
    thread: ThreadSummary,
    isSelected: Boolean,
    selectionMode: Boolean,
    showSimBadge: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleRead: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onBlockSender: () -> Unit,
    onDelete: () -> Unit,
    onCopyOtp: (String) -> Unit
) {
    val strings = LocalStrings.current
    var menuExpanded by remember { mutableStateOf(false) }
    val hasUnread = thread.unreadCount > 0
    val previewBody = thread.matchedSnippet ?: thread.lastBody
    val otpCode = remember(thread.lastBody) { OtpExtractor.extractOtp(thread.lastBody) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .testTag("thread_card_${thread.threadKey}"),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                hasUnread -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected || hasUnread) 1.2.dp else 0.8.dp,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                hasUnread -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hasUnread) 3.dp else 0.5.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onLongPress() }
                )
            } else {
                SenderAvatar(
                    senderDisplay = thread.senderDisplay,
                    senderType = thread.senderType,
                    contactName = thread.contactName,
                    photoUri = thread.contactPhotoUri
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (thread.isPinned) {
                            StickerIconBadge(
                                icon = AppIcons.Pin,
                                tint = AmberGold,
                                contentDescription = strings.menuPin,
                                size = 20.dp,
                                iconSize = 13.dp
                            )
                        }
                        Text(
                            text = thread.displayTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (thread.senderType == SenderType.ALPHANUMERIC) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = strings.badgeTextSender,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (thread.isArchived) {
                            Surface(
                                color = ElectricIndigo.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = strings.badgeArchived,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricIndigo,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (showSimBadge && thread.lastSimSlot > 0) {
                            val simTint = if (thread.lastSimSlot == 2) AmberGold else NeonCyan
                            Surface(
                                color = simTint.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = PersianFormatters.formatDigits("SIM ${thread.lastSimSlot}", strings.language),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = simTint,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = PersianFormatters.formatRelativeOrTime(thread.lastTimestamp, strings.language),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = previewBody,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (hasUnread || thread.matchedSnippet != null) FontWeight.Medium else FontWeight.Normal
                        ),
                        color = when {
                            thread.matchedSnippet != null -> MaterialTheme.colorScheme.primary
                            hasUnread -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (hasUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(NeonCyan, ElectricIndigo))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = PersianFormatters.formatDigits(thread.unreadCount.toString(), strings.language),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }

                if (otpCode != null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onCopyOtp(otpCode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                AppIcons.Copy,
                                contentDescription = null,
                                tint = SkyBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = strings.copyOtpBtn(otpCode),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("thread_menu_${thread.threadKey}")
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (thread.isPinned) strings.menuUnpin else strings.menuPin) },
                        leadingIcon = {
                            StickerIconBadge(
                                icon = AppIcons.Pin,
                                tint = AmberGold
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (hasUnread) strings.menuMarkRead else strings.menuMarkUnread) },
                        leadingIcon = {
                            StickerIconBadge(
                                icon = if (hasUnread) Icons.Default.CheckCircle else Icons.Default.Notifications,
                                tint = if (hasUnread) SafeEmerald else SkyBlue
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleRead()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (thread.isArchived) strings.menuUnarchive else strings.menuArchive) },
                        leadingIcon = {
                            StickerIconBadge(
                                icon = if (thread.isArchived) AppIcons.Restore else AppIcons.Archive,
                                tint = if (thread.isArchived) SoftViolet else ElectricIndigo
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleArchive()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.menuBlockSender, color = TangerineOrange) },
                        leadingIcon = {
                            StickerIconBadge(
                                icon = AppIcons.Block,
                                tint = TangerineOrange
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onBlockSender()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.menuDeleteThread, color = RoseCrimson) },
                        leadingIcon = {
                            StickerIconBadge(
                                icon = Icons.Default.Delete,
                                tint = RoseCrimson
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
