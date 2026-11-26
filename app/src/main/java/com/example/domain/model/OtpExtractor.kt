package com.example.domain.model

/**
 * Extracts 4-8 digit OTP / verification codes from incoming SMS messages
 * (e.g. "کد تایید شما: 482910" or "Your verification code is 928174")
 * so the UI can show a 1-tap Copy Code pill like Google Messages.
 */
object OtpExtractor {
    private val contextHintKeywords = listOf(
        "کد", "رمز", "تایید", "تائید", "احراز", "ورود", "پویا",
        "code", "otp", "pin", "passcode", "verify", "verification", "2fa"
    )
    private val codeRegex = Regex("""(?<!\d)\d{4,8}(?!\d)""")

    fun extractOtp(body: String): String? {
        val normalized = PersianTextNormalizer.normalizeDigits(body)
        val lower = normalized.lowercase()
        val hasHint = contextHintKeywords.any { lower.contains(it) }
        if (!hasHint) return null

        val candidates = codeRegex.findAll(normalized)
            .map { it.value }
            .filter { code ->
                // Ignore likely years like 1403/1404/2025/2026 unless 5+ digits
                !(code.length == 4 && (code.startsWith("13") || code.startsWith("14") || code.startsWith("20")))
            }
            .toList()
        return candidates.firstOrNull()
    }
}
