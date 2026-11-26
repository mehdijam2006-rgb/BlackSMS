package com.example.sms

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.telephony.TelephonyManager
import com.example.data.local.DeliveryStatus
import com.example.data.local.SettingsPreferences
import com.example.data.local.SmartSmsDatabase
import com.example.data.repository.SmartSmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Required WAP_PUSH_DELIVER receiver for Default SMS App MMS support.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.WAP_PUSH_DELIVER_ACTION") return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Re-sync MMS provider entries after system delivers PDU
                val records = DeviceSmsManager.readDeviceMessages(appContext, limit = 20)
                    .filter { it.isMms }
                if (records.isNotEmpty()) {
                    val db = SmartSmsDatabase.getInstance(appContext)
                    val prefs = SettingsPreferences(appContext)
                    val repo = SmartSmsRepository(
                        messageDao = db.messageDao(),
                        filterRuleDao = db.filterRuleDao(),
                        listEntryDao = db.listEntryDao(),
                        settingsPrefs = prefs
                    )
                    repo.syncDeviceRecords(records)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * Required RESPOND_VIA_MESSAGE service for Default SMS App quick responses.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val recipient = intent.data?.let { uri: Uri ->
                uri.schemeSpecificPart?.substringBefore('?')?.trim()
            }.orEmpty()
            val message = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (recipient.isNotBlank() && message.isNotBlank()) {
                val appCtx = applicationContext
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = SmartSmsDatabase.getInstance(appCtx)
                        val repo = SmartSmsRepository(
                            messageDao = db.messageDao(),
                            filterRuleDao = db.filterRuleDao(),
                            listEntryDao = db.listEntryDao(),
                            settingsPrefs = SettingsPreferences(appCtx)
                        )
                        val messageId = repo.recordOutgoingMessage(
                            recipientRaw = recipient,
                            body = message,
                            deliveryStatus = DeliveryStatus.SENDING
                        )
                        if (messageId > 0L) {
                            val outcome = DeviceSmsManager.sendRealSms(
                                context = appCtx,
                                recipient = recipient,
                                body = message,
                                messageId = messageId,
                                subscriptionId = null,
                                requestDeliveryReport = true
                            )
                            outcome.systemSmsId?.let { repo.attachSystemSmsId(messageId, it) }
                            if (!outcome.sentDirectly) {
                                repo.updateDeliveryStatus(messageId, DeliveryStatus.FAILED)
                            }
                        }
                    } finally {
                        stopSelf(startId)
                    }
                }
                return START_NOT_STICKY
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
