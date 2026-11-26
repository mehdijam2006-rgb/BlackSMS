package com.example.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.data.local.SettingsPreferences
import com.example.data.local.SmartSmsDatabase
import com.example.data.repository.SmartSmsRepository
import com.example.domain.model.SenderClassifier
import com.example.util.SmsNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Primary SMS_DELIVER receiver when BlackSMS is the Default SMS App.
 * Evaluates incoming SMS (including multipart and alphanumeric sender IDs) BEFORE
 * writing to Inbox or notifying. Blocked messages stay strictly silent and never enter Inbox.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        handleIncomingSmsIntent(context.applicationContext, intent, writeToSystemInbox = true, goAsync())
    }
}

/**
 * Fallback SMS_RECEIVED receiver when BlackSMS is not yet the Default SMS App.
 * Skips execution if BlackSMS is already the Default SMS App to avoid duplicate handling.
 */
class SmsFallbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (DefaultSmsHelper.isDefaultSmsApp(context)) return
        handleIncomingSmsIntent(context.applicationContext, intent, writeToSystemInbox = false, goAsync())
    }
}

private fun handleIncomingSmsIntent(
    appContext: Context,
    intent: Intent,
    writeToSystemInbox: Boolean,
    pendingResult: BroadcastReceiver.PendingResult
) {
    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
    if (messages.isNullOrEmpty()) {
        pendingResult.finish()
        return
    }

    // Group multipart SMS segments by raw originating address (preserving text Sender IDs)
    val grouped = linkedMapOf<String, StringBuilder>()
    var timestamp = System.currentTimeMillis()
    for (sms in messages) {
        val rawSender = sms.originatingAddress ?: sms.displayOriginatingAddress ?: ""
        timestamp = sms.timestampMillis.takeIf { it > 0L } ?: timestamp
        grouped.getOrPut(rawSender) { StringBuilder() }.append(sms.messageBody.orEmpty())
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val db = SmartSmsDatabase.getInstance(appContext)
            val prefs = SettingsPreferences(appContext)
            val repo = SmartSmsRepository(
                messageDao = db.messageDao(),
                filterRuleDao = db.filterRuleDao(),
                listEntryDao = db.listEntryDao(),
                settingsPrefs = prefs
            )
            val appSettings = prefs.getSettingsOnce()

            for ((senderRaw, bodyBuilder) in grouped) {
                val body = bodyBuilder.toString()
                val result = repo.processAndStoreIncomingMessage(
                    senderRaw = senderRaw,
                    body = body,
                    customTimestamp = timestamp
                ) ?: continue

                if (result.decision.shouldBlock) {
                    // Blocked message: NO notification, NO sound, NOT added to system Inbox
                    SmsNotificationHelper.suppressBlockedNotification(appContext, result.messageId.toInt())
                } else {
                    if (writeToSystemInbox) {
                        val systemSmsId = DeviceSmsManager.writeAllowedIncomingToSystemInbox(
                            context = appContext,
                            senderRaw = senderRaw,
                            body = body,
                            timestamp = timestamp
                        )
                        if (systemSmsId != null) {
                            repo.attachSystemSmsId(result.messageId, systemSmsId)
                        }
                    }
                    val contactTitle = if (DefaultSmsHelper.hasContactsPermission(appContext)) {
                        val lookup = DeviceContactsManager.buildLookupMap(
                            DeviceContactsManager.loadContacts(appContext)
                        )
                        DeviceContactsManager.resolveSender(senderRaw, lookup)?.name
                    } else {
                        null
                    }
                    SmsNotificationHelper.notifyAllowedMessage(
                        context = appContext,
                        senderDisplay = contactTitle?.takeIf { it.isNotBlank() } ?: result.senderDisplay,
                        body = result.body,
                        messageId = result.messageId.toInt(),
                        notificationsEnabled = appSettings.notificationsEnabled,
                        senderRaw = senderRaw,
                        threadKey = SenderClassifier.extractThreadKey(senderRaw),
                        language = appSettings.appLanguage
                    )
                }
            }
        } finally {
            pendingResult.finish()
        }
    }
}
