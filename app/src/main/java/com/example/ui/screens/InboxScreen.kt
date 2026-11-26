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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoseCrimson
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
            // Contextual multi-select bar (Google Messages style)
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
                        Row {
                            IconButton(onClick = onBatchMarkRead) {
                                Icon(AppIcons.DoneAll, contentDescription = strings.menuMarkRead)
                            }
                            IconButton(onClick = onBatchArchive) {
                                Icon(AppIcons.Archive, contentDescription = strings.menuArchive)
                            }
                            IconButton(onClick = onBatchBlock) {
                                Icon(AppIcons.Block, contentDescription = strings.menuBlockSender, tint = RoseCrimson)
                            }
                            IconButton(onClick = onBatchDelete) {
                                Icon(Icons.Default.Delete, contentDescription = strings.menuDeleteThread, tint = MaterialTheme.colorScheme.error)
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
                            onDelete = { onDeleteThread(thread.threadKey) },
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                color = MaterialTheme.colorScheme.surface
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(strings.searchInboxHint) },
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadCard(
    thread: ThreadSummary,
    isSelected: Boolean,
    selectionMode: Boolean,
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
                hasUnread -> NeonCyan.copy(alpha = 0.55f)
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (thread.isPinned) {
                            Icon(
                                imageVector = AppIcons.Pin,
                                contentDescription = strings.menuPin,
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = thread.displayTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        text = thread.lastBody,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal
                        ),
                        color = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                        leadingIcon = { Icon(AppIcons.Pin, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (hasUnread) strings.menuMarkRead else strings.menuMarkUnread) },
                        leadingIcon = {
                            Icon(
                                if (hasUnread) Icons.Default.CheckCircle else Icons.Default.Notifications,
                                contentDescription = null
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
                            Icon(
                                if (thread.isArchived) AppIcons.Restore else AppIcons.Archive,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleArchive()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.menuBlockSender) },
                        leadingIcon = { Icon(AppIcons.Block, contentDescription = null, tint = RoseCrimson) },
                        onClick = {
                            menuExpanded = false
                            onBlockSender()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.menuDeleteThread) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
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
