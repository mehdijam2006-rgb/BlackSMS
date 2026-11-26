package com.example.domain.engine

import com.example.data.local.FilterRuleEntity
import com.example.data.local.ListEntryEntity
import com.example.domain.model.BlockSource
import com.example.domain.model.FilterDecision
import com.example.domain.model.ListEntryType
import com.example.domain.model.ListType
import com.example.domain.model.MatchType
import com.example.domain.model.PersianTextNormalizer
import com.example.domain.model.SenderClassifier
import com.example.domain.model.SenderType
import com.example.domain.model.SmartFilterLevel

/**
 * Deterministic 4-stage SMS filtering pipeline:
 * 1. Whitelist & Rule Exceptions (highest priority)
 * 2. Manual Blacklist
 * 3. Manual Rules ordered by Priority DESC
 * 4. Offline Smart Scoring Engine according to SmartFilterLevel
 */
object SmsFilterEngine {

    fun evaluate(
        senderRaw: String,
        body: String,
        rules: List<FilterRuleEntity>,
        listEntries: List<ListEntryEntity>,
        smartLevel: SmartFilterLevel,
        recentIncomingBodies: List<String> = emptyList(),
        learnedBlockedExamples: List<BlockedExample> = emptyList()
    ): FilterDecision {
        val smartAnalysis = SmartScoringEngine.analyze(
            senderRaw = senderRaw,
            body = body,
            recentIncomingBodies = recentIncomingBodies,
            learnedBlockedExamples = learnedBlockedExamples
        )
        val senderType = SenderClassifier.classify(senderRaw)
        val threadKey = SenderClassifier.extractThreadKey(senderRaw)

        // Stage 1: Whitelist (numbers, alphanumeric senders, rule exceptions)
        val whitelistEntries = listEntries.filter { it.listType == ListType.WHITELIST }
        for (entry in whitelistEntries) {
            if (matchesListEntry(senderRaw, threadKey, body, entry)) {
                return FilterDecision(
                    shouldBlock = false,
                    blockSource = null,
                    reasonFa = "مجاز به دلیل تطبیق با فهرست سفید (${entry.value})",
                    matchedListEntryId = entry.id,
                    whitelisted = true,
                    smartAnalysis = smartAnalysis
                )
            }
        }

        // Stage 2: Blacklist
        val blacklistEntries = listEntries.filter { it.listType == ListType.BLACKLIST }
        for (entry in blacklistEntries) {
            if (matchesListEntry(senderRaw, threadKey, body, entry)) {
                return FilterDecision(
                    shouldBlock = true,
                    blockSource = BlockSource.BLACKLIST,
                    reasonFa = "مسدودشده توسط فهرست سیاه (${entry.entryType.labelFa}: ${entry.value})",
                    matchedListEntryId = entry.id,
                    smartAnalysis = smartAnalysis
                )
            }
        }

        // Stage 3: Manual Rules sorted by priority descending, then id ascending
        val orderedRules = rules
            .filter { it.isEnabled }
            .sortedWith(compareByDescending<FilterRuleEntity> { it.priority }.thenBy { it.id })

        for (rule in orderedRules) {
            if (matchesRuleException(senderRaw, body, rule)) {
                continue
            }
            if (matchesRule(senderRaw, senderType, body, rule)) {
                val senderDesc = if (senderType == SenderType.ALPHANUMERIC) {
                    "فرستنده متنی «${SenderClassifier.displaySender(senderRaw)}»"
                } else {
                    "فرستنده «${SenderClassifier.displaySender(senderRaw)}»"
                }
                return FilterDecision(
                    shouldBlock = true,
                    blockSource = BlockSource.MANUAL_RULE,
                    reasonFa = "قانون «${rule.name}» (${rule.matchType.labelFa}) روی $senderDesc",
                    matchedRuleId = rule.id,
                    smartAnalysis = smartAnalysis
                )
            }
        }

        // Stage 4: Smart Scoring Engine
        if (smartLevel != SmartFilterLevel.OFF && smartAnalysis.score >= smartLevel.threshold) {
            val topSignals = smartAnalysis.signals.take(2).joinToString(" • ")
            val reason = buildString {
                append("${smartAnalysis.categoryHintFa} (امتیاز ${smartAnalysis.score}/۱۰۰ در حالت ${smartLevel.labelFa})")
                if (topSignals.isNotBlank()) {
                    append(" — $topSignals")
                }
            }
            return FilterDecision(
                shouldBlock = true,
                blockSource = BlockSource.SMART_ENGINE,
                reasonFa = reason,
                smartAnalysis = smartAnalysis
            )
        }

        return FilterDecision(
            shouldBlock = false,
            blockSource = null,
            reasonFa = "مجاز (امتیاز هوشمندی ${smartAnalysis.score}/۱۰۰)",
            smartAnalysis = smartAnalysis
        )
    }

    private fun matchesListEntry(
        senderRaw: String,
        threadKey: String,
        body: String,
        entry: ListEntryEntity
    ): Boolean {
        val value = entry.value.trim()
        if (value.isEmpty()) return false
        return when (entry.entryType) {
            ListEntryType.PHONE_NUMBER -> {
                val normEntry = PersianTextNormalizer.normalizePhoneDigits(value)
                normEntry.isNotEmpty() && normEntry == threadKey
            }
            ListEntryType.ALPHANUMERIC_SENDER -> {
                val normSender = PersianTextNormalizer.normalizePersian(senderRaw)
                val normVal = PersianTextNormalizer.normalizePersian(value)
                normSender.isNotEmpty() && (normSender == normVal || threadKey.equals(value, ignoreCase = true))
            }
            ListEntryType.WORD -> {
                val normBody = PersianTextNormalizer.normalizePersian(body)
                val normVal = PersianTextNormalizer.normalizePersian(value)
                normVal.isNotEmpty() && containsWholeWord(normBody, normVal)
            }
            ListEntryType.PHRASE -> {
                val normBody = PersianTextNormalizer.normalizePersian(body)
                val normVal = PersianTextNormalizer.normalizePersian(value)
                normVal.isNotEmpty() && normBody.contains(normVal)
            }
            ListEntryType.PATTERN_REGEX -> {
                safeRegexMatches(value, body, caseSensitive = false) ||
                    safeRegexMatches(value, senderRaw, caseSensitive = false)
            }
            ListEntryType.RULE_EXCEPTION -> {
                val normBody = PersianTextNormalizer.normalizePersian(body)
                val normSender = PersianTextNormalizer.normalizePersian(senderRaw)
                val normVal = PersianTextNormalizer.normalizePersian(value)
                normVal.isNotEmpty() && (normBody.contains(normVal) || normSender.contains(normVal))
            }
        }
    }

    private fun matchesRuleException(
        senderRaw: String,
        body: String,
        rule: FilterRuleEntity
    ): Boolean {
        if (rule.exceptionPattern.isBlank()) return false
        val exceptions = rule.exceptionPattern.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }
        val normSender = PersianTextNormalizer.normalizePersian(senderRaw, rule.caseSensitive, rule.whitespaceSensitive)
        val normBody = PersianTextNormalizer.normalizePersian(body, rule.caseSensitive, rule.whitespaceSensitive)
        return exceptions.any { ex ->
            val normEx = PersianTextNormalizer.normalizePersian(ex, rule.caseSensitive, rule.whitespaceSensitive)
            normSender.contains(normEx) || normBody.contains(normEx)
        }
    }

    fun matchesRule(
        senderRaw: String,
        senderType: SenderType,
        body: String,
        rule: FilterRuleEntity
    ): Boolean {
        val pattern = rule.pattern.trim()
        val normSender = PersianTextNormalizer.normalizePersian(
            senderRaw,
            caseSensitive = rule.caseSensitive,
            whitespaceSensitive = rule.whitespaceSensitive
        )
        val normBody = PersianTextNormalizer.normalizePersian(
            body,
            caseSensitive = rule.caseSensitive,
            whitespaceSensitive = rule.whitespaceSensitive
        )
        val normPattern = PersianTextNormalizer.normalizePersian(
            pattern,
            caseSensitive = rule.caseSensitive,
            whitespaceSensitive = rule.whitespaceSensitive
        )

        return when (rule.matchType) {
            MatchType.EXACT_NUMBER -> {
                val ruleDigits = PersianTextNormalizer.normalizePhoneDigits(pattern)
                val senderDigits = PersianTextNormalizer.normalizePhoneDigits(senderRaw)
                ruleDigits.isNotEmpty() && ruleDigits == senderDigits
            }
            MatchType.UNKNOWN_OR_PRIVATE -> {
                senderType == SenderType.PRIVATE_OR_INVALID
            }
            MatchType.ALPHANUMERIC_ALL -> {
                senderType == SenderType.ALPHANUMERIC
            }
            MatchType.SENDER_TEXT_EXACT -> {
                normPattern.isNotEmpty() && normSender == normPattern
            }
            MatchType.SENDER_TEXT_CONTAINS -> {
                normPattern.isNotEmpty() && normSender.contains(normPattern)
            }
            MatchType.BRAND_OR_PROMO_SENDER -> {
                if (normPattern.isEmpty()) {
                    senderType == SenderType.ALPHANUMERIC && SenderClassifier.isPromoLikeAlphanumeric(senderRaw)
                } else {
                    val tokens = normPattern.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }
                    tokens.any { token ->
                        if (rule.exactMatch) normSender == token else normSender.contains(token)
                    }
                }
            }
            MatchType.SINGLE_WORD -> {
                if (normPattern.isEmpty()) return false
                if (rule.exactMatch) {
                    containsWholeWord(normBody, normPattern)
                } else {
                    normBody.contains(normPattern)
                }
            }
            MatchType.MULTI_WORD_ALL -> {
                val words = normPattern.split(',', '،', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                if (words.isEmpty()) return false
                words.all { word ->
                    if (rule.exactMatch) containsWholeWord(normBody, word) else normBody.contains(word)
                }
            }
            MatchType.EXACT_PHRASE -> {
                if (normPattern.isEmpty()) return false
                if (rule.exactMatch) {
                    normBody == normPattern || containsWholePhrase(normBody, normPattern)
                } else {
                    normBody.contains(normPattern)
                }
            }
            MatchType.SUBSTRING -> {
                normPattern.isNotEmpty() && normBody.contains(normPattern)
            }
            MatchType.HAS_LINK -> {
                SmartScoringEngine.extractUrls(body).isNotEmpty()
            }
            MatchType.REGEX -> {
                if (pattern.isEmpty()) return false
                val target = if (rule.whitespaceSensitive) body else PersianTextNormalizer.normalizePersian(body, rule.caseSensitive, false)
                safeRegexMatches(pattern, target, rule.caseSensitive)
            }
        }
    }

    fun isValidRegex(pattern: String): Boolean {
        if (pattern.isBlank()) return false
        return try {
            Regex(pattern)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun safeRegexMatches(pattern: String, text: String, caseSensitive: Boolean): Boolean {
        return try {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(pattern, options).containsMatchIn(text)
        } catch (_: Exception) {
            false
        }
    }

    private fun containsWholeWord(text: String, word: String): Boolean {
        if (word.isEmpty()) return false
        val escaped = Regex.escape(word)
        return Regex("(^|\\s|[.,!?:؛،«»()\\[\\]\"'])$escaped($|\\s|[.,!?:؛،«»()\\[\\]\"'])")
            .containsMatchIn(text)
    }

    private fun containsWholePhrase(text: String, phrase: String): Boolean = containsWholeWord(text, phrase)
}
