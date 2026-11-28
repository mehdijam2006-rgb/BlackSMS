package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.MessageEntity
import com.example.domain.model.SenderClassifier
import com.example.ui.components.AppIcons
import com.example.ui.components.BlockSourceBadge
import com.example.ui.components.EmptyStateBanner
import com.example.ui.components.PersianFormatters
import com.example.ui.components.SenderAvatar
import com.example.ui.components.StickerIconBadge
import com.example.ui.components.UriBitmapImage
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.RoseCrimson
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.SkyBlue
import com.example.ui.viewmodel.BlockedFilterTab

@Composable
fun BlockedScreen(
    blockedMessages: List<MessageEntity>,
    searchQuery: String,
    activeFilter: BlockedFilterTab,
    selectedIds: Set<Long>,
    onSearchChange: (String) -> Unit,
    onFilterChange: (BlockedFilterTab) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: (List<Long>) -> Unit,
    onClearSelection: () -> Unit,
    onRestoreMessage: (messageId: Long, alsoWhitelist: Boolean) -> Unit,
    onDeleteSingle: (Long) -> Unit,
    onDeleteSelected: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var inspectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var pendingDeleteSingleId by remember { mutableStateOf<Long?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            AnimatedVisibility(visible = selectedIds.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClearSelection) {
                                Icon(Icons.Default.Close, contentDescription = strings.cancel)
                            }
                            Text(
                                text = PersianFormatters.formatDigits(
                                    strings.selectedCountLabel(selectedIds.size),
                                    strings.language
                                ),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onSelectAll(blockedMessages.map { it.id }) }) {
                                Icon(Icons.Default.Check, contentDescription = strings.selectAll)
                            }
                            Button(
                                onClick = { showBatchDeleteConfirm = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.testTag("blocked_batch_delete_btn")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.batchDelete)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(strings.searchBlockedHint) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = strings.close)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("blocked_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    BlockedFilterTab.ALL to strings.filterAll,
                    BlockedFilterTab.SMART to strings.filterSmart,
                    BlockedFilterTab.MANUAL to strings.filterManual,
                    BlockedFilterTab.ALPHANUMERIC to strings.filterTextSender
                )
                tabs.forEach { (tab, label) ->
                    FilterChip(
                        selected = activeFilter == tab,
                        onClick = { onFilterChange(tab) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("blocked_filter_${tab.name.lowercase()}")
                    )
                }
            }

            if (blockedMessages.isEmpty()) {
                EmptyStateBanner(
                    title = strings.emptyBlockedTitle,
                    subtitle = strings.emptyBlockedSubtitle
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(blockedMessages, key = { it.id }) { msg ->
                        BlockedMessageCard(
                            message = msg,
                            isSelected = msg.id in selectedIds,
                            selectionMode = selectedIds.isNotEmpty(),
                            onTap = {
                                if (selectedIds.isNotEmpty()) {
                                    onToggleSelect(msg.id)
                                } else {
                                    inspectedMessage = msg
                                }
                            },
                            onView = { inspectedMessage = msg },
                            onLongPress = { onToggleSelect(msg.id) },
                            onRestore = { onRestoreMessage(msg.id, false) },
                            onRestoreAndWhitelist = { onRestoreMessage(msg.id, true) },
                            onRequestDelete = { pendingDeleteSingleId = msg.id }
                        )
                    }
                }
            }
        }
    }

    inspectedMessage?.let { msg ->
        var showExplanation by remember(msg.id) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { inspectedMessage = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SenderAvatar(
                        senderDisplay = SenderClassifier.displaySender(msg.senderRaw),
                        senderType = msg.senderType
                    )
                    Column {
                        Text(
                            text = SenderClassifier.displaySender(msg.senderRaw),
                            style = MaterialTheme.typography.titleMedium
                        )
                        val typeLabel = if (strings.language == AppLanguage.EN) msg.senderType.name else msg.senderType.labelFa
                        Text(
                            text = "$typeLabel • ${PersianFormatters.formatFullDateTime(msg.timestamp, strings.language)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    msg.attachmentUri?.let { uri ->
                        UriBitmapImage(
                            uriString = uri,
                            contentDescription = context.getString(R.string.cd_mms_attachment),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg.body,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .padding(12.dp)
                                .testTag("blocked_view_body_${msg.id}")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(msg.body))
                                onShowMessage(strings.msgCopiedText)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("blocked_view_copy_btn")
                        ) {
                            Icon(AppIcons.Copy, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.bubbleActionCopy, style = MaterialTheme.typography.labelMedium)
                        }

                        TextButton(
                            onClick = { showExplanation = !showExplanation },
                            modifier = Modifier.testTag("blocked_request_explanation_btn")
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (showExplanation) strings.hideExplanationBtn else strings.requestExplanationBtn,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    AnimatedVisibility(visible = showExplanation) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("blocked_explanation_panel")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BlockSourceBadge(msg.blockSource)
                                    Text(
                                        text = PersianFormatters.formatDigits(
                                            strings.scoreLabel(msg.smartScore),
                                            strings.language
                                        ),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily)
                                    )
                                }
                                Text(
                                    text = msg.blockReason?.takeIf { it.isNotBlank() } ?: strings.noExplanationAvailable,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreMessage(msg.id, true)
                        inspectedMessage = null
                    },
                    modifier = Modifier.testTag("dialog_restore_whitelist_btn")
                ) {
                    Icon(AppIcons.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.restoreAndAllow)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            onRestoreMessage(msg.id, false)
                            inspectedMessage = null
                        }
                    ) {
                        Text(strings.onlyRestore)
                    }
                    TextButton(onClick = { inspectedMessage = null }) {
                        Text(strings.close)
                    }
                }
            }
        )
    }

    pendingDeleteSingleId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSingleId = null },
            icon = {
                StickerIconBadge(
                    icon = Icons.Default.Delete,
                    tint = RoseCrimson,
                    size = 42.dp,
                    iconSize = 24.dp
                )
            },
            title = { Text(strings.confirmDeleteSingleTitle) },
            text = { Text(strings.confirmDeleteSingleBody) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSingle(id)
                        pendingDeleteSingleId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseCrimson)
                ) {
                    Text(strings.deleteForever)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSingleId = null }) {
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
                        strings.confirmDeleteBatchTitle(selectedIds.size),
                        strings.language
                    )
                )
            },
            text = { Text(strings.confirmDeleteBatchBody) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSelected()
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseCrimson)
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
private fun BlockedMessageCard(
    message: MessageEntity,
    isSelected: Boolean,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onView: () -> Unit,
    onLongPress: () -> Unit,
    onRestore: () -> Unit,
    onRestoreAndWhitelist: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val strings = LocalStrings.current
    val senderDisplay = SenderClassifier.displaySender(message.senderRaw)
    val typeLabel = if (strings.language == AppLanguage.EN) message.senderType.name else message.senderType.labelFa
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .testTag("blocked_card_${message.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
            }
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onLongPress() }
                        )
                    } else {
                        SenderAvatar(
                            senderDisplay = senderDisplay,
                            senderType = message.senderType,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Column {
                        Text(
                            text = senderDisplay,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$typeLabel • ${PersianFormatters.formatRelativeOrTime(message.timestamp, strings.language)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(
                    onClick = onView,
                    modifier = Modifier.testTag("view_blocked_btn_${message.id}")
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.viewMessageBtn, color = SkyBlue, style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.testTag("restore_btn_${message.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(AppIcons.Restore, contentDescription = null, tint = SafeEmerald, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.restore, style = MaterialTheme.typography.labelMedium)
                }

                if (message.senderRaw.isNotBlank()) {
                    OutlinedButton(
                        onClick = onRestoreAndWhitelist,
                        modifier = Modifier.testTag("whitelist_btn_${message.id}"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ElectricIndigo, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.allowSender, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = onRequestDelete,
                    modifier = Modifier.testTag("delete_blocked_btn_${message.id}")
                ) {
                    StickerIconBadge(
                        icon = Icons.Default.Delete,
                        tint = RoseCrimson,
                        contentDescription = strings.deleteForever
                    )
                }
            }
        }
    }
}
