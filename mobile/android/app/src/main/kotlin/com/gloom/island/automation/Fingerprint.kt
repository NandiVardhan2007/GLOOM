package com.gloom.island.automation

import java.security.MessageDigest

/**
 * Builds an opaque, in-memory-only fingerprint for the reel on screen.
 *
 * Adapted from the open-source reels-tracker (https://github.com/s0uvik/reels-tracker).
 * The inputs (author label, caption snippet, content descriptions) are hashed
 * immediately and never stored or logged. Only "did the reel change?" matters.
 */
object Fingerprint {
    private const val MAX_PART_LENGTH = 120

    fun of(parts: List<CharSequence?>): String? {
        val cleaned = parts.mapNotNull { part ->
            part?.toString()?.trim()?.take(MAX_PART_LENGTH)?.takeIf { it.isNotEmpty() }
        }
        if (cleaned.isEmpty()) return null
        return sha256(cleaned.joinToString("\u001F"))
    }

    fun ofIndex(index: Int): String = "idx:$index"

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(32)
        for (i in 0 until 16) {
            sb.append(String.format("%02x", digest[i]))
        }
        return sb.toString()
    }
}
