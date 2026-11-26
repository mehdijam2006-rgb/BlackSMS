package com.example.domain.engine

import com.example.domain.model.PersianTextNormalizer
import com.example.domain.model.SenderClassifier
import com.example.domain.model.SenderType
import com.example.domain.model.SmartAnalysisResult

data class BlockedExample(
    val senderRaw: String,
    val body: String
)

/**
 * Local, offline, multi-signal weighted scoring engine optimized for Persian SMS,
 * with Adaptive Learning from messages the user has previously marked/blocked as spam.
 */
object SmartScoringEngine {

    private val urlRegex = Regex(
        """(?i)\b((https?://|www\.)\S+|[a-z0-9-]+\.(ir|com|net|org|xyz|top|click|tk|ml|ga|cf|info|co|me|link|shop|online)(/\S*)?)\b"""
    )

    private val shortenedDomains = listOf(
        "bit.ly", "b2n.ir", "yun.ir", "zil.ink", "tinyurl.com", "is.gd",
        "cutt.ly", "t.co", "goo.gl", "ow.ly", "rb.gy", "shorturl.at", "t.me/"
    )

    private val suspiciousTlds = listOf(".xyz", ".top", ".click", ".tk", ".ml", ".ga", ".cf", ".work")
    private val ipUrlRegex = Regex("""https?://\d{1,3}(\.\d{1,3}){3}""")

    private val promoTerms = listOf(
        "تخفیف", "فروش ویژه", "حراج", "جایزه", "قرعه کشی", "قرعه‌کشی",
        "برنده", "لغو 11", "لغو11", "ارسال عدد", "ثبت نام رایگان", "ثبت‌نام رایگان",
        "کد تخفیف", "خرید اقساطی", "وام فوری", "شارژ رایگان", "سود تضمینی",
        "پیشنهاد ویژه", "فقط امروز", "ظرفیت محدود", "درصد تخفیف", "تبلیغ"
    )

    private val phishingOrgTerms = listOf(
        "ثنا", "ابلاغیه", "قوه قضاییه", "عدلیه", "شکایت", "احضاریه",
        "سهام عدالت", "یارانه", "مسدود", "حساب بانکی", "رمز پویا",
        "همراه بانک", "کارت بانکی", "بانک مرکزی", "بانک ملی", "بانک ملت",
        "تایید هویت", "بروزرسانی اطلاعات", "هشدار امنیتی", "کد ملی", "شبا"
    )

    private val urgencyTerms = listOf(
        "فوری", "اخطار", "مهلت محدود", "تا 24 ساعت", "قطع خدمات",
        "جریمه", "تعلیق", "اقدام فوری", "لغو اشتراک"
    )

    private val otpSafeTerms = listOf(
        "کد تایید", "کد ورود", "رمز یکبار مصرف", "رمز عبور", "otp", "verification code"
    )

    private val stopWords = setOf(
        "سلام", "درود", "خوب", "خوبی", "برای", "است", "هست", "بود", "شود", "شد",
        "شما", "من", "ما", "این", "آن", "های", "هایی", "لطف", "لطفا", "ممنون",
        "تشکر", "باشید", "دارد", "دارم", "کنید", "بکنید", "می", "های", "هم", "یا",
        "که", "را", "از", "به", "در", "با", "تا", "بر", "و", "ای",
        "the", "and", "for", "you", "your", "are", "with", "this", "that", "from", "have"
    )

    fun extractUrls(body: String): List<String> {
        val normalized = PersianTextNormalizer.normalizeDigits(body)
        return urlRegex.findAll(normalized).map { it.value }.toList()
    }

    fun analyze(
        senderRaw: String,
        body: String,
        recentIncomingBodies: List<String> = emptyList(),
        learnedBlockedExamples: List<BlockedExample> = emptyList()
    ): SmartAnalysisResult {
        val senderType = SenderClassifier.classify(senderRaw)
        val normalizedBody = PersianTextNormalizer.normalizePersian(body)
        val signals = mutableListOf<String>()
        var score = 0

        // 1. Sender heuristics
        when (senderType) {
            SenderType.PRIVATE_OR_INVALID -> {
                score += 25
                signals += "فرستنده ناشناس یا فاقد شناسه معتبر (+۲۵)"
            }
            SenderType.ALPHANUMERIC -> {
                score += 18
                signals += "فرستنده متنی بدون شماره تلفن (+۱۸)"
                if (SenderClassifier.isPromoLikeAlphanumeric(senderRaw)) {
                    score += 20
                    signals += "پیشوند یا نام تبلیغاتی در شناسه فرستنده (+۲۰)"
                }
            }
            SenderType.SHORT_CODE -> {
                score += 12
                signals += "ارسال از سرشماره کوتاه انبوه (+۱۲)"
            }
            SenderType.PHONE_NUMBER -> Unit
        }

        // 2. Link & suspicious URL analysis
        val urls = extractUrls(body)
        val hasAnyUrl = urls.isNotEmpty()
        if (hasAnyUrl) {
            score += 12
            signals += "وجود لینک در متن پیام (+۱۲)"

            val hasShortened = urls.any { url ->
                val lower = url.lowercase()
                shortenedDomains.any { lower.contains(it) }
            }
            if (hasShortened) {
                score += 26
                signals += "لینک کوتاه‌شده یا پیام‌رسان (+۲۶)"
            }

            val hasSuspiciousTld = urls.any { url ->
                val lower = url.lowercase()
                suspiciousTlds.any { lower.contains(it) } || ipUrlRegex.containsMatchIn(lower)
            }
            if (hasSuspiciousTld) {
                score += 32
                signals += "دامنه یا آدرس اینترنتی مشکوک (+۳۲)"
            }
        }

        // 3. Promotional / bulk spam terms
        val matchedPromo = promoTerms.filter { normalizedBody.contains(PersianTextNormalizer.normalizePersian(it)) }
        if (matchedPromo.isNotEmpty()) {
            val promoPoints = (matchedPromo.size * 14).coerceAtMost(40)
            score += promoPoints
            signals += "الگوی تبلیغاتی (${matchedPromo.take(2).joinToString("، ")}) (+$promoPoints)"
        }

        // 4. Bank / organization impersonation risk
        val matchedOrg = phishingOrgTerms.filter { normalizedBody.contains(PersianTextNormalizer.normalizePersian(it)) }
        if (matchedOrg.isNotEmpty()) {
            val orgContextBonus = when {
                hasAnyUrl && senderType == SenderType.PHONE_NUMBER -> 35
                hasAnyUrl -> 26
                matchedOrg.size >= 2 -> 20
                else -> 10
            }
            score += orgContextBonus
            signals += "کلیدواژه‌های سازمانی/بانکی (${matchedOrg.take(2).joinToString("، ")}) (+$orgContextBonus)"
        }

        // 5. Urgency & pressure cues
        val matchedUrgency = urgencyTerms.filter { normalizedBody.contains(PersianTextNormalizer.normalizePersian(it)) }
        if (matchedUrgency.isNotEmpty()) {
            val urgencyPoints = (matchedUrgency.size * 10).coerceAtMost(20)
            score += urgencyPoints
            signals += "لحن اضطراری یا فشار زمانی (+$urgencyPoints)"
        }

        // 6. Repetitive / bulk duplicate check against recent incoming texts
        val isDuplicateBulk = recentIncomingBodies.any { existing ->
            val normExisting = PersianTextNormalizer.normalizePersian(existing)
            normExisting.length >= 16 && tokenSimilarity(normalizedBody, normExisting) >= 0.80
        }
        if (isDuplicateBulk) {
            score += 24
            signals += "تکرار محتوای مشابه پیام‌های انبوه اخیر (+۲۴)"
        }

        // 7. Adaptive Local Learning from user's previously blocked messages
        var learnedMatch = false
        if (learnedBlockedExamples.isNotEmpty() && normalizedBody.length >= 10) {
            val incomingTokens = extractMeaningfulTokens(normalizedBody)
            val incomingTemplate = stripVariableNumbers(normalizedBody)

            var bestSim = 0.0
            var similarBlockedCount = 0
            for (example in learnedBlockedExamples) {
                val exNorm = PersianTextNormalizer.normalizePersian(example.body)
                if (exNorm.length < 10) continue
                val exTemplate = stripVariableNumbers(exNorm)
                val tokSim = tokenSimilarity(incomingTemplate, exTemplate)
                val charSim = trigramSimilarity(incomingTemplate, exTemplate)
                val sim = maxOf(tokSim, charSim)
                if (sim > bestSim) bestSim = sim
                if (sim >= 0.45) similarBlockedCount++
            }

            if (similarBlockedCount >= 2 && normalizedBody.length >= 12) {
                score += 64
                learnedMatch = true
                signals += "یادگیری هوشمند از پیام‌های مشابه مسدودشده (+۶۴)"
            } else if (bestSim >= 0.72 && incomingTokens.size >= 3 && normalizedBody.length >= 16) {
                score += 62
                learnedMatch = true
                signals += "تشابه بسیار بالا با پیام مسدودشده قبلی (+۶۲)"
            } else if (similarBlockedCount >= 1 && bestSim >= 0.54) {
                score += 40
                learnedMatch = true
                signals += "شباهت نسبی با پیام مسدودشده قبلی (+۴۰)"
            } else if (learnedBlockedExamples.size >= 2 && incomingTokens.isNotEmpty()) {
                // Recurring vocabulary learned from >= 2 user-blocked messages (excluding common allowed words)
                val allowedTokens = recentIncomingBodies
                    .flatMap { extractMeaningfulTokens(PersianTextNormalizer.normalizePersian(it)) }
                    .groupingBy { it }
                    .eachCount()
                val blockedTokenFreq = mutableMapOf<String, Int>()
                for (example in learnedBlockedExamples) {
                    val tokens = extractMeaningfulTokens(PersianTextNormalizer.normalizePersian(example.body))
                    for (t in tokens) {
                        blockedTokenFreq[t] = (blockedTokenFreq[t] ?: 0) + 1
                    }
                }
                val learnedHits = incomingTokens.filter { tok ->
                    val bCount = blockedTokenFreq[tok] ?: 0
                    val aCount = allowedTokens[tok] ?: 0
                    bCount >= 2 && bCount > aCount
                }
                if (learnedHits.size >= 2) {
                    val pts = (learnedHits.size * 18).coerceAtMost(64)
                    score += pts
                    learnedMatch = true
                    signals += "کلمات تکرارشونده در اسپم‌های قبلی (${learnedHits.take(3).joinToString("، ")}) (+$pts)"
                }
            }

            // Learned URL domain overlap from blocked messages
            if (urls.isNotEmpty()) {
                val incomingDomains = urls.mapNotNull { extractDomain(it) }.toSet()
                val blockedDomains = learnedBlockedExamples
                    .flatMap { extractUrls(it.body) }
                    .mapNotNull { extractDomain(it) }
                    .toSet()
                if (incomingDomains.any { it in blockedDomains }) {
                    score += 40
                    learnedMatch = true
                    signals += "دامنه لینک تکراری از پیام‌های مسدودشده قبلی (+۴۰)"
                }
            }

            // Learned sender prefix pattern (e.g. bulk short codes or promo prefixes blocked >= 2 times)
            val senderKey = SenderClassifier.extractThreadKey(senderRaw)
            if (senderKey.length >= 4) {
                val prefix = senderKey.take(4)
                val prefixBlockCount = learnedBlockedExamples.count {
                    val k = SenderClassifier.extractThreadKey(it.senderRaw)
                    k.length >= 4 && k.startsWith(prefix, ignoreCase = true)
                }
                if (prefixBlockCount >= 2) {
                    score += 20
                    learnedMatch = true
                    signals += "پیش‌شماره یا الگوی فرستنده مسدودشده (+۲۰)"
                }
            }
        }

        // 8. Legitimate OTP dampening (if short OTP with NO URL, NO promo, and NOT learned spam)
        val hasOtpCue = otpSafeTerms.any { normalizedBody.contains(PersianTextNormalizer.normalizePersian(it)) }
        if (hasOtpCue && !hasAnyUrl && matchedPromo.isEmpty() && !learnedMatch) {
            score = (score - 18).coerceAtLeast(0)
            signals += "نشانگر کد ورود بدون لینک (-۱۸)"
        }

        val finalScore = score.coerceIn(0, 100)
        val categoryHint = when {
            learnedMatch && finalScore >= 45 ->
                "تشخیص هوشمند بر اساس پیام‌های مسدودشده قبلی"
            matchedOrg.isNotEmpty() && hasAnyUrl && finalScore >= 50 ->
                "احتمال فیشینگ یا جعل هویت سازمانی/بانکی"
            matchedPromo.isNotEmpty() && finalScore >= 38 ->
                "پیامک تبلیغاتی یا انبوه"
            hasAnyUrl && finalScore >= 50 ->
                "پیامک حاوی لینک مشکوک"
            isDuplicateBulk ->
                "پیامک تکراری / ارسال انبوه"
            finalScore >= 38 ->
                "پیامک مشکوک به اسپم"
            else ->
                "پیام عادی"
        }

        return SmartAnalysisResult(
            score = finalScore,
            signals = signals,
            categoryHintFa = categoryHint
        )
    }

    private fun stripVariableNumbers(text: String): String =
        text.replace(Regex("""\d+"""), "#").replace(Regex("""\s+"""), " ").trim()

    private fun extractMeaningfulTokens(normalizedText: String): Set<String> =
        normalizedText
            .split(Regex("""[\s.,!?:؛،«»()\[\]"'/\-]+"""))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stopWords && !it.all { ch -> ch.isDigit() } }
            .toSet()

    private fun extractDomain(url: String): String? {
        val cleaned = url.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore('?')
            .trim()
        return cleaned.takeIf { it.contains('.') && it.length >= 4 }
    }

    private fun tokenSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val tokensA = a.split(' ').filter { it.length > 1 }.toSet()
        val tokensB = b.split(' ').filter { it.length > 1 }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val intersection = tokensA.intersect(tokensB).size.toDouble()
        val union = tokensA.union(tokensB).size.toDouble()
        return intersection / union
    }

    private fun trigramSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.length < 3 || b.length < 3) return 0.0
        val gramsA = (0..a.length - 3).map { a.substring(it, it + 3) }.toSet()
        val gramsB = (0..b.length - 3).map { b.substring(it, it + 3) }.toSet()
        if (gramsA.isEmpty() || gramsB.isEmpty()) return 0.0
        val intersection = gramsA.intersect(gramsB).size.toDouble()
        val union = gramsA.union(gramsB).size.toDouble()
        return intersection / union
    }
}
