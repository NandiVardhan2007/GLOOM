package com.gloom.island.automation

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Small, local time parser for reminders, alarms and calendar entries.
 *
 * It understands the phrases people actually say: "in 20 minutes", "at 7:30",
 * "tomorrow at 9 am", "tonight". It never guesses a time from a bare number
 * without a clock signal, so "remind me to buy 2 apples" stays a note.
 */
object TimeParser {

    data class Parsed(val text: String, val atMs: Long?)

    private val inPattern = Regex(
        """\bin\s+(\d{1,3}|an?|half\s+an?)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val atPattern = Regex(
        """\bat\s+(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?\b""",
        RegexOption.IGNORE_CASE
    )
    private val bareAmPmPattern = Regex(
        """\b(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)\b""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String, now: Long = System.currentTimeMillis()): Long? {
        val lower = text.lowercase()

        inPattern.find(lower)?.let { match ->
            val raw = match.groupValues[1]
            val amount = when {
                raw.startsWith("half") -> 30L
                raw == "a" || raw == "an" -> 1L
                else -> raw.toLongOrNull() ?: 1L
            }
            val unit = match.groupValues[2]
            val multiplier = when {
                unit.startsWith("sec") -> 1_000L
                unit.startsWith("min") -> 60_000L
                else -> 3_600_000L
            }
            return now + amount * multiplier
        }

        val zone = ZoneId.systemDefault()
        val nowZoned = Instant.ofEpochMilli(now).atZone(zone)
        val tomorrow = lower.contains("tomorrow")

        atPattern.find(lower)?.let { match ->
            return resolveClock(
                rawHour = match.groupValues[1].toIntOrNull() ?: return null,
                minute = match.groupValues[2].toIntOrNull() ?: 0,
                meridiem = match.groupValues[3],
                tomorrow = tomorrow,
                now = nowZoned,
            )
        }
        bareAmPmPattern.find(lower)?.let { match ->
            return resolveClock(
                rawHour = match.groupValues[1].toIntOrNull() ?: return null,
                minute = match.groupValues[2].toIntOrNull() ?: 0,
                meridiem = match.groupValues[3],
                tomorrow = tomorrow,
                now = nowZoned,
            )
        }

        val hour = when {
            lower.contains("tonight") -> 20
            lower.contains("noon") -> 12
            lower.contains("midnight") -> 0
            tomorrow -> 9
            else -> return null
        }
        return resolveClock(hour, 0, "", tomorrow, nowZoned)
    }

    /** The task with its time phrase removed, so notes stay readable. */
    fun stripTime(text: String): String {
        var result = text
        inPattern.find(result)?.let { result = result.removeRange(it.range) }
        atPattern.find(result)?.let { result = result.removeRange(it.range) }
        bareAmPmPattern.find(result)?.let { result = result.removeRange(it.range) }
        result = result
            .replace(Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\btonight\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b(at\s+noon|at\s+midnight)\b""", RegexOption.IGNORE_CASE), " ")
        return result.replace(Regex("""\s{2,}"""), " ").trim().trim(',', '.', '-')
    }

    fun extract(text: String): Parsed = Parsed(stripTime(text), parse(text))

    fun format(ms: Long): String {
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(ms).atZone(zone)
        val hour = when {
            time.hour == 0 -> 12
            time.hour > 12 -> time.hour - 12
            else -> time.hour
        }
        val suffix = if (time.hour < 12) "AM" else "PM"
        val minute = time.minute.toString().padStart(2, '0')
        val today = Instant.now().atZone(zone).toLocalDate()
        return when (time.toLocalDate()) {
            today -> "$hour:$minute $suffix"
            today.plusDays(1) -> "Tomorrow $hour:$minute $suffix"
            else -> "${time.month.name.lowercase().replaceFirstChar { it.uppercase() }} " +
                "${time.dayOfMonth}, $hour:$minute $suffix"
        }
    }

    private fun resolveClock(
        rawHour: Int,
        minute: Int,
        meridiem: String,
        tomorrow: Boolean,
        now: ZonedDateTime,
    ): Long? {
        if (minute !in 0..59) return null
        val pm = meridiem.startsWith("p")
        val am = meridiem.startsWith("a")
        var hour = rawHour.coerceIn(0, 23)
        if (pm && hour < 12) hour += 12
        if (am && hour == 12) hour = 0

        val base = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        val resolved = when {
            tomorrow -> base.plusDays(1)
            meridiem.isNotBlank() -> if (base.isAfter(now)) base else base.plusDays(1)
            else -> {
                val evening = now.withHour((hour + 12) % 24).withMinute(minute)
                    .withSecond(0).withNano(0)
                when {
                    base.isAfter(now) -> base
                    hour <= 12 && evening.isAfter(now) -> evening
                    else -> base.plusDays(1)
                }
            }
        }
        return resolved.toInstant().toEpochMilli()
    }
}
