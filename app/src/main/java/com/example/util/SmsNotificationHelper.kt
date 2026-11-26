package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.SenderClassifier
import com.example.sms.SmsNotificationActionReceiver
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.PersianStrings

/**
 * Ensures blocked messages NEVER trigger any notification on the phone,
 * while allowed messages show an interactive notification that opens the conversation
 * on tap and provides "Mark as read" (مطالعه کردم) and inline "Reply" (جواب) actions.
 */
object SmsNotificationHelper {
    private const val CHANNEL_ID = "allowed_sms_channel"

    const val ACTION_OPEN_THREAD = "com.example.sms.ACTION_OPEN_THREAD"
    const val ACTION_MARK_READ = "com.example.sms.ACTION_MARK_READ"
    const val ACTION_REPLY = "com.example.sms.ACTION_REPLY"

    const val EXTRA_THREAD_KEY = "extra_thread_key"
    const val EXTRA_SENDER_RAW = "extra_sender_raw"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val KEY_TEXT_REPLY = "key_text_reply"

    fun buildAllowedNotification(
        context: Context,
        senderDisplay: String,
        body: String,
        messageId: Int,
        senderRaw: String = senderDisplay,
        threadKey: String = SenderClassifier.extractThreadKey(senderRaw),
        language: AppLanguage = AppLanguage.FA
    ): android.app.Notification {
        val strings = if (language == AppLanguage.EN) EnglishStrings else PersianStrings
        val safeNotifId = if (messageId > 0) messageId else (threadKey.hashCode() and 0x7FFFFFFF)

        // 1. Tap notification -> open BlackSMS directly into this conversation
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_THREAD
            putExtra(EXTRA_THREAD_KEY, threadKey)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            safeNotifId * 10 + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. "Mark as read" / "مطالعه کردم" action
        val markReadIntent = Intent(context, SmsNotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_THREAD_KEY, threadKey)
            putExtra(EXTRA_SENDER_RAW, senderRaw)
            putExtra(EXTRA_NOTIFICATION_ID, safeNotifId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            safeNotifId * 10 + 2,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markReadAction = NotificationCompat.Action.Builder(
            0,
            strings.notifActionMarkRead,
            markReadPendingIntent
        ).build()

        // 3. Inline "Reply" / "جواب" action with RemoteInput
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(strings.notifReplyHint)
            .build()
        val replyIntent = Intent(context, SmsNotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_THREAD_KEY, threadKey)
            putExtra(EXTRA_SENDER_RAW, senderRaw)
            putExtra(EXTRA_NOTIFICATION_ID, safeNotifId)
        }
        val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            safeNotifId * 10 + 3,
            replyIntent,
            replyFlags
        )
        val replyAction = NotificationCompat.Action.Builder(
            0,
            strings.notifActionReply,
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderDisplay)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(markReadAction)
            .addAction(replyAction)
            .build()
    }

    fun notifyAllowedMessage(
        context: Context,
        senderDisplay: String,
        body: String,
        messageId: Int,
        notificationsEnabled: Boolean,
        senderRaw: String = senderDisplay,
        threadKey: String = SenderClassifier.extractThreadKey(senderRaw),
        language: AppLanguage = AppLanguage.FA
    ) {
        if (!notificationsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Allowed Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications only for messages allowed into the Inbox"
            }
            manager.createNotificationChannel(channel)
        }

        val safeNotifId = if (messageId > 0) messageId else (threadKey.hashCode() and 0x7FFFFFFF)
        val notification = buildAllowedNotification(
            context = context,
            senderDisplay = senderDisplay,
            body = body,
            messageId = safeNotifId,
            senderRaw = senderRaw,
            threadKey = threadKey,
            language = language
        )
        manager.notify(safeNotifId, notification)
    }

    /**
     * Explicit no-op guard for blocked messages: cancels any existing notification ID
     * and guarantees zero alerts, sounds, or banners are posted.
     */
    fun suppressBlockedNotification(context: Context, messageId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(messageId)
    }
}
