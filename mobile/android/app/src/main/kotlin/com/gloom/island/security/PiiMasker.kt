package com.gloom.island.security

/**
 * PII & Financial Masking Filter (Google Artemis architecture).
 *
 * Sanitizes screen hierarchy summaries and prompts before they are sent to
 * cloud LLM providers, ensuring sensitive financial, credential, and identity tokens
 * remain strictly on the local device.
 */
object PiiMasker {

    // Visa, MasterCard, Amex, Discover, RuPay (13 to 19 digits with optional spaces or dashes)
    private val creditCardRegex = Regex("""\b(?:\d[ -]*?){13,19}\b""")

    // CVV / CVC (3 or 4 digits preceded by cvv/cvc indicators)
    private val cvvRegex = Regex("""(?i)\b(?:cvv|cvc|security\s+code)[:\s=]*(\d{3,4})\b""")

    // One-Time Passwords / Verification codes (4-8 digits)
    private val otpRegex = Regex("""(?i)\b(?:otp|code|pin|verification)[:\s=]*([0-9]{4,8})\b""")

    // Common password fields or masked asterisks/dots
    private val passwordDotsRegex = Regex("""[•●*]{4,}""")

    /**
     * Replaces sensitive credit card numbers, CVVs, and credentials with safe redaction tags.
     */
    fun maskText(text: String): String {
        if (text.isBlank()) return text

        var sanitized = text

        // Mask credit cards
        sanitized = creditCardRegex.replace(sanitized) { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length in 13..19 && isValidLuhn(digits)) {
                "[CARD_MASKED_****${digits.takeLast(4)}]"
            } else {
                match.value
            }
        }

        // Mask CVV
        sanitized = cvvRegex.replace(sanitized) { match ->
            match.value.replace(match.groupValues[1], "[CVV_MASKED]")
        }

        // Mask OTP codes
        sanitized = otpRegex.replace(sanitized) { match ->
            match.value.replace(match.groupValues[1], "[OTP_MASKED]")
        }

        // Mask password dots
        sanitized = passwordDotsRegex.replace(sanitized, "[PASSWORD_PROTECTED]")

        return sanitized
    }

    /**
     * Luhn checksum algorithm to prevent false positives on random number strings.
     */
    private fun isValidLuhn(number: String): Boolean {
        var checksum = 0
        var doubleDigit = false
        for (i in number.length - 1 downTo 0) {
            var digit = number[i] - '0'
            if (doubleDigit) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            checksum += digit
            doubleDigit = !doubleDigit
        }
        return checksum % 10 == 0
    }
}
