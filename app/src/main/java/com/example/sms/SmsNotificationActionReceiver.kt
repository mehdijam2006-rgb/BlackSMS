package com.example.sms

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.data.local.DeliveryStatus
import com.example.data.local.SettingsPreferences
import com.example.data.local.SmartSmsDatabase
import com.example.data.repository.SmartSmsRepository
import com.example.util.SmsNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles "Mark as read" (مطالعه کردم) and inline "Reply" (جواب) notification actions.
 */
class SmsNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val threadKey = intent.getStringExtra(SmsNotificationHelper.EXTRA_THREAD_KEY).orEmpty()
        val senderRaw = intent.getStringExtra(SmsNotificationHelper.EXTRA_SENDER_RAW).orEmpty()
        val notificationId = intent.getIntExtra(SmsNotificationHelper.EXTRA_NOTIFICATION_ID, -1)

        if (action != SmsNotificationHelper.ACTION_MARK_READ &&
            action != SmsNotificationHelper.ACTION_REPLY
        ) {
            return
        }

        val appContext = context.applicationContext
        val replyText = if (action == SmsNotificationHelper.ACTION_REPLY) {
            RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(SmsNotificationHelper.KEY_TEXT_REPLY)
                ?.toString()
                ?.trim()
                .orEmpty()
        } else {
            ""
        }

        val pendingResult = goAsync()
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

                if (threadKey.isNotBlank()) {
                    repo.markThreadRead(threadKey)
                }
                if (senderRaw.isNotBlank()) {
                    DeviceSmsManager.markSenderReadInSystem(appContext, senderRaw)
                }

                if (action == SmsNotificationHelper.ACTION_REPLY &&
                    replyText.isNotBlank() &&
                    senderRaw.isNotBlank()
                ) {
                    val settings = prefs.getSettingsOnce()
                    val messageId = repo.recordOutgoingMessage(
                        recipientRaw = senderRaw,
                        body = replyText,
                        deliveryStatus = DeliveryStatus.SENDING
                    )
                    if (messageId > 0L) {
                        val outcome = DeviceSmsManager.sendRealSms(
                            context = appContext,
                            recipient = senderRaw,
                            body = replyText,
                            messageId = messageId,
                            subscriptionId = null,
                            requestDeliveryReport = settings.deliveryReportsEnabled
                        )
                        outcome.systemSmsId?.let { repo.attachSystemSmsId(messageId, it) }
                        if (!outcome.sentDirectly) {
                            repo.updateDeliveryStatus(messageId, DeliveryStatus.FAILED)
                        }
                    }
                }

                if (notificationId >= 0) {
                    val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    manager?.cancel(notificationId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
