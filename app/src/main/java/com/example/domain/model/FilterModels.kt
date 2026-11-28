package com.example.domain.model

enum class SenderType(val labelFa: String) {
    PHONE_NUMBER("شماره تلفن"),
    ALPHANUMERIC("فرستنده متنی"),
    SHORT_CODE("سرشماره کوتاه"),
    PRIVATE_OR_INVALID("ناشناس / نامعتبر")
}

enum class BlockSource(val labelFa: String) {
    MANUAL_RULE("قانون دستی"),
    BLACKLIST("فهرست سیاه"),
    SMART_ENGINE("فیلتر هوشمند")
}

enum class RuleTarget(val labelFa: String) {
    SENDER("بر اساس فرستنده"),
    CONTENT("بر اساس محتوا")
}

enum class MatchType(val labelFa: String, val target: RuleTarget, val needsPattern: Boolean = true) {
    // Sender rules
    EXACT_NUMBER("شماره تلفن مشخص", RuleTarget.SENDER),
    UNKNOWN_OR_PRIVATE("شماره ناشناس / خصوصی / نامعتبر", RuleTarget.SENDER, needsPattern = false),
    ALPHANUMERIC_ALL("تمام فرستندگان متنی بدون شماره", RuleTarget.SENDER, needsPattern = false),
    SENDER_TEXT_EXACT("نام فرستنده متنی دقیق", RuleTarget.SENDER),
    SENDER_TEXT_CONTAINS("بخشی از نام فرستنده", RuleTarget.SENDER),
    BRAND_OR_PROMO_SENDER("برند یا شناسه تبلیغاتی", RuleTarget.SENDER),

    // Content rules
    SINGLE_WORD("یک کلمه", RuleTarget.CONTENT),
    MULTI_WORD_ALL("چند کلمه (همگی در متن)", RuleTarget.CONTENT),
    EXACT_PHRASE("عبارت کامل", RuleTarget.CONTENT),
    SUBSTRING("بخشی از متن", RuleTarget.CONTENT),
    HAS_LINK("دارای لینک یا نشانی وب", RuleTarget.CONTENT, needsPattern = false),
    REGEX("الگوی Regex", RuleTarget.CONTENT)
}

enum class ListType(val labelFa: String) {
    WHITELIST("فهرست سفید (مجاز)"),
    BLACKLIST("فهرست سیاه (مسدود)")
}

enum class ListEntryType(val labelFa: String) {
    PHONE_NUMBER("شماره تلفن"),
    ALPHANUMERIC_SENDER("شناسه متنی فرستنده"),
    WORD("کلمه"),
    PHRASE("عبارت"),
    PATTERN_REGEX("الگو (Regex)"),
    RULE_EXCEPTION("استثنای قانون")
}

enum class SmartFilterLevel(val labelFa: String, val descriptionFa: String, val threshold: Int) {
    OFF("خاموش", "فقط قوانین دستی و فهرست سیاه اجرا می‌شوند.", 101),
    NORMAL("معمولی", "پیام‌های دارای نشانه‌های قوی اسپم و فیشینگ مسدود می‌شوند (امتیاز ۶۰+).", 60),
    STRICT("سخت‌گیرانه", "پیام‌های مشکوک‌تر نیز برای بررسی در پوشه مسدودشده قرار می‌گیرند (امتیاز ۳۸+).", 38)
}

data class SmartAnalysisResult(
    val score: Int,
    val signals: List<String>,
    val categoryHintFa: String
)

data class FilterDecision(
    val shouldBlock: Boolean,
    val blockSource: BlockSource?,
    val reasonFa: String,
    val matchedRuleId: Long? = null,
    val matchedListEntryId: Long? = null,
    val whitelisted: Boolean = false,
    val smartAnalysis: SmartAnalysisResult
)

object PersianTextNormalizer {
    private val diacriticsRegex = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val multiSpaceRegex = Regex("\\s+")

    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun normalizePersian(
        input: String,
        caseSensitive: Boolean = false,
        whitespaceSensitive: Boolean = false
    ): String {
        var text = normalizeDigits(input)
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .replace('ة', 'ه')
            .replace('ؤ', 'و')
            .replace('إ', 'ا')
            .replace('أ', 'ا')
            .replace("\u200C", if (whitespaceSensitive) "\u200C" else " ")
        text = diacriticsRegex.replace(text, "")
        if (!caseSensitive) {
            text = text.lowercase()
        }
        if (!whitespaceSensitive) {
            text = multiSpaceRegex.replace(text, " ").trim()
        }
        return text
    }

    fun normalizePhoneDigits(raw: String): String {
        val digitsOnly = normalizeDigits(raw).filter { it.isDigit() }
        return when {
            digitsOnly.startsWith("0098") && digitsOnly.length > 4 -> "0" + digitsOnly.drop(4)
            digitsOnly.startsWith("98") && digitsOnly.length >= 12 -> "0" + digitsOnly.drop(2)
            else -> digitsOnly
        }
    }
}

object SenderClassifier {
    private val unknownKeywords = setOf(
        "unknown", "private", "anonymous", "hidden", "restricted",
        "unavailable", "blocked", "ناشناس", "خصوصی", "مخفی", "نامشخص", "null"
    )
    private val promoPrefixes = listOf(
        "ad-", "ads", "ad_", "promo", "info", "sms", "offer", "tabligh",
        "club", "notify", "alert", "news", "sale", "shop",
        "app-", "app_", "myket", "bazaar", "cafebazaar", "sibapp", "sibche", "market", "store", "payam"
    )

    fun extractThreadKey(senderRaw: String): String {
        val trimmed = PersianTextNormalizer.normalizeDigits(senderRaw).trim()
        val type = classify(trimmed)
        return when (type) {
            SenderType.PRIVATE_OR_INVALID -> "PRIVATE_UNKNOWN"
            SenderType.PHONE_NUMBER -> PersianTextNormalizer.normalizePhoneDigits(trimmed)
            SenderType.SHORT_CODE -> trimmed.filter { it.isDigit() }
            SenderType.ALPHANUMERIC -> trimmed.uppercase()
        }
    }

    fun classify(senderRaw: String): SenderType {
        val trimmed = PersianTextNormalizer.normalizeDigits(senderRaw).trim()
        if (trimmed.isEmpty() || unknownKeywords.contains(trimmed.lowercase())) {
            return SenderType.PRIVATE_OR_INVALID
        }
        val hasLetters = trimmed.any { it.isLetter() }
        val digits = trimmed.filter { it.isDigit() }

        if (hasLetters) {
            // Alphanumeric Sender ID (e.g., BANK, AD-SMS, IRANCELL) - no real phone number required
            return SenderType.ALPHANUMERIC
        }

        // Only digits/symbols (+, -, spaces)
        val allowedSymbols = trimmed.all { it.isDigit() || it in setOf('+', '-', ' ', '(', ')') }
        if (!allowedSymbols || digits.isEmpty()) {
            return SenderType.PRIVATE_OR_INVALID
        }
        if (digits.length in 3..8) {
            return SenderType.SHORT_CODE
        }
        if (digits.length in 9..15) {
            return SenderType.PHONE_NUMBER
        }
        return SenderType.PRIVATE_OR_INVALID
    }

    fun isPromoLikeAlphanumeric(senderRaw: String): Boolean {
        val lower = senderRaw.trim().lowercase()
        return promoPrefixes.any { prefix ->
            lower.startsWith(prefix) || lower.contains("-$prefix") || lower.contains("${prefix}-")
        }
    }

    fun displaySender(senderRaw: String): String {
        val trimmed = senderRaw.trim()
        return if (trimmed.isEmpty()) "فرستنده ناشناس" else trimmed
    }
}

object SmsPartCalculator {
    private val gsmBasicChars =
        "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ ÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
            .toSet()

    fun calculateParts(body: String): Int {
        if (body.isEmpty()) return 1
        val isGsm = body.all { it in gsmBasicChars }
        val singleLimit = if (isGsm) 160 else 70
        val multiLimit = if (isGsm) 153 else 67
        return if (body.length <= singleLimit) {
            1
        } else {
            ((body.length + multiLimit - 1) / multiLimit)
        }
    }

    fun isUnicode(body: String): Boolean = !body.all { it in gsmBasicChars }
}
