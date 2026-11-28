package com.example.sms

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SimCardInfo(
    val subscriptionId: Int,
    val slotIndex: Int, // 0-based from SubscriptionInfo, displayed as slotIndex + 1
    val displayName: String,
    val carrierName: String
)

data class SendSmsOutcome(
    val sentDirectly: Boolean,
    val systemSmsId: Long? = null
)

data class DeviceSmsRecord(
    val systemId: Long,
    val senderRaw: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isRead: Boolean,
    val isMms: Boolean = false,
    val attachmentUri: String? = null,
    val simSlot: Int = 1
)

object DeviceSmsManager {

    @SuppressLint("MissingPermission")
    fun getActiveSims(context: Context): List<SimCardInfo> {
        if (!DefaultSmsHelper.hasPhoneStatePermission(context)) return emptyList()
        return try {
            val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
            val list = subManager.activeSubscriptionInfoList.orEmpty()
            list.sortedBy { it.simSlotIndex }.map { info ->
                SimCardInfo(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    displayName = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: "SIM ${info.simSlotIndex + 1}",
                    carrierName = info.carrierName?.toString().orEmpty()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun buildSubIdToSlotMap(context: Context): Map<Int, Int> {
        val result = linkedMapOf<Int, Int>()
        if (!DefaultSmsHelper.hasPhoneStatePermission(context)) return result
        try {
            val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return result
            val allInfos = buildList {
                subManager.activeSubscriptionInfoList?.let { addAll(it) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching { subManager.completeActiveSubscriptionInfoList }.getOrNull()?.let { addAll(it) }
                }
                runCatching { subManager.accessibleSubscriptionInfoList }.getOrNull()?.let { addAll(it) }
                for (slot in 0..1) {
                    runCatching { subManager.getActiveSubscriptionInfoForSimSlotIndex(slot) }.getOrNull()?.let { add(it) }
                }
            }
            for (info in allInfos) {
                if (info.subscriptionId >= 0 && info.simSlotIndex >= 0) {
                    result[info.subscriptionId] = info.simSlotIndex + 1
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun resolveSimSlotFromSubscription(
        subId: Int?,
        subIdToSlotMap: Map<Int, Int>,
        fallbackDistinctMap: Map<Int, Int> = emptyMap(),
        explicitSlotIndex: Int? = null
    ): Int {
        if (explicitSlotIndex != null && explicitSlotIndex in 0..3) {
            return explicitSlotIndex + 1
        }
        if (subId != null && subId >= 0) {
            subIdToSlotMap[subId]?.let { return it }
            fallbackDistinctMap[subId]?.let { return it }
            if (subId in 1..2) return subId
        }
        return 1
    }

    fun resolveSimSlotFromIntent(context: Context, intent: Intent): Int {
        val extras = intent.extras ?: return 1
        val subIdToSlotMap = buildSubIdToSlotMap(context)

        val slotCandidates = listOf(
            "android.telephony.extra.SLOT_INDEX",
            "slot",
            "simSlot",
            "sim_slot",
            "phone",
            "sim_id"
        )
        var explicitSlot: Int? = null
        for (key in slotCandidates) {
            if (extras.containsKey(key)) {
                val raw = extras.getInt(key, -1)
                if (raw in 0..3) {
                    explicitSlot = raw
                    break
                }
            }
        }

        val subCandidates = listOf(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            "subscription",
            "sub_id"
        )
        var subId: Int? = null
        for (key in subCandidates) {
            if (extras.containsKey(key)) {
                val raw = extras.getInt(key, -1)
                if (raw >= 0) {
                    subId = raw
                    break
                }
            }
        }

        return resolveSimSlotFromSubscription(
            subId = subId,
            subIdToSlotMap = subIdToSlotMap,
            explicitSlotIndex = explicitSlot
        )
    }

    suspend fun readDeviceMessages(context: Context, limit: Int = 1000): List<DeviceSmsRecord> =
        withContext(Dispatchers.IO) {
            if (!DefaultSmsHelper.hasReadSmsPermission(context)) return@withContext emptyList()
            val results = mutableListOf<DeviceSmsRecord>()
            val resolver = context.contentResolver
            val subIdToSlotMap = buildSubIdToSlotMap(context)

            data class RawSms(
                val sysId: Long,
                val address: String,
                val body: String,
                val timestamp: Long,
                val isOutgoing: Boolean,
                val isRead: Boolean,
                val subId: Int?
            )

            val rawSmsList = mutableListOf<RawSms>()
            try {
                val fullProjection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ,
                    Telephony.Sms.SUBSCRIPTION_ID
                )
                val fallbackProjection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                )
                val cursor = try {
                    resolver.query(
                        Telephony.Sms.CONTENT_URI,
                        fullProjection,
                        null,
                        null,
                        "${Telephony.Sms.DATE} DESC LIMIT $limit"
                    )
                } catch (_: IllegalArgumentException) {
                    resolver.query(
                        Telephony.Sms.CONTENT_URI,
                        fallbackProjection,
                        null,
                        null,
                        "${Telephony.Sms.DATE} DESC LIMIT $limit"
                    )
                }

                cursor?.use { c ->
                    val idIdx = c.getColumnIndex(Telephony.Sms._ID)
                    val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                    val typeIdx = c.getColumnIndex(Telephony.Sms.TYPE)
                    val readIdx = c.getColumnIndex(Telephony.Sms.READ)
                    val subIdx = c.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                    while (c.moveToNext()) {
                        val sysId = if (idIdx >= 0) c.getLongOrNull(idIdx) ?: continue else continue
                        val address = if (addrIdx >= 0) c.getStringOrNull(addrIdx).orEmpty() else ""
                        val body = if (bodyIdx >= 0) c.getStringOrNull(bodyIdx).orEmpty() else ""
                        val rawDate = if (dateIdx >= 0) c.getLongOrNull(dateIdx) ?: System.currentTimeMillis() else System.currentTimeMillis()
                        val date = if (rawDate in 1L until 10_000_000_000L) rawDate * 1000L else rawDate
                        val type = if (typeIdx >= 0) c.getIntOrNull(typeIdx) ?: Telephony.Sms.MESSAGE_TYPE_INBOX else Telephony.Sms.MESSAGE_TYPE_INBOX
                        val read = if (readIdx >= 0) (c.getIntOrNull(readIdx) ?: 1) == 1 else true
                        val subId = if (subIdx >= 0) c.getIntOrNull(subIdx)?.takeIf { it >= 0 } else null

                        if (body.isBlank()) continue
                        val isOutgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                            type == Telephony.Sms.MESSAGE_TYPE_QUEUED

                        rawSmsList += RawSms(
                            sysId = sysId,
                            address = address,
                            body = body,
                            timestamp = date,
                            isOutgoing = isOutgoing,
                            isRead = read,
                            subId = subId
                        )
                    }
                }
            } catch (_: Exception) {
            }

            data class RawMms(
                val mmsId: Long,
                val mmsAddr: String,
                val finalBody: String,
                val timestamp: Long,
                val isOutgoing: Boolean,
                val isRead: Boolean,
                val imageUri: String?,
                val subId: Int?
            )

            val rawMmsList = mutableListOf<RawMms>()
            try {
                val mmsProjFull = arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.READ,
                    Telephony.Mms.SUBSCRIPTION_ID
                )
                val mmsProjFallback = arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.READ
                )
                val cursor = try {
                    resolver.query(
                        Telephony.Mms.CONTENT_URI,
                        mmsProjFull,
                        null,
                        null,
                        "${Telephony.Mms.DATE} DESC LIMIT 100"
                    )
                } catch (_: IllegalArgumentException) {
                    resolver.query(
                        Telephony.Mms.CONTENT_URI,
                        mmsProjFallback,
                        null,
                        null,
                        "${Telephony.Mms.DATE} DESC LIMIT 100"
                    )
                }

                cursor?.use { c ->
                    val idIdx = c.getColumnIndex(Telephony.Mms._ID)
                    val dateIdx = c.getColumnIndex(Telephony.Mms.DATE)
                    val boxIdx = c.getColumnIndex(Telephony.Mms.MESSAGE_BOX)
                    val readIdx = c.getColumnIndex(Telephony.Mms.READ)
                    val subIdx = c.getColumnIndex(Telephony.Mms.SUBSCRIPTION_ID)

                    while (c.moveToNext()) {
                        val mmsId = if (idIdx >= 0) c.getLongOrNull(idIdx) ?: continue else continue
                        val rawDate = if (dateIdx >= 0) c.getLongOrNull(dateIdx) ?: 0L else 0L
                        val ts = if (rawDate in 1L until 10_000_000_000L) rawDate * 1000L else rawDate
                        val box = if (boxIdx >= 0) c.getIntOrNull(boxIdx) ?: Telephony.Mms.MESSAGE_BOX_INBOX else Telephony.Mms.MESSAGE_BOX_INBOX
                        val read = if (readIdx >= 0) (c.getIntOrNull(readIdx) ?: 1) == 1 else true
                        val subId = if (subIdx >= 0) c.getIntOrNull(subIdx)?.takeIf { it >= 0 } else null

                        val mmsAddr = queryMmsSender(context, mmsId)
                        val (mmsBody, mmsImageUri) = queryMmsParts(context, mmsId)
                        val finalBody = mmsBody.ifBlank { if (mmsImageUri != null) "" else "[MMS]" }

                        rawMmsList += RawMms(
                            mmsId = mmsId,
                            mmsAddr = mmsAddr,
                            finalBody = finalBody,
                            timestamp = ts,
                            isOutgoing = box == Telephony.Mms.MESSAGE_BOX_SENT,
                            isRead = read,
                            imageUri = mmsImageUri,
                            subId = subId
                        )
                    }
                }
            } catch (_: Exception) {
            }

            // If READ_PHONE_STATE is unavailable (subIdToSlotMap empty), infer SIM 1 vs SIM 2 deterministically from distinct observed sub_ids
            val distinctUnmappedSubIds = (rawSmsList.mapNotNull { it.subId } + rawMmsList.mapNotNull { it.subId })
                .distinct()
                .sorted()
            val fallbackDistinctMap = if (subIdToSlotMap.isEmpty() && distinctUnmappedSubIds.size >= 2) {
                distinctUnmappedSubIds.mapIndexed { index, subId -> subId to (index + 1) }.toMap()
            } else {
                emptyMap()
            }

            for (raw in rawSmsList) {
                results += DeviceSmsRecord(
                    systemId = raw.sysId,
                    senderRaw = raw.address,
                    body = raw.body,
                    timestamp = raw.timestamp,
                    isOutgoing = raw.isOutgoing,
                    isRead = raw.isRead,
                    isMms = false,
                    simSlot = resolveSimSlotFromSubscription(raw.subId, subIdToSlotMap, fallbackDistinctMap)
                )
            }
            for (raw in rawMmsList) {
                results += DeviceSmsRecord(
                    systemId = -raw.mmsId,
                    senderRaw = raw.mmsAddr,
                    body = raw.finalBody,
                    timestamp = raw.timestamp,
                    isOutgoing = raw.isOutgoing,
                    isRead = raw.isRead,
                    isMms = true,
                    attachmentUri = raw.imageUri,
                    simSlot = resolveSimSlotFromSubscription(raw.subId, subIdToSlotMap, fallbackDistinctMap)
                )
            }

            results.sortedBy { it.timestamp }
        }

    private fun queryMmsSender(context: Context, mmsId: Long): String {
        return try {
            val uri = Uri.parse("content://mms/$mmsId/addr")
            context.contentResolver.query(uri, arrayOf("address", "type"), null, null, null)?.use { c ->
                val addrIdx = c.getColumnIndex("address")
                val typeIdx = c.getColumnIndex("type")
                var fallback = ""
                while (c.moveToNext()) {
                    val addr = if (addrIdx >= 0) c.getStringOrNull(addrIdx).orEmpty() else ""
                    val type = if (typeIdx >= 0) c.getIntOrNull(typeIdx) ?: 0 else 0
                    if (addr.isNotBlank() && addr != "insert-address-token") {
                        if (type == 137) return addr
                        fallback = addr
                    }
                }
                fallback
            }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun queryMmsParts(context: Context, mmsId: Long): Pair<String, String?> {
        return try {
            val uri = Uri.parse("content://mms/part")
            var textBody = ""
            var imageUri: String? = null
            context.contentResolver.query(
                uri,
                arrayOf("_id", "ct", "text"),
                "mid = ?",
                arrayOf(mmsId.toString()),
                null
            )?.use { c ->
                val idIdx = c.getColumnIndex("_id")
                val ctIdx = c.getColumnIndex("ct")
                val textIdx = c.getColumnIndex("text")
                while (c.moveToNext()) {
                    val partId = if (idIdx >= 0) c.getLongOrNull(idIdx) else null
                    val ct = if (ctIdx >= 0) c.getStringOrNull(ctIdx).orEmpty() else ""
                    if (ct == "text/plain" && textBody.isBlank()) {
                        val txt = if (textIdx >= 0) c.getStringOrNull(textIdx).orEmpty() else ""
                        if (txt.isNotBlank()) textBody = txt
                    } else if (ct.startsWith("image/") && partId != null && imageUri == null) {
                        imageUri = "content://mms/part/$partId"
                    }
                }
            }
            textBody to imageUri
        } catch (_: Exception) {
            "" to null
        }
    }

    private fun resolveSmsManager(context: Context, subscriptionId: Int?): SmsManager? {
        return if (subscriptionId != null && subscriptionId >= 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)?.createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }

    fun sendRealSms(
        context: Context,
        recipient: String,
        body: String,
        messageId: Long,
        subscriptionId: Int? = null,
        requestDeliveryReport: Boolean = true
    ): SendSmsOutcome {
        val cleanRecipient = recipient.trim()
        if (cleanRecipient.isEmpty() || body.isEmpty()) return SendSmsOutcome(false)
        if (!DefaultSmsHelper.hasSendSmsPermission(context)) return SendSmsOutcome(false)

        return try {
            val smsManager = resolveSmsManager(context, subscriptionId) ?: return SendSmsOutcome(false)

            val sentPendingIntent = PendingIntent.getBroadcast(
                context,
                (messageId * 2).toInt(),
                Intent(context, SmsStatusReceiver::class.java).apply {
                    action = SmsStatusReceiver.ACTION_SMS_SENT
                    putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val deliveryPendingIntent = if (requestDeliveryReport) {
                PendingIntent.getBroadcast(
                    context,
                    (messageId * 2 + 1).toInt(),
                    Intent(context, SmsStatusReceiver::class.java).apply {
                        action = SmsStatusReceiver.ACTION_SMS_DELIVERED
                        putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                null
            }

            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent?>(parts.size)
                val deliveryIntents = if (deliveryPendingIntent != null) {
                    ArrayList<PendingIntent?>(parts.size)
                } else {
                    null
                }
                repeat(parts.size) { idx ->
                    sentIntents.add(if (idx == parts.lastIndex) sentPendingIntent else null)
                    deliveryIntents?.add(if (idx == parts.lastIndex) deliveryPendingIntent else null)
                }
                smsManager.sendMultipartTextMessage(
                    cleanRecipient,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                smsManager.sendTextMessage(
                    cleanRecipient,
                    null,
                    body,
                    sentPendingIntent,
                    deliveryPendingIntent
                )
            }

            val sysId = if (DefaultSmsHelper.isDefaultSmsApp(context)) {
                writeSentToSystemProvider(
                    context = context,
                    recipient = cleanRecipient,
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    subscriptionId = subscriptionId
                )
            } else {
                null
            }
            SendSmsOutcome(sentDirectly = true, systemSmsId = sysId)
        } catch (_: Exception) {
            SendSmsOutcome(sentDirectly = false)
        }
    }

    fun writeAllowedIncomingToSystemInbox(
        context: Context,
        senderRaw: String,
        body: String,
        timestamp: Long,
        subscriptionId: Int? = null
    ): Long? {
        if (!DefaultSmsHelper.isDefaultSmsApp(context)) return null
        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, senderRaw)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                if (subscriptionId != null && subscriptionId >= 0) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
                }
            }
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)?.let { uri ->
                ContentUris.parseId(uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeSentToSystemProvider(
        context: Context,
        recipient: String,
        body: String,
        timestamp: Long,
        subscriptionId: Int? = null
    ): Long? {
        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, recipient)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                if (subscriptionId != null && subscriptionId >= 0) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
                }
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)?.let { uri ->
                ContentUris.parseId(uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun markSenderReadInSystem(context: Context, senderRaw: String) {
        if (!DefaultSmsHelper.isDefaultSmsApp(context) || senderRaw.isBlank()) return
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.Inbox.CONTENT_URI,
                values,
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(senderRaw)
            )
        } catch (_: Exception) {
        }
    }
}
