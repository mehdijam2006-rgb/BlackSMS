package com.example.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.DeliveryStatus
import com.example.data.local.SmartSmsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsStatusReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_SENT = "com.example.sms.ACTION_SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.example.sms.ACTION_SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId <= 0L) return

        val action = intent.action ?: return
        val resultCode = resultCode
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = SmartSmsDatabase.getInstance(context.applicationContext).messageDao()
                when (action) {
                    ACTION_SMS_SENT -> {
                        val nextStatus = if (resultCode == Activity.RESULT_OK) {
                            DeliveryStatus.SENT
                        } else {
                            DeliveryStatus.FAILED
                        }
                        dao.updateDeliveryStatus(messageId, nextStatus)
                    }
                    ACTION_SMS_DELIVERED -> {
                        if (resultCode == Activity.RESULT_OK) {
                            dao.updateDeliveryStatus(messageId, DeliveryStatus.DELIVERED)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
