package com.example.domain.engine

import com.example.domain.model.PersianTextNormalizer
import com.example.domain.model.SenderClassifier
import com.example.domain.model.SenderType
import com.example.domain.model.SmartAnalysisResult

data class BlockedExample(
    val senderRaw: String,
    val body: String
)

private enum class LengthBucket { SHORT, MEDIUM, LONG }

private data class BehavioralSignature(
    val senderType: SenderType,
    val hasUrl: Boolean,
    val hasHandle: Boolean,
    val hasAppPromo: Boolean,
    val hasPromoTerm: Boolean,
    val hasCancelCue: Boolean,
    val hasUrgency: Boolean,
    val lengthBucket: LengthBucket
)

/**
 * Local, offline, multi-signal weighted scoring engine optimized for Persian SMS,
 * with:
 * - Promotional App / Store-Link / Referral Spam Detection
 * - Behavioral Pattern & User-Blocking Learning (adapts from user blocks vs. allowed inbox messages)
 */
object SmartScoringEngine {

    private val urlRegex = Regex(
        """(?i)\b((https?://|www\.)\S+|[a-z0-9-]+\.(ir|com|net|org|xyz|top|click|tk|ml|ga|cf|info|co|me|link|shop|online|app)(/\S*)?)\b"""
    )

    private val handleRegex = Regex("""@[a-zA-Z0-9_]{3,}\b""")

    private val shortenedDomains = listOf(
        "bit.ly", "b2n.ir", "yun.ir", "zil.ink", "tinyurl.com", "is.gd",
        "cutt.ly", "t.co", "goo.gl", "ow.ly", "rb.gy", "shorturl.at", "t.me/",
        "rubika.ir/", "eitaa.com/", "bale.ai/"
    )

    private val appStoreDomains = listOf(
        "cafebazaar.ir", "bazaar.ir", "myket.ir", "play.google.com", "apps.apple.com",
        "sibapp.com", "sibche.com", "anardoni.com", "iapps.ir", "app.link",
        "onelink.me", "apkpure.com", ".apk"
    )

    private val suspiciousTlds = listOf(".xyz", ".top", ".click", ".tk", ".ml", ".ga", ".cf", ".work")
    private val ipUrlRegex = Regex("""https?://\d{1,3}(\.\d{1,3}){3}""")

    private val promoTerms = listOf(
        "تخفیف", "فروش ویژه", "حراج", "جایزه", "قرعه کشی", "قرعه‌کشی",
        "برنده", "لغو 11", "لغو11", "ارسال عدد", "ثبت نام رایگان", "ثبت‌نام رایگان",
        "کد تخفیف", "خرید اقساطی", "وام فوری", "شارژ رایگان", "سود تضمینی",
        "پیشنهاد ویژه", "فقط امروز", "ظرفیت محدود", "درصد تخفیف", "تبلیغ",
        "جشنواره", "کسب درآمد"
    )

    private val promoAppTerms = listOf(
        "نصب اپلیکیشن", "دانلود اپلیکیشن", "نصب برنامه", "دانلود برنامه",
        "اپلیکیشن ما", "اپ جدید", "نسخه جدید اپلیکیشن", "نسخه جدید برنامه",
        "کد معرف", "دعوت از دوستان", "دعوت دوستان", "هدیه ثبت نام",
        "هدیه ورود", "امتیاز در اپلیکیشن", "اپلیکیشن تبلیغاتی",
        "نصب رایگان", "دانلود مستقیم", "از کافه بازار", "کافه بازار",
        "از مایکت", "مایکت", "سیب اپ", "سیبچه", "باشگاه مشتریان",
        "install our app", "download the app", "download our app",
        "download app", "referral code", "invite friends", "promo app"
    )

    private val cancelCues = listOf(
        "لغو 11", "لغو11", "ارسال عدد", "لغو اشتراک", "unsubscribe", "reply stop"
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
        "تشکر", "باشید", "دارد", "دارم", "کنید", "بکنید", "می", "هم", "یا",
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

        // 2. Link, app-store download & suspicious URL analysis
        val urls = extractUrls(body)
        val hasAnyUrl = urls.isNotEmpty()
        var hasAppStoreLink = false
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

            hasAppStoreLink = urls.any { url ->
                val lower = url.lowercase()
                appStoreDomains.any { lower.contains(it) }
            }
            if (hasAppStoreLink) {
                score += 32
                signals += "لینک دانلود یا نصب اپلیکیشن (+۳۲)"
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

        val hasMessengerHandle = handleRegex.containsMatchIn(body)
        if (hasMessengerHandle) {
            score += 14
            signals += "حاوی آیدی تبلیغاتی (@) (+۱۴)"
        }

        // 3. Promotional / bulk spam terms
        val matchedPromo = promoTerms.filter { normalizedBody.contains(PersianTextNormalizer.normalizePersian(it)) }
        if (matchedPromo.isNotEmpty()) {
            val promoPoints = (matchedPromo.size * 14).coerceAtMost(40)
            score += promoPoints
            signals += "الگوی تبلیغاتی (${matchedPromo.take(2).joinToString("، ")}) (+$promoPoints)"
        }

        // 3b. Promotional app / referral / install campaign detection
        val matchedAppPromo = promoAppTerms.filter { normalizedBody.contains(PersianTextNormalizer.normalizePersian(it)) }
        if (matchedAppPromo.isNotEmpty()) {
            val appPromoPoints = (matchedAppPromo.size * 26).coerceAtMost(46)
            score += appPromoPoints
            signals += "تشخیص اپلیکیشن تبلیغاتی (${matchedAppPromo.take(2).joinToString("، ")}) (+$appPromoPoints)"
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

        // 7. Adaptive Behavioral & User-Blocking Learning
        var learnedMatch = false
        var behavioralMatch = false
        val incomingSig = extractBehavioralSignature(
            senderType = senderType,
            rawBody = body,
            normalizedBody = normalizedBody,
            hasUrl = hasAnyUrl,
            hasHandle = hasMessengerHandle,
            hasAppPromo = hasAppStoreLink || matchedAppPromo.isNotEmpty(),
            hasPromoTerm = matchedPromo.isNotEmpty(),
            hasUrgency = matchedUrgency.isNotEmpty()
        )

        if (learnedBlockedExamples.isNotEmpty() && normalizedBody.length >= 8) {
            val incomingTokens = extractMeaningfulTokens(normalizedBody)
            val incomingTemplate = stripVariableNumbers(normalizedBody)

            var bestSim = 0.0
            var similarBlockedCount = 0
            for (example in learnedBlockedExamples) {
                val exNorm = PersianTextNormalizer.normalizePersian(example.body)
                if (exNorm.length < 8) continue
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

            // Learned URL domain & handle overlap from blocked messages
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

            if (hasMessengerHandle) {
                val incomingHandles = handleRegex.findAll(body).map { it.value.lowercase() }.toSet()
                val blockedHandles = learnedBlockedExamples
                    .flatMap { handleRegex.findAll(it.body).map { m -> m.value.lowercase() } }
                    .toSet()
                if (incomingHandles.any { it in blockedHandles }) {
                    score += 44
                    learnedMatch = true
                    signals += "آیدی تکراری مسدودشده توسط کاربر (+۴۴)"
                }
            }

            // Learned sender prefix pattern (bulk short codes / promo prefixes / number ranges)
            val senderKey = SenderClassifier.extractThreadKey(senderRaw)
            if (senderKey.length >= 4) {
                val prefix = senderKey.take(4)
                val prefixBlockCount = learnedBlockedExamples.count {
                    val k = SenderClassifier.extractThreadKey(it.senderRaw)
                    k.length >= 4 && k.startsWith(prefix, ignoreCase = true)
                }
                if (prefixBlockCount >= 2) {
                    val prefixPts = if (prefixBlockCount >= 3) 34 else 22
                    score += prefixPts
                    learnedMatch = true
                    signals += "پیش‌شماره یا الگوی فرستنده مسدودشده (+$prefixPts)"
                }
            }

            // Behavioral Pattern Learning: learn recurring structural/sender/action habits from user blocks
            if (learnedBlockedExamples.size >= 2) {
                val blockedSigs = learnedBlockedExamples.map { ex ->
                    val exType = SenderClassifier.classify(ex.senderRaw)
                    val exNorm = PersianTextNormalizer.normalizePersian(ex.body)
                    val exUrls = extractUrls(ex.body)
                    val exPromo = promoTerms.any { exNorm.contains(PersianTextNormalizer.normalizePersian(it)) }
                    val exAppPromo = exUrls.any { u -> appStoreDomains.any { u.lowercase().contains(it) } } ||
                        promoAppTerms.any { exNorm.contains(PersianTextNormalizer.normalizePersian(it)) }
                    val exUrgency = urgencyTerms.any { exNorm.contains(PersianTextNormalizer.normalizePersian(it)) }
                    extractBehavioralSignature(
                        senderType = exType,
                        rawBody = ex.body,
                        normalizedBody = exNorm,
                        hasUrl = exUrls.isNotEmpty(),
                        hasHandle = handleRegex.containsMatchIn(ex.body),
                        hasAppPromo = exAppPromo,
                        hasPromoTerm = exPromo,
                        hasUrgency = exUrgency
                    )
                }

                val allowedSigs = recentIncomingBodies.map { allowedBody ->
                    val aNorm = PersianTextNormalizer.normalizePersian(allowedBody)
                    val aUrls = extractUrls(allowedBody)
                    val aPromo = promoTerms.any { aNorm.contains(PersianTextNormalizer.normalizePersian(it)) }
                    val aAppPromo = aUrls.any { u -> appStoreDomains.any { u.lowercase().contains(it) } } ||
                        promoAppTerms.any { aNorm.contains(PersianTextNormalizer.normalizePersian(it)) }
                    val aUrgency = urgencyTerms.any { aNorm.contains(PersianTextNormalizer.normalizePersian(it)) }
                    extractBehavioralSignature(
                        senderType = SenderType.PHONE_NUMBER,
                        rawBody = allowedBody,
                        normalizedBody = aNorm,
                        hasUrl = aUrls.isNotEmpty(),
                        hasHandle = handleRegex.containsMatchIn(allowedBody),
                        hasAppPromo = aAppPromo,
                        hasPromoTerm = aPromo,
                        hasUrgency = aUrgency
                    )
                }

                val behaviorMatchedBlocked = blockedSigs.count { bSig ->
                    behavioralMatchScore(incomingSig, bSig) >= 3
                }
                val behaviorMatchedAllowed = allowedSigs.count { aSig ->
                    behavioralMatchScore(incomingSig, aSig) >= 3
                }

                // Also check if user consistently blocks this sender class (e.g. ALPHANUMERIC / SHORT_CODE)
                val sameSenderTypeBlocked = blockedSigs.count { it.senderType == senderType }
                val nonPhoneSenderBehaviorBoost = if (
                    (senderType == SenderType.ALPHANUMERIC || senderType == SenderType.SHORT_CODE) &&
                    sameSenderTypeBlocked >= 3 &&
                    (incomingSig.hasUrl || incomingSig.hasHandle || incomingSig.hasPromoTerm || incomingSig.hasAppPromo || incomingSig.hasCancelCue)
                ) {
                    18
                } else {
                    0
                }

                val hasRiskyTrait = incomingSig.hasUrl || incomingSig.hasHandle || incomingSig.hasAppPromo ||
                    incomingSig.hasPromoTerm || incomingSig.hasCancelCue || incomingSig.hasUrgency
                if (hasRiskyTrait && behaviorMatchedBlocked >= 2 && behaviorMatchedBlocked > behaviorMatchedAllowed) {
                    val behaviorPoints = (28 + (behaviorMatchedBlocked - 2) * 8 + nonPhoneSenderBehaviorBoost).coerceAtMost(48)
                    score += behaviorPoints
                    behavioralMatch = true
                    learnedMatch = true
                    signals += "یادگیری الگوی رفتاری از مسدودسازی‌های قبلی شما (+$behaviorPoints)"
                } else if (nonPhoneSenderBehaviorBoost > 0) {
                    score += nonPhoneSenderBehaviorBoost
                    behavioralMatch = true
                    learnedMatch = true
                    signals += "الگوی رفتاری حساسیت کاربر به فرستندگان تبلیغاتی (+$nonPhoneSenderBehaviorBoost)"
                }
            }
        }

        // 8. Legitimate OTP dampening (if clean OTP with NO URL, NO promo/app-promo, and NOT learned spam)
        val hasOtpCue = otpSafeTerms.any { normalizedBody.contains(PersianTextNormalizer.normalizePersian(it)) }
        if (hasOtpCue && !hasAnyUrl && !hasMessengerHandle && matchedPromo.isEmpty() && matchedAppPromo.isEmpty() && !learnedMatch) {
            score = (score - 18).coerceAtLeast(0)
            signals += "نشانگر کد ورود بدون لینک (-۱۸)"
        }

        val finalScore = score.coerceIn(0, 100)
        val categoryHint = when {
            behavioralMatch && finalScore >= 45 ->
                "تشخیص هوشمند بر اساس الگوی رفتاری مسدودسازی شما"
            learnedMatch && finalScore >= 45 ->
                "تشخیص هوشمند بر اساس پیام‌های مسدودشده قبلی"
            (hasAppStoreLink || matchedAppPromo.isNotEmpty()) && finalScore >= 45 ->
                "اپلیکیشن تبلیغاتی / کمپین نصب برنامه"
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

    private fun extractBehavioralSignature(
        senderType: SenderType,
        rawBody: String,
        normalizedBody: String,
        hasUrl: Boolean,
        hasHandle: Boolean,
        hasAppPromo: Boolean,
        hasPromoTerm: Boolean,
        hasUrgency: Boolean
    ): BehavioralSignature {
        val hasCancelCue = cancelCues.any { normalizedBody.contains(PersianTextNormalizer.normalizePersian(it)) }
        val bucket = when {
            rawBody.length < 55 -> LengthBucket.SHORT
            rawBody.length <= 135 -> LengthBucket.MEDIUM
            else -> LengthBucket.LONG
        }
        return BehavioralSignature(
            senderType = senderType,
            hasUrl = hasUrl,
            hasHandle = hasHandle,
            hasAppPromo = hasAppPromo,
            hasPromoTerm = hasPromoTerm,
            hasCancelCue = hasCancelCue,
            hasUrgency = hasUrgency,
            lengthBucket = bucket
        )
    }

    private fun behavioralMatchScore(a: BehavioralSignature, b: BehavioralSignature): Int {
        var matches = 0
        if (a.senderType == b.senderType) matches++
        if (a.hasUrl && b.hasUrl) matches += 2
        if (a.hasHandle && b.hasHandle) matches += 2
        if (a.hasAppPromo && b.hasAppPromo) matches += 2
        if (a.hasPromoTerm && b.hasPromoTerm) matches++
        if (a.hasCancelCue && b.hasCancelCue) matches += 2
        if (a.hasUrgency && b.hasUrgency) matches++
        if (a.lengthBucket == b.lengthBucket && a.lengthBucket != LengthBucket.SHORT) matches++
        return matches
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
