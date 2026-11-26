package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.AppSettings
import com.example.data.local.ListEntryEntity
import com.example.domain.model.SmartFilterLevel
import com.example.ui.components.AppIcons
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoseCrimson
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.AppThemeMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    isDefaultSmsApp: Boolean,
    hasSmsPermissions: Boolean,
    isSyncing: Boolean,
    blockedSenders: List<ListEntryEntity>,
    blockedPhrases: List<ListEntryEntity>,
    allowedSenders: List<ListEntryEntity>,
    autoBlockUnknown: Boolean,
    autoBlockPromoText: Boolean,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onRequestDefaultSms: () -> Unit,
    onRequestPermissions: () -> Unit,
    onSyncSms: () -> Unit,
    onSetSmartLevel: (SmartFilterLevel) -> Unit,
    onAddBlockedSender: (String) -> Unit,
    onAddBlockedPhrase: (String) -> Unit,
    onDeleteFilterEntry: (Long) -> Unit,
    onToggleAutoBlockUnknown: (Boolean) -> Unit,
    onToggleAutoBlockPromoText: (Boolean) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onSetDeliveryReportsEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalStrings.current

    var senderInput by remember { mutableStateOf("") }
    var phraseInput by remember { mutableStateOf("") }
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotifPermission = granted
            if (granted) onSetNotificationsEnabled(true)
        }
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Bilingual Language Selector (Persian / English)
            SettingsCard {
                SectionHeader(
                    icon = AppIcons.Language,
                    title = strings.settingsLanguageTitle,
                    gradient = listOf(NeonCyan, ElectricIndigo)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AppLanguage.entries.forEachIndexed { index, lang ->
                        SegmentedButton(
                            selected = settings.appLanguage == lang,
                            onClick = { onSetLanguage(lang) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AppLanguage.entries.size
                            ),
                            modifier = Modifier.testTag("lang_${lang.name.lowercase()}")
                        ) {
                            Text(lang.label)
                        }
                    }
                }
            }

            // 2. Theme Mode Selector (Obsidian Dark / Light / System)
            SettingsCard {
                SectionHeader(
                    icon = AppIcons.Palette,
                    title = strings.settingsThemeTitle,
                    gradient = listOf(SoftViolet, ElectricIndigo)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val themes = listOf(
                        AppThemeMode.DARK to strings.themeDark,
                        AppThemeMode.LIGHT to strings.themeLight,
                        AppThemeMode.SYSTEM to strings.themeSystem
                    )
                    themes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = themes.size
                            ),
                            modifier = Modifier.testTag("theme_${mode.name.lowercase()}")
                        ) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }

            // 3. Default SMS App & Phone Sync
            SettingsCard {
                SectionHeader(
                    icon = AppIcons.Shield,
                    title = strings.settingsDefaultSmsTitle,
                    gradient = if (isDefaultSmsApp) listOf(EmeraldMint, NeonCyan) else listOf(AmberGold, RoseCrimson)
                )
                Text(
                    text = if (isDefaultSmsApp) strings.settingsDefaultSmsActiveDesc else strings.settingsDefaultSmsInactiveDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isDefaultSmsApp) {
                        Button(
                            onClick = onRequestDefaultSms,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_set_default_btn")
                        ) {
                            Text(strings.setAsDefaultBtn)
                        }
                    } else {
                        Surface(
                            color = SafeEmerald.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SafeEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = strings.defaultSmsActive,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SafeEmerald
                                )
                            }
                        }
                    }

                    if (!hasSmsPermissions) {
                        OutlinedButton(
                            onClick = onRequestPermissions,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("settings_grant_perm_btn")
                        ) {
                            Text(strings.grantPermissionBtn)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onSyncSms,
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("settings_sync_sms_btn")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(strings.syncSmsBtn)
                        }
                    }
                }
            }

            // 4. Smart Filtering Level (Off / Normal / Strict)
            SettingsCard {
                SectionHeader(
                    icon = AppIcons.Spark,
                    title = strings.settingsSmartLevelTitle,
                    gradient = listOf(NeonCyan, SoftViolet)
                )
                Text(
                    text = strings.settingsSmartLevelSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val levelOptions = listOf(
                    Triple(SmartFilterLevel.OFF, strings.levelOffTitle, strings.levelOffDesc),
                    Triple(SmartFilterLevel.NORMAL, strings.levelNormalTitle, strings.levelNormalDesc),
                    Triple(SmartFilterLevel.STRICT, strings.levelStrictTitle, strings.levelStrictDesc)
                )

                levelOptions.forEach { (level, title, desc) ->
                    val selected = settings.smartFilterLevel == level
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = if (selected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        } else null,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetSmartLevel(level) }
                            .testTag("smart_level_${level.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onSetSmartLevel(level) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 5. Simple Number & Sender Title Filtering
            SettingsCard {
                SectionHeader(
                    icon = AppIcons.Block,
                    title = strings.settingsSenderFilterTitle,
                    gradient = listOf(RoseCrimson, AmberGold)
                )
                Text(
                    text = strings.settingsSenderFilterHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = senderInput,
                        onValueChange = { senderInput = it },
                        placeholder = { Text(strings.settingsSenderPlaceholder) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_sender_input")
                    )
                    FilledTonalButton(
                        onClick = {
                            if (senderInput.isNotBlank()) {
                                onAddBlockedSender(senderInput)
                                senderInput = ""
                            }
                        },
                        enabled = senderInput.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("settings_add_sender_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.addBtn)
                    }
                }

                if (blockedSenders.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        blockedSenders.forEach { entry ->
                            InputChip(
                                selected = false,
                                onClick = { onDeleteFilterEntry(entry.id) },
                                label = {
                                    Text(
                                        text = entry.value,
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = strings.deleteForever,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("blocked_sender_chip_${entry.id}")
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.settingsAutoBlockPromoText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoBlockPromoText,
                        onCheckedChange = onToggleAutoBlockPromoText,
                        modifier = Modifier.testTag("switch_auto_promo")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.settingsAutoBlockUnknown,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoBlockUnknown,
                        onCheckedChange = onToggleAutoBlockUnknown,
                        modifier = Modifier.testTag("switch_auto_unknown")
                    )
                }
            }

            // 6. Simple Phrase & Word Filtering
            SettingsCard {
                SectionHeader(
                    icon = AppIcons.Tag,
                    title = strings.settingsPhraseFilterTitle,
                    gradient = listOf(ElectricIndigo, NeonCyan)
                )
                Text(
                    text = strings.settingsPhraseFilterHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = phraseInput,
                        onValueChange = { phraseInput = it },
                        placeholder = { Text(strings.settingsPhrasePlaceholder) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_phrase_input")
                    )
                    FilledTonalButton(
                        onClick = {
                            if (phraseInput.isNotBlank()) {
                                onAddBlockedPhrase(phraseInput)
                                phraseInput = ""
                            }
                        },
                        enabled = phraseInput.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("settings_add_phrase_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.addBtn)
                    }
                }

                if (blockedPhrases.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        blockedPhrases.forEach { entry ->
                            InputChip(
                                selected = false,
                                onClick = { onDeleteFilterEntry(entry.id) },
                                label = { Text(entry.value) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = strings.deleteForever,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("blocked_phrase_chip_${entry.id}")
                            )
                        }
                    }
                }
            }

            // 7. Allowed Senders (shown cleanly if user added exceptions)
            if (allowedSenders.isNotEmpty()) {
                SettingsCard {
                    SectionHeader(
                        icon = AppIcons.Shield,
                        title = strings.settingsAllowedSendersTitle,
                        gradient = listOf(EmeraldMint, NeonCyan)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allowedSenders.forEach { entry ->
                            InputChip(
                                selected = true,
                                onClick = { onDeleteFilterEntry(entry.id) },
                                label = { Text(entry.value) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = strings.deleteForever,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 8. Silent Blocked Notifications Policy
            SettingsCard {
                SectionHeader(
                    icon = Icons.Default.Notifications,
                    title = strings.settingsNotificationsTitle,
                    gradient = listOf(AmberGold, ElectricIndigo)
                )
                Text(
                    text = strings.settingsNotificationsBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.settingsNotificationsToggle,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onSetNotificationsEnabled(enabled)
                            }
                        },
                        modifier = Modifier.testTag("switch_allowed_notifications")
                    )
                }
            }

            // 8b. SMS Delivery Reports (1-tick / 2-tick)
            SettingsCard {
                SectionHeader(
                    icon = AppIcons.DoneAll,
                    title = strings.settingsDeliveryReportsTitle,
                    gradient = listOf(NeonCyan, SafeEmerald)
                )
                Text(
                    text = strings.settingsDeliveryReportsDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.settingsDeliveryReportsToggle,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.deliveryReportsEnabled,
                        onCheckedChange = onSetDeliveryReportsEnabled,
                        modifier = Modifier.testTag("switch_delivery_reports")
                    )
                }
            }

            // 9. 100% Offline Privacy
            Card(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.25f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionHeader(
                        icon = Icons.Default.Lock,
                        title = strings.settingsPrivacyTitle,
                        gradient = listOf(NeonCyan, EmeraldMint)
                    )
                    Text(
                        text = strings.settingsPrivacyBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    gradient: List<Color>
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}
