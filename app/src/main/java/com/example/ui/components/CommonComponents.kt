package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.BlockSource
import com.example.domain.model.SenderType
import com.example.domain.model.SmsPartCalculator
import com.example.sms.DeviceContact
import com.example.sms.SimCardInfo
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.AvatarGradientPairs
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.SafeEmeraldBg
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object PersianFormatters {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.getDefault())

    fun toPersianDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(('۰'.code + (ch.code - '0'.code)).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatDigits(input: String, language: AppLanguage): String =
        if (language == AppLanguage.FA) toPersianDigits(input) else input

    fun formatTimeOnly(timestamp: Long, language: AppLanguage): String =
        formatDigits(timeFormat.format(Date(timestamp)), language)

    fun isSameDay(t1: Long, t2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    fun formatDateDivider(
        timestamp: Long,
        todayLabel: String,
        yesterdayLabel: String,
        language: AppLanguage
    ): String {
        val now = System.currentTimeMillis()
        if (isSameDay(timestamp, now)) return todayLabel
        val yesterday = now - 86_400_000L
        if (isSameDay(timestamp, yesterday)) return yesterdayLabel
        return formatDigits(dateOnlyFormat.format(Date(timestamp)), language)
    }

    fun formatRelativeOrTime(timestamp: Long, language: AppLanguage): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return if (language == AppLanguage.EN) {
            when {
                diff in 0..60_000L -> "Just now"
                diff in 0..3600_000L -> "${diff / 60_000L}m ago"
                isSameDay(timestamp, now) -> timeFormat.format(Date(timestamp))
                else -> dateOnlyFormat.format(Date(timestamp))
            }
        } else {
            when {
                diff in 0..60_000L -> "لحظاتی پیش"
                diff in 0..3600_000L -> toPersianDigits("${diff / 60_000L} دقیقه پیش")
                isSameDay(timestamp, now) -> toPersianDigits(timeFormat.format(Date(timestamp)))
                else -> toPersianDigits(dateOnlyFormat.format(Date(timestamp)))
            }
        }
    }

    fun formatFullDateTime(timestamp: Long, language: AppLanguage): String =
        formatDigits(dateTimeFormat.format(Date(timestamp)), language)
}

object SystemSmsLauncher {
    fun launchSystemSmsComposer(context: Context, recipient: String, body: String): Boolean {
        val cleanRecipient = recipient.trim()
        val uri = Uri.parse("smsto:${Uri.encode(cleanRecipient)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "SMS permission required.",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

    fun launchDialer(context: Context, phoneNumber: String) {
        val clean = phoneNumber.trim()
        if (clean.isEmpty()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(clean)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}

@Composable
fun SimChooserDialog(
    sims: List<SimCardInfo>,
    onSelectSim: (SimCardInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.chooseSimTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sims.forEach { sim ->
                    val slotNum = sim.slotIndex + 1
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectSim(sim) }
                            .testTag("sim_option_$slotNum")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(NeonCyan, ElectricIndigo))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = PersianFormatters.formatDigits(slotNum.toString(), strings.language),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = PersianFormatters.formatDigits(strings.simSlotLabel(slotNum), strings.language),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                val subtitle = listOf(sim.displayName, sim.carrierName)
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                    .joinToString(" • ")
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

/**
 * Lightweight native ContentResolver image loader so BlackSMS avoids bundling Coil.
 */
@Composable
fun UriBitmapImage(
    uriString: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uriString) {
        value = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src).asImageBitmap()
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    bitmap?.let { img ->
        Image(
            bitmap = img,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun SenderAvatar(
    senderDisplay: String,
    senderType: SenderType,
    contactName: String? = null,
    photoUri: String? = null,
    modifier: Modifier = Modifier
) {
    val titleForHash = contactName?.takeIf { it.isNotBlank() } ?: senderDisplay
    val pairIndex = abs(titleForHash.hashCode()) % AvatarGradientPairs.size
    val gradient = when (senderType) {
        SenderType.PRIVATE_OR_INVALID -> Color(0xFFF43F5E) to Color(0xFFFB7185)
        SenderType.ALPHANUMERIC -> Color(0xFF6366F1) to Color(0xFF22D3EE)
        else -> AvatarGradientPairs[pairIndex]
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(gradient.first, gradient.second)))
            .border(1.5.dp, Color.White.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrBlank()) {
            UriBitmapImage(
                uriString = photoUri,
                contentDescription = titleForHash,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
            )
        } else if (!contactName.isNullOrBlank()) {
            val initial = contactName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        } else {
            when (senderType) {
                SenderType.ALPHANUMERIC -> {
                    Text(
                        text = senderDisplay.take(2).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
                SenderType.PRIVATE_OR_INVALID -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = senderType.labelFa,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                SenderType.SHORT_CODE -> {
                    Icon(
                        imageVector = AppIcons.Tag,
                        contentDescription = senderType.labelFa,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                SenderType.PHONE_NUMBER -> {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = senderType.labelFa,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BlockSourceBadge(blockSource: BlockSource?) {
    val strings = LocalStrings.current
    val isEn = strings.language == AppLanguage.EN
    val (label, bg, fg, icon) = when (blockSource) {
        BlockSource.SMART_ENGINE -> Quad(
            if (isEn) "Smart Shield" else "فیلتر هوشمند",
            WarningAmberBg.copy(alpha = 0.85f),
            WarningAmber,
            AppIcons.Spark
        )
        BlockSource.MANUAL_RULE -> Quad(
            if (isEn) "Auto Rule" else "قانون خودکار",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            AppIcons.Shield
        )
        BlockSource.BLACKLIST -> Quad(
            if (isEn) "Spam / Blocked" else "مسدودشده",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            AppIcons.Block
        )
        null -> Quad(
            if (isEn) "Allowed" else "مجاز",
            SafeEmeraldBg,
            SafeEmerald,
            Icons.Default.CheckCircle
        )
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

/**
 * Lightweight vector-drawn Compose Shield illustration (0 KB raster overhead).
 */
@Composable
fun EmptyStateBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(116.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(116.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.25f),
                            ElectricIndigo.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = size.minDimension / 2f
                    )
                )
                val w = size.width
                val h = size.height
                val shield = Path().apply {
                    moveTo(cx, h * 0.14f)
                    lineTo(w * 0.80f, h * 0.28f)
                    lineTo(w * 0.80f, h * 0.54f)
                    cubicTo(w * 0.80f, h * 0.74f, cx, h * 0.86f, cx, h * 0.86f)
                    cubicTo(cx, h * 0.86f, w * 0.20f, h * 0.74f, w * 0.20f, h * 0.54f)
                    lineTo(w * 0.20f, h * 0.28f)
                    close()
                }
                drawPath(
                    path = shield,
                    brush = Brush.linearGradient(
                        colors = listOf(ElectricIndigo.copy(alpha = 0.35f), NeonCyan.copy(alpha = 0.25f))
                    )
                )
                drawPath(
                    path = shield,
                    brush = Brush.linearGradient(colors = listOf(NeonCyan, ElectricIndigo)),
                    style = Stroke(width = 4.5f)
                )
            }
            Icon(
                imageVector = AppIcons.ChatBubble,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeSmsSheet(
    contacts: List<DeviceContact>,
    activeSims: List<SimCardInfo>,
    hasContactsPermission: Boolean,
    initialBody: String? = null,
    initialRecipient: String? = null,
    onRequestPermissions: () -> Unit,
    onDismiss: () -> Unit,
    onSendSms: (recipient: String, body: String, attachmentUri: String?, selectedSim: SimCardInfo?) -> Unit
) {
    val strings = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var recipient by remember { mutableStateOf(initialRecipient ?: "") }
    var selectedContactName by remember { mutableStateOf<String?>(null) }
    var body by remember { mutableStateOf(initialBody ?: "") }
    var attachmentUri by remember { mutableStateOf<String?>(null) }
    var showSimDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> attachmentUri = uri?.toString() }
    )

    val filteredContacts = remember(contacts, recipient) {
        val q = recipient.trim().lowercase()
        if (q.isEmpty()) {
            contacts.take(25)
        } else {
            contacts.filter {
                it.name.lowercase().contains(q) ||
                    it.phoneNumber.lowercase().contains(q) ||
                    it.normalizedKey.contains(q)
            }.take(25)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.composeTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = strings.close)
                }
            }

            OutlinedTextField(
                value = recipient,
                onValueChange = {
                    recipient = it
                    selectedContactName = null
                },
                label = {
                    Text(
                        if (!selectedContactName.isNullOrBlank()) {
                            "${strings.composeRecipientLabel}: $selectedContactName"
                        } else {
                            strings.composeRecipientLabel
                        }
                    )
                },
                placeholder = { Text(strings.composeRecipientPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_recipient_input")
            )

            if (!hasContactsPermission) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.composeContactsGrantHint,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRequestPermissions) {
                            Text(strings.grantPermissionBtn)
                        }
                    }
                }
            } else if (filteredContacts.isNotEmpty() && selectedContactName == null) {
                Text(
                    text = strings.composeContactsHeader,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 155.dp)
                ) {
                    LazyColumn {
                        items(filteredContacts, key = { "${it.id}_${it.normalizedKey}" }) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        recipient = contact.phoneNumber
                                        selectedContactName = contact.name
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SenderAvatar(
                                    senderDisplay = contact.phoneNumber,
                                    senderType = SenderType.PHONE_NUMBER,
                                    contactName = contact.name,
                                    photoUri = contact.photoUri,
                                    modifier = Modifier.size(36.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = contact.phoneNumber,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(strings.composeBodyLabel) },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_body_input")
            )

            val parts = SmsPartCalculator.calculateParts(body)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = PersianFormatters.formatDigits(
                        strings.smsPartInfo(body.length, parts),
                        strings.language
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(AppIcons.Attach, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (attachmentUri == null) {
                            if (strings.language == AppLanguage.EN) "Attach Photo" else "پیوست تصویر"
                        } else {
                            if (strings.language == AppLanguage.EN) "Attached" else "پیوست شد"
                        }
                    )
                }
            }

            Button(
                onClick = {
                    if (activeSims.size > 1) {
                        showSimDialog = true
                    } else {
                        onSendSms(recipient, body, attachmentUri, activeSims.firstOrNull())
                        onDismiss()
                    }
                },
                enabled = recipient.isNotBlank() && (body.isNotBlank() || attachmentUri != null),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("compose_send_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.composeSendBtn)
            }
        }
    }

    if (showSimDialog) {
        SimChooserDialog(
            sims = activeSims,
            onSelectSim = { sim ->
                showSimDialog = false
                onSendSms(recipient, body, attachmentUri, sim)
                onDismiss()
            },
            onDismiss = { showSimDialog = false }
        )
    }
}
