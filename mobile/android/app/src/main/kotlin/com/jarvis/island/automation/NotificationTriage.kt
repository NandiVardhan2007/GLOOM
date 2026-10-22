package com.jarvis.island.automation

/**
 * Notification triage: act, tell, or ignore.
 *
 * The rules are local and deterministic. An alert is only dropped when it is
 * clearly promotional, and a one-time code is always surfaced with the code
 * extracted so it can be copied from the island.
 */
object NotificationTriage {

    enum class Kind { OTP, MESSAGE, ALERT, PROMO }

    data class Verdict(
        val kind: Kind,
        val show: Boolean,
        val otp: String?,
    )

    private val otpContext = Regex(
        """(?:code|otp|one[\s-]?time|verification|verify|security code|passcode|pin|login)\D{0,24}(\d{4,8})""",
        RegexOption.IGNORE_CASE
    )
    private val otpContextAfter = Regex(
        """(\d{4,8})\D{0,24}(?:is your|as your)?\s*(?:code|otp|one[\s-]?time|verification|passcode|pin)""",
        RegexOption.IGNORE_CASE
    )
    private val separatedOtp = Regex("""\b(\d{3})[-\s](\d{3,5})\b""")

    private val promoWords = listOf(
        "% off", "off today", "sale", "discount", "deal of", "offer ends",
        "limited time", "shop now", "buy now", "free trial", "coupon", "promo",
        "new arrivals", "unsubscribe", "save big", "flash sale", "clearance",
        "upgrade now", "exclusive offer", "order now", "earn points",
        "recommended for you", "you might like", "trending now", "last chance"
    )

    private val messagePackages = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
        "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging",
        "com.discord", "com.slack", "com.instagram.android", "com.facebook.orca",
        "com.microsoft.teams", "com.viber.voip", "com.signal", "com.google.android.gm",
    )

    private val messageWords = listOf(
        "sent you a message", "sent a photo", "replied", "mentioned you",
        "new message", "missed call", "is typing", "reacted to your"
    )

    fun classify(
        packageName: String,
        title: String,
        text: String,
        channelName: String = "",
    ): Verdict {
        val combined = "$title $text $channelName"
        extractOtp(combined)?.let { code ->
            return Verdict(Kind.OTP, show = true, otp = code)
        }

        val lower = combined.lowercase()
        if (promoWords.any { lower.contains(it) } && !isMessagePackage(packageName)) {
            return Verdict(Kind.PROMO, show = false, otp = null)
        }
        if (isMessagePackage(packageName) ||
            messageWords.any { lower.contains(it) } ||
            lower.contains("message from")
        ) {
            return Verdict(Kind.MESSAGE, show = true, otp = null)
        }
        return Verdict(Kind.ALERT, show = true, otp = null)
    }

    fun extractOtp(text: String): String? {
        otpContext.find(text)?.let { match ->
            normalize(match.groupValues[1])?.let { return it }
        }
        otpContextAfter.find(text)?.let { match ->
            normalize(match.groupValues[1])?.let { return it }
        }
        separatedOtp.find(text)?.let { match ->
            normalize(match.groupValues[1] + match.groupValues[2])?.let { return it }
        }
        return null
    }

    private fun normalize(raw: String): String? {
        val code = raw.filter { it.isDigit() }
        if (code.length !in 4..8) return null
        // A year or a plain round number is not a one-time code.
        if (code.length == 4 && code.toIntOrNull()?.let { it in 1900..2100 } == true) return null
        return code
    }

    private fun isMessagePackage(packageName: String): Boolean =
        packageName in messagePackages || packageName.contains("messag", ignoreCase = true)
}
