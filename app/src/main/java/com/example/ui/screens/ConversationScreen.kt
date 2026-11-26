package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.DeliveryStatus
import com.example.data.local.MessageEntity
import com.example.domain.model.OtpExtractor
import com.example.domain.model.SenderClassifier
import com.example.domain.model.SenderType
import com.example.domain.model.SmsPartCalculator
import com.example.sms.ResolvedContact
import com.example.sms.SimCardInfo
import com.example.ui.components.AppIcons
import com.example.ui.components.PersianFormatters
import com.example.ui.components.SenderAvatar
import com.example.ui.components.SimChooserDialog
import com.example.ui.components.SystemSmsLauncher
import com.example.ui.components.UriBitmapImage
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoseCrimson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    threadKey: String,
    messages: List<MessageEntity>,
    resolvedContact: ResolvedContact?,
    activeSims: List<SimCardInfo>,
    onBack: () -> Unit,
    onMarkRead: (senderRaw: String) -> Unit,
    onSendSms: (recipient: String, body: String, attachmentUri: String?, selectedSim: SimCardInfo?) -> Unit,
    onWhitelistSender: (String) -> Unit,
    onBlockSenderAndExit: (String) -> Unit,
    onBlockSingleMessage: (messageId: Long, alsoBlacklist: Boolean) -> Unit,
    onDeleteSingleMessage: (messageId: Long) -> Unit,
    onForwardText: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val strings = LocalStrings.current

    val firstMsg = messages.firstOrNull()
    val senderRaw = firstMsg?.senderRaw?.ifBlank { threadKey } ?: threadKey
    val senderDisplay = SenderClassifier.displaySender(senderRaw)
    val senderType = firstMsg?.senderType ?: SenderClassifier.classify(senderRaw)
    val headerTitle = resolvedContact?.name?.takeIf { it.isNotBlank() } ?: senderDisplay

    var replyText by remember { mutableStateOf("") }
    var attachmentUri by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var actionTargetMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var detailsMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showSimDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> attachmentUri = uri?.toString() }
    )

    LaunchedEffect(threadKey, senderRaw) {
        onMarkRead(senderRaw)
    }

    // Always scroll to newest message (bottom) when opening or receiving/sending
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("conversation_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SenderAvatar(
                            senderDisplay = senderDisplay,
                            senderType = senderType,
                            contactName = resolvedContact?.name,
                            photoUri = resolvedContact?.photoUri,
                            modifier = Modifier.size(38.dp)
                        )
                        Column {
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (resolvedContact != null) {
                                    senderDisplay
                                } else if (strings.language == AppLanguage.EN) {
                                    senderType.name
                                } else {
                                    senderType.labelFa
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (senderType == SenderType.PHONE_NUMBER || senderType == SenderType.SHORT_CODE) {
                        IconButton(onClick = { SystemSmsLauncher.launchDialer(context, senderRaw) }) {
                            Icon(Icons.Default.Call, contentDescription = strings.callSender)
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(strings.addToWhitelist) },
                                leadingIcon = { Icon(AppIcons.Shield, null) },
                                onClick = {
                                    menuExpanded = false
                                    onWhitelistSender(senderRaw)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.addToBlacklist) },
                                leadingIcon = { Icon(AppIcons.Block, null, tint = RoseCrimson) },
                                onClick = {
                                    menuExpanded = false
                                    onBlockSenderAndExit(senderRaw)
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val parts = SmsPartCalculator.calculateParts(replyText)
                        if (replyText.isNotEmpty()) {
                            Text(
                                text = PersianFormatters.formatDigits(
                                    strings.smsPartInfo(replyText.length, parts),
                                    strings.language
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Icon(
                                    AppIcons.Attach,
                                    contentDescription = if (strings.language == AppLanguage.EN) "Attach photo" else "پیوست تصویر",
                                    tint = if (attachmentUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedTextField(
                                value = replyText,
                                onValueChange = { replyText = it },
                                placeholder = { Text(strings.replyPlaceholder) },
                                maxLines = 4,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("conversation_reply_input")
                            )

                            FilledIconButton(
                                onClick = {
                                    if (replyText.isNotBlank() || attachmentUri != null) {
                                        if (activeSims.size > 1) {
                                            showSimDialog = true
                                        } else {
                                            onSendSms(senderRaw, replyText, attachmentUri, activeSims.firstOrNull())
                                            replyText = ""
                                            attachmentUri = null
                                        }
                                    }
                                },
                                enabled = replyText.isNotBlank() || attachmentUri != null,
                                modifier = Modifier.testTag("conversation_send_btn")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = strings.composeSendBtn
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                    val prev = messages.getOrNull(index - 1)
                    val showDateHeader = prev == null || !PersianFormatters.isSameDay(prev.timestamp, msg.timestamp)
                    if (showDateHeader) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = PersianFormatters.formatDateDivider(
                                        timestamp = msg.timestamp,
                                        todayLabel = strings.todayLabel,
                                        yesterdayLabel = strings.yesterdayLabel,
                                        language = strings.language
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    MessageBubble(
                        message = msg,
                        showSimBadge = activeSims.size > 1,
                        onLongPress = { actionTargetMessage = msg },
                        onCopyOtp = { code ->
                            clipboard.setText(AnnotatedString(code))
                            onShowMessage(strings.msgCopiedOtp(code))
                        }
                    )
                }
            }
        }
    }

    if (showSimDialog) {
        SimChooserDialog(
            sims = activeSims,
            onSelectSim = { sim ->
                showSimDialog = false
                if (replyText.isNotBlank() || attachmentUri != null) {
                    onSendSms(senderRaw, replyText, attachmentUri, sim)
                    replyText = ""
                    attachmentUri = null
                }
            },
            onDismiss = { showSimDialog = false }
        )
    }

    actionTargetMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { actionTargetMessage = null },
            title = { Text(strings.detailsTitle) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text(strings.bubbleActionCopy) },
                        leadingContent = { Icon(AppIcons.Copy, contentDescription = null) },
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(target.body))
                            onShowMessage(strings.msgCopiedText)
                            actionTargetMessage = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text(strings.bubbleActionForward) },
                        leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                        modifier = Modifier.clickable {
                            actionTargetMessage = null
                            onForwardText(target.body)
                        }
                    )
                    if (!target.isOutgoing) {
                        ListItem(
                            headlineContent = { Text(strings.bubbleActionSpam) },
                            leadingContent = { Icon(AppIcons.Block, contentDescription = null, tint = RoseCrimson) },
                            modifier = Modifier.clickable {
                                actionTargetMessage = null
                                onBlockSingleMessage(target.id, true)
                            }
                        )
                    }
                    ListItem(
                        headlineContent = { Text(strings.bubbleActionDetails) },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                        modifier = Modifier.clickable {
                            actionTargetMessage = null
                            detailsMessage = target
                        }
                    )
                    ListItem(
                        headlineContent = { Text(strings.bubbleActionDelete) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            actionTargetMessage = null
                            onDeleteSingleMessage(target.id)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTargetMessage = null }) {
                    Text(strings.close)
                }
            }
        )
    }

    detailsMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { detailsMessage = null },
            title = { Text(strings.detailsTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "${msg.senderRaw} (${msg.senderType.labelFa})", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = PersianFormatters.formatFullDateTime(msg.timestamp, strings.language),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = PersianFormatters.formatDigits(
                            strings.smsPartInfo(msg.body.length, msg.partCount),
                            strings.language
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!msg.isOutgoing) {
                        Text(
                            text = PersianFormatters.formatDigits(
                                strings.scoreLabel(msg.smartScore),
                                strings.language
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsMessage = null }) {
                    Text(strings.close)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    showSimBadge: Boolean,
    onLongPress: () -> Unit,
    onCopyOtp: (String) -> Unit
) {
    val strings = LocalStrings.current
    val isOutgoing = message.isOutgoing
    val otpCode = remember(message.body, isOutgoing) {
        if (isOutgoing) null else OtpExtractor.extractOtp(message.body)
    }
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isOutgoing) 6.dp else 20.dp,
        bottomEnd = if (isOutgoing) 20.dp else 6.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.Start else Alignment.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(bubbleShape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
                .then(
                    if (isOutgoing) {
                        Modifier.background(
                            Brush.linearGradient(
                                listOf(ElectricIndigo, Color(0xFF0EA5E9))
                            )
                        )
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                message.attachmentUri?.let { uri ->
                    UriBitmapImage(
                        uriString = uri,
                        contentDescription = LocalContext.current.getString(R.string.cd_mms_attachment),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                if (message.body.isNotBlank()) {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = PersianFormatters.formatTimeOnly(message.timestamp, strings.language),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOutgoing) {
                            Color.White.copy(alpha = 0.82f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                        }
                    )
                    if (showSimBadge && message.simSlot > 0) {
                        Text(
                            text = PersianFormatters.formatDigits("SIM ${message.simSlot}", strings.language),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOutgoing) {
                                Color.White.copy(alpha = 0.75f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                    }
                    if (message.partCount > 1) {
                        val partText = if (strings.language == AppLanguage.EN) {
                            "${message.partCount} parts"
                        } else {
                            "${PersianFormatters.toPersianDigits(message.partCount.toString())} بخش"
                        }
                        Text(
                            text = partText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOutgoing) {
                                Color.White.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    if (isOutgoing) {
                        when (message.deliveryStatus) {
                            DeliveryStatus.SENDING -> {
                                Text(
                                    text = "...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.testTag("delivery_status_sending_${message.id}")
                                )
                            }
                            DeliveryStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = if (strings.language == AppLanguage.EN) "Sent" else "ارسال‌شده",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .testTag("delivery_status_sent_${message.id}")
                                )
                            }
                            DeliveryStatus.DELIVERED -> {
                                Icon(
                                    imageVector = AppIcons.DoneAll,
                                    contentDescription = if (strings.language == AppLanguage.EN) "Delivered" else "تحویل‌داده‌شده",
                                    tint = NeonCyan,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .testTag("delivery_status_delivered_${message.id}")
                                )
                            }
                            DeliveryStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = if (strings.language == AppLanguage.EN) "Failed" else "خطا در ارسال",
                                    tint = AmberGold,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .testTag("delivery_status_failed_${message.id}")
                                )
                            }
                            DeliveryStatus.NONE -> Unit
                        }
                    }
                }
            }
        }

        if (otpCode != null) {
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCopyOtp(otpCode) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        AppIcons.Copy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = strings.copyOtpBtn(otpCode),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
