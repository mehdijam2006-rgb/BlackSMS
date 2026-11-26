package com.example

import com.example.data.local.FilterRuleEntity
import com.example.data.local.ListEntryEntity
import com.example.domain.engine.SmartScoringEngine
import com.example.domain.engine.SmsFilterEngine
import com.example.domain.model.BlockSource
import com.example.domain.model.ListEntryType
import com.example.domain.model.ListType
import com.example.domain.model.MatchType
import com.example.domain.model.RuleTarget
import com.example.domain.model.SenderClassifier
import com.example.domain.model.SenderType
import com.example.domain.model.SmartFilterLevel
import com.example.domain.model.SmsPartCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSmsFilterTest {

    @Test
    fun `sender classifier identifies alphanumeric senders without phone numbers`() {
        assertEquals(SenderType.ALPHANUMERIC, SenderClassifier.classify("BANK"))
        assertEquals(SenderType.ALPHANUMERIC, SenderClassifier.classify("AD-SMS"))
        assertEquals(SenderType.ALPHANUMERIC, SenderClassifier.classify("IRANCELL"))
        assertEquals(SenderType.PRIVATE_OR_INVALID, SenderClassifier.classify(""))
        assertEquals(SenderType.PRIVATE_OR_INVALID, SenderClassifier.classify("Unknown"))
        assertEquals(SenderType.PRIVATE_OR_INVALID, SenderClassifier.classify("ناشناس"))
        assertEquals(SenderType.SHORT_CODE, SenderClassifier.classify("983000"))
        assertEquals(SenderType.PHONE_NUMBER, SenderClassifier.classify("+989123456789"))
    }

    @Test
    fun `whitelist overrides manual rules and smart filter`() {
        val rules = listOf(
            FilterRuleEntity(
                id = 1,
                name = "Block All Alphanumeric",
                ruleTarget = RuleTarget.SENDER,
                matchType = MatchType.ALPHANUMERIC_ALL,
                pattern = "",
                priority = 100
            )
        )
        val lists = listOf(
            ListEntryEntity(
                id = 10,
                listType = ListType.WHITELIST,
                entryType = ListEntryType.ALPHANUMERIC_SENDER,
                value = "BANK-MELLI"
            )
        )

        val decision = SmsFilterEngine.evaluate(
            senderRaw = "BANK-MELLI",
            body = "رمز پویا: ۱۲۳۴۵۶",
            rules = rules,
            listEntries = lists,
            smartLevel = SmartFilterLevel.STRICT
        )

        assertFalse(decision.shouldBlock)
        assertTrue(decision.whitelisted)
    }

    @Test
    fun `manual rule exception skips blocking when exception phrase matches`() {
        val rules = listOf(
            FilterRuleEntity(
                id = 2,
                name = "Block Promo Text Senders",
                ruleTarget = RuleTarget.SENDER,
                matchType = MatchType.SENDER_TEXT_CONTAINS,
                pattern = "BANK",
                exceptionPattern = "کد تایید",
                priority = 80
            )
        )

        val otpDecision = SmsFilterEngine.evaluate(
            senderRaw = "BANK-INFO",
            body = "کد تایید شما: ۴۸۲۱",
            rules = rules,
            listEntries = emptyList(),
            smartLevel = SmartFilterLevel.OFF
        )
        assertFalse(otpDecision.shouldBlock)

        val promoDecision = SmsFilterEngine.evaluate(
            senderRaw = "BANK-INFO",
            body = "جشنواره ویژه فروش اقساطی!",
            rules = rules,
            listEntries = emptyList(),
            smartLevel = SmartFilterLevel.OFF
        )
        assertTrue(promoDecision.shouldBlock)
        assertEquals(BlockSource.MANUAL_RULE, promoDecision.blockSource)
    }

    @Test
    fun `content rules handle multi-word, regex, and Persian Arabic character normalization`() {
        val rules = listOf(
            FilterRuleEntity(
                id = 3,
                name = "Multi word promo",
                ruleTarget = RuleTarget.CONTENT,
                matchType = MatchType.MULTI_WORD_ALL,
                pattern = "تخفیف, لغو ۱۱",
                priority = 60
            )
        )

        // Uses Arabic ي/ك and Latin digits 11
        val decision = SmsFilterEngine.evaluate(
            senderRaw = "09120001122",
            body = "فروش ويژه با تخفيف باورنكردنی. لغو 11",
            rules = rules,
            listEntries = emptyList(),
            smartLevel = SmartFilterLevel.OFF
        )
        assertTrue(decision.shouldBlock)
        assertEquals(3L, decision.matchedRuleId)
    }

    @Test
    fun `smart scoring engine distinguishes phishing bank link from clean personal message`() {
        val phishing = SmartScoringEngine.analyze(
            senderRaw = "09351112233",
            body = "ابلاغیه فوری سامانه ثنا و مسدودی حساب بانکی: http://b2n.ir/sana"
        )
        assertTrue(phishing.score >= SmartFilterLevel.NORMAL.threshold)

        val personal = SmartScoringEngine.analyze(
            senderRaw = "09121234567",
            body = "سلام، ساعت پنج کافه می‌بینمت."
        )
        assertTrue(personal.score < SmartFilterLevel.STRICT.threshold)
    }

    @Test
    fun `sms part calculator computes GSM-7 vs Persian UCS-2 multipart counts accurately`() {
        assertEquals(1, SmsPartCalculator.calculateParts("Hello world"))
        assertEquals(2, SmsPartCalculator.calculateParts("a".repeat(161)))
        assertEquals(1, SmsPartCalculator.calculateParts("سلام دنیا"))
        assertEquals(2, SmsPartCalculator.calculateParts("س".repeat(71)))
    }
}
