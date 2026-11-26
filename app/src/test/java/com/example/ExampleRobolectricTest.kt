package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DeliveryStatus
import com.example.data.local.SettingsPreferences
import com.example.data.local.SmartSmsDatabase
import com.example.data.repository.SmartSmsRepository
import com.example.domain.model.BlockSource
import com.example.domain.model.OtpExtractor
import com.example.domain.model.SenderType
import com.example.domain.model.SmartFilterLevel
import com.example.sms.DeviceContactsManager
import com.example.sms.DeviceSmsRecord
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.AppThemeMode
import com.example.util.SmsNotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: SmartSmsDatabase
    private lateinit var repository: SmartSmsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SmartSmsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefs = SettingsPreferences(context)
        repository = SmartSmsRepository(
            messageDao = db.messageDao(),
            filterRuleDao = db.filterRuleDao(),
            listEntryDao = db.listEntryDao(),
            settingsPrefs = prefs
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app_name string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("BlackSMS", appName)
    }

    @Test
    fun `initial database has zero demo or sample messages`() = runBlocking {
        repository.ensureSeeded()
        assertTrue(repository.inboxThreads.first().isEmpty())
        assertTrue(repository.blockedMessages.first().isEmpty())
    }

    @Test
    fun `blocking a sender from inbox automatically moves all their messages to spam folder`() = runBlocking {
        repository.ensureSeeded()
        val first = repository.processAndStoreIncomingMessage(
            senderRaw = "+989121234567",
            body = "سلام پیام اول"
        )
        val second = repository.processAndStoreIncomingMessage(
            senderRaw = "+989121234567",
            body = "سلام پیام دوم"
        )
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(1, repository.inboxThreads.first().size)
        assertEquals(0, repository.blockedMessages.first().size)

        // User marks sender as spam from Inbox
        repository.addSenderToBlacklist("+989121234567", moveExistingToBlocked = true)

        assertTrue(repository.inboxThreads.first().isEmpty())
        val blocked = repository.blockedMessages.first()
        assertEquals(2, blocked.size)
        assertTrue(blocked.all { it.isBlocked })
    }

    @Test
    fun `inbox threads and conversation messages are sorted strictly by date and time`() = runBlocking {
        repository.ensureSeeded()
        repository.syncDeviceRecords(
            listOf(
                DeviceSmsRecord(
                    systemId = 1L,
                    senderRaw = "+989121111111",
                    body = "قدیمی‌تر",
                    timestamp = 1_700_000_000_000L,
                    isOutgoing = false,
                    isRead = true
                ),
                DeviceSmsRecord(
                    systemId = 2L,
                    senderRaw = "+989122222222",
                    body = "جدیدتر",
                    timestamp = 1_700_000_500_000L,
                    isOutgoing = false,
                    isRead = true
                ),
                DeviceSmsRecord(
                    systemId = 3L,
                    senderRaw = "+989121111111",
                    body = "پاسخ من",
                    timestamp = 1_700_000_100_000L,
                    isOutgoing = true,
                    isRead = true
                )
            )
        )

        val threads = repository.inboxThreads.first()
        assertEquals(2, threads.size)
        assertEquals("+989122222222", threads[0].senderDisplay)
        assertEquals("+989121111111", threads[1].senderDisplay)

        // Pinning moves a thread to the top, followed by newest timestamp
        repository.setThreadPinned(threads[1].threadKey, true)
        val pinnedThreads = repository.inboxThreads.first()
        assertEquals("+989121111111", pinnedThreads[0].senderDisplay)

        // Messages inside a thread are strictly oldest -> newest (ASC)
        val chat = repository.observeThreadMessages(threads[1].threadKey).first()
        assertEquals(2, chat.size)
        assertTrue(chat[0].timestamp <= chat[1].timestamp)
        assertEquals("قدیمی‌تر", chat[0].body)
        assertEquals("پاسخ من", chat[1].body)
    }

    @Test
    fun `otp extractor and contact lookup resolve Persian digits and phone numbers`() {
        assertEquals("482910", OtpExtractor.extractOtp("کد تایید ورود شما: ۴۸۲۹۱۰"))
        assertEquals("928174", OtpExtractor.extractOtp("Your verification code is 928174"))
        assertEquals("09123456789", DeviceContactsManager.normalizePhoneLookupKey("+98 912 345 6789"))
    }

    @Test
    fun `syncDeviceRecords filters device SMS into inbox and blocked vault silently`() = runBlocking {
        repository.ensureSeeded()
        repository.addBlockedPhrase("جایزه ویژه")

        val synced = repository.syncDeviceRecords(
            listOf(
                DeviceSmsRecord(
                    systemId = 101L,
                    senderRaw = "AD-SMS",
                    body = "جشنواره فروش ویژه",
                    timestamp = 1_700_000_000_000L,
                    isOutgoing = false,
                    isRead = false
                ),
                DeviceSmsRecord(
                    systemId = 102L,
                    senderRaw = "+989121112233",
                    body = "سلام، ساعت پنج جلسه داریم",
                    timestamp = 1_700_000_060_000L,
                    isOutgoing = false,
                    isRead = true
                ),
                DeviceSmsRecord(
                    systemId = 103L,
                    senderRaw = "90001122",
                    body = "شما برنده جایزه ویژه شدید",
                    timestamp = 1_700_000_120_000L,
                    isOutgoing = false,
                    isRead = false
                )
            )
        )

        assertEquals(3, synced)
        val inbox = repository.inboxThreads.first()
        val blocked = repository.blockedMessages.first()
        assertEquals(1, inbox.size)
        assertEquals("+989121112233", inbox.first().senderDisplay)
        assertEquals(2, blocked.size)
        assertTrue(blocked.any { it.senderRaw == "AD-SMS" })
    }

    @Test
    fun `alphanumeric sender and phrase filters block messages silently with zero notifications`() = runBlocking {
        repository.ensureSeeded()
        repository.addSenderToBlacklist("SPAM-BRAND")
        repository.addBlockedPhrase("پیشنهاد ویژه")

        val bySender = repository.processAndStoreIncomingMessage(
            senderRaw = "SPAM-BRAND",
            body = "سلام وقت بخیر"
        )
        assertNotNull(bySender)
        assertTrue(bySender!!.decision.shouldBlock)
        SmsNotificationHelper.suppressBlockedNotification(context, bySender.messageId.toInt())

        val byPhrase = repository.processAndStoreIncomingMessage(
            senderRaw = "+989120009988",
            body = "یک پیشنهاد ویژه برای شما داریم"
        )
        assertNotNull(byPhrase)
        assertTrue(byPhrase!!.decision.shouldBlock)
        SmsNotificationHelper.suppressBlockedNotification(context, byPhrase.messageId.toInt())

        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(0, Shadows.shadowOf(notifManager).allNotifications.size)

        val blocked = repository.blockedMessages.first()
        assertTrue(blocked.any { it.senderRaw == "SPAM-BRAND" && it.senderType == SenderType.ALPHANUMERIC })
        assertTrue(blocked.any { it.body.contains("پیشنهاد ویژه") })

        val inbox = repository.inboxThreads.first()
        assertFalse(inbox.any { it.senderDisplay == "SPAM-BRAND" })
    }

    @Test
    fun `restore and whitelist moves blocked message to inbox and allows future messages`() = runBlocking {
        repository.ensureSeeded()
        repository.setSmartFilterLevel(SmartFilterLevel.NORMAL)

        val blockedResult = repository.processAndStoreIncomingMessage(
            senderRaw = "MY-BANK-ALERT",
            body = "فوری: جهت بروزرسانی حساب کلیک کنید http://b2n.ir/test"
        )
        assertNotNull(blockedResult)
        assertTrue(blockedResult!!.decision.shouldBlock)
        assertEquals(BlockSource.SMART_ENGINE, blockedResult.decision.blockSource)

        repository.restoreBlockedMessage(blockedResult.messageId, alsoWhitelistSender = true)

        val inboxThreads = repository.inboxThreads.first()
        assertTrue(inboxThreads.any { it.senderDisplay == "MY-BANK-ALERT" })

        val nextResult = repository.processAndStoreIncomingMessage(
            senderRaw = "MY-BANK-ALERT",
            body = "اطلاع‌رسانی مجدد http://b2n.ir/next"
        )
        assertFalse(nextResult!!.decision.shouldBlock)
        assertTrue(nextResult.decision.whitelisted)
    }

    @Test
    fun `language and theme mode settings persist cleanly`() = runBlocking {
        assertEquals(AppLanguage.FA, repository.settings.first().appLanguage)
        assertEquals(AppThemeMode.DARK, repository.settings.first().themeMode)

        repository.setAppLanguage(AppLanguage.EN)
        repository.setThemeMode(AppThemeMode.LIGHT)

        val updated = repository.settings.first()
        assertEquals(AppLanguage.EN, updated.appLanguage)
        assertEquals(AppThemeMode.LIGHT, updated.themeMode)
    }

    @Test
    fun `outgoing and incoming messages are never duplicated when ContentObserver triggers syncDeviceRecords`() = runBlocking {
        repository.ensureSeeded()
        val now = System.currentTimeMillis()

        // 1. User sends an SMS (inserted locally with null systemSmsId first)
        val outgoingId = repository.recordOutgoingMessage(
            recipientRaw = "09123456789",
            body = "سلام خوبی؟",
            simSlot = 2,
            deliveryStatus = DeliveryStatus.SENDING
        )
        assertTrue(outgoingId > 0L)

        // 2. User receives an SMS via SmsDeliverReceiver
        val incomingResult = repository.processAndStoreIncomingMessage(
            senderRaw = "+989123456789",
            body = "ممنون تو چطوری؟",
            customTimestamp = now + 2_000L
        )
        assertNotNull(incomingResult)

        // 3. System Telephony provider emits ContentObserver -> syncDeviceRecords runs with same messages
        val imported = repository.syncDeviceRecords(
            listOf(
                DeviceSmsRecord(
                    systemId = 5001L,
                    senderRaw = "+989123456789",
                    body = "سلام خوبی؟",
                    timestamp = now + 500L,
                    isOutgoing = true,
                    isRead = true
                ),
                DeviceSmsRecord(
                    systemId = 5002L,
                    senderRaw = "09123456789",
                    body = "ممنون تو چطوری؟",
                    timestamp = now + 2_500L,
                    isOutgoing = false,
                    isRead = false
                )
            )
        )

        assertEquals(0, imported)
        val threadKey = com.example.domain.model.SenderClassifier.extractThreadKey("09123456789")
        val chat = repository.observeThreadMessages(threadKey).first()
        assertEquals(2, chat.size)
        assertEquals(5001L, chat[0].systemSmsId)
        assertEquals(5002L, chat[1].systemSmsId)
    }

    @Test
    fun `delivery status updates from SENDING to SENT and DELIVERED and preference persists`() = runBlocking {
        repository.ensureSeeded()
        assertTrue(repository.settings.first().deliveryReportsEnabled)

        repository.setDeliveryReportsEnabled(false)
        assertFalse(repository.settings.first().deliveryReportsEnabled)
        repository.setDeliveryReportsEnabled(true)
        assertTrue(repository.settings.first().deliveryReportsEnabled)

        val msgId = repository.recordOutgoingMessage(
            recipientRaw = "+989120001122",
            body = "تست دلیوری",
            deliveryStatus = DeliveryStatus.SENDING
        )
        val threadKey = com.example.domain.model.SenderClassifier.extractThreadKey("+989120001122")
        assertEquals(DeliveryStatus.SENDING, repository.observeThreadMessages(threadKey).first().single().deliveryStatus)

        repository.updateDeliveryStatus(msgId, DeliveryStatus.SENT)
        assertEquals(DeliveryStatus.SENT, repository.observeThreadMessages(threadKey).first().single().deliveryStatus)

        repository.updateDeliveryStatus(msgId, DeliveryStatus.DELIVERED)
        assertEquals(DeliveryStatus.DELIVERED, repository.observeThreadMessages(threadKey).first().single().deliveryStatus)
    }

    @Test
    fun `allowed message notification opens app and includes mark-read and inline reply actions`() {
        val notification = SmsNotificationHelper.buildAllowedNotification(
            context = context,
            senderDisplay = "علی رضایی",
            body = "سلام، کجایی؟",
            messageId = 42,
            senderRaw = "+989123456789",
            language = AppLanguage.FA
        )

        assertNotNull(notification.contentIntent)
        val launchedIntent = Shadows.shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, launchedIntent.component?.className)
        assertEquals(SmsNotificationHelper.ACTION_OPEN_THREAD, launchedIntent.action)
        assertNotNull(launchedIntent.getStringExtra(SmsNotificationHelper.EXTRA_THREAD_KEY))

        val actions = notification.actions
        assertNotNull(actions)
        assertEquals(2, actions!!.size)
        assertEquals("مطالعه کردم", actions[0].title.toString())
        assertEquals("جواب", actions[1].title.toString())
        assertNotNull(actions[1].remoteInputs)
        assertEquals(SmsNotificationHelper.KEY_TEXT_REPLY, actions[1].remoteInputs!!.first().resultKey)
    }

    @Test
    fun `adaptive engine learns from user-blocked messages and auto-blocks similar future spam`() = runBlocking {
        repository.ensureSeeded()
        repository.setSmartFilterLevel(SmartFilterLevel.NORMAL)

        // Two similar spam messages from completely different normal mobile numbers, manually blocked by user
        val m1 = repository.processAndStoreIncomingMessage(
            senderRaw = "+989121000001",
            body = "مشترک گرامی بسته اینترنت رایگان ۵۰ گیگ برای خط شما فعال شد جهت دریافت عدد ۵ را ارسال نمایید"
        )!!
        val m2 = repository.processAndStoreIncomingMessage(
            senderRaw = "+989121000002",
            body = "مشترک گرامی بسته اینترنت رایگان ۱۰۰ گیگ برای خط شما فعال شد جهت دریافت عدد ۹ را ارسال نمایید"
        )!!
        repository.blockSingleMessage(m1.messageId, alsoBlacklistSender = false)
        repository.blockSingleMessage(m2.messageId, alsoBlacklistSender = false)

        // A 3rd similar message arrives from a brand-new unseen number
        val m3 = repository.processAndStoreIncomingMessage(
            senderRaw = "+989359998877",
            body = "مشترک گرامی بسته اینترنت رایگان ۲۰ گیگ برای خط شما فعال شد جهت دریافت عدد ۲ را ارسال نمایید"
        )!!
        assertTrue(m3.decision.shouldBlock)
        assertEquals(BlockSource.SMART_ENGINE, m3.decision.blockSource)

        // Unseen blocked count resets cleanly when the Spam folder is opened
        val totalBlocked = repository.blockedCount.first()
        assertTrue(totalBlocked >= 3)
        repository.setLastSeenBlockedCount(totalBlocked)
        val settings = repository.settings.first()
        assertEquals(0, (totalBlocked - settings.lastSeenBlockedCount).coerceAtLeast(0))
    }
}
