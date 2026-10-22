package com.gloom.island.automation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore

/**
 * The phone's own tools, reached through public Android intents.
 *
 * Calls, texts, alarms and calendar entries open in the user's own apps with
 * the fields filled in. Nothing is sent or saved without the user pressing the
 * final button in that app, which keeps a misheard command from becoming a real
 * message or a real appointment.
 */
object DeviceTools {

    data class Result(val success: Boolean, val message: String)

    fun call(context: Context, query: String): Result {
        val number = resolveNumber(context, query)
            ?: return Result(false, missingNumberMessage(context, query))
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
        return launch(context, intent, "Opening $query in the dialer.")
    }

    fun text(context: Context, query: String, message: String): Result {
        val number = resolveNumber(context, query)
            ?: return Result(false, missingNumberMessage(context, query))
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
            putExtra("sms_body", message)
        }
        return launch(
            context,
            intent,
            "Ready to text $query. Press Send in your messaging app."
        )
    }

    fun email(context: Context, address: String, subject: String, body: String): Result {
        val target = if (address.contains('@')) address.trim()
        else com.gloom.island.ai.AiMemory.resolveEntity(address)?.takeIf { it.contains('@') }
            ?: "kovvurinandivardhanreddy2007@gmail.com"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(target)}")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return launch(context, intent, "Ready to email $target.")
    }

    fun openCamera(context: Context): Result {
        val still = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryStart(context, still)) return Result(true, "Opening the camera.")
        val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(context, capture, "Opening the camera.")
    }

    fun setAlarm(context: Context, hour: Int, minute: Int, label: String): Result {
        if (hour !in 0..23 || minute !in 0..59) return Result(false, "That is not a valid time.")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        val display = formatTime(hour, minute)
        return launch(context, intent, "Opening the clock to set an alarm for $display.")
    }

    fun addCalendarEvent(
        context: Context,
        title: String,
        beginMs: Long,
        durationMinutes: Int = 60,
    ): Result {
        val endMs = beginMs + durationMinutes.coerceIn(5, 24 * 60) * 60_000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
        }
        return launch(context, intent, "Opening the calendar to add $title.")
    }

    fun openContactPicker(context: Context): Result {
        val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
        return launch(context, intent, "Opening contacts.")
    }

    fun resolveNumber(context: Context, query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val digits = trimmed.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 3) return digits

        // Check AiMemory for contact alias or phone number first
        val memResolved = com.gloom.island.ai.AiMemory.resolveEntity("$trimmed number")
            ?: com.gloom.island.ai.AiMemory.resolveEntity("$trimmed phone")
            ?: com.gloom.island.ai.AiMemory.resolveEntity(trimmed)?.filter { it.isDigit() || it == '+' }?.takeIf { it.length >= 3 }
        if (memResolved != null) return memResolved

        if (!hasContactsPermission(context)) return null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        val cursor = try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$trimmed%"),
                null
            )
        } catch (e: Exception) {
            null
        } ?: return null
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    fun hasContactsPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private fun missingNumberMessage(context: Context, query: String): String =
        if (hasContactsPermission(context)) {
            "I couldn't find $query in your contacts."
        } else {
            "I couldn't read contacts, so I need a phone number for $query."
        }

    private fun launch(context: Context, intent: Intent, successMessage: String): Result {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (tryStart(context, intent)) Result(true, successMessage)
        else Result(false, "No app on this phone can handle that.")
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val suffix = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return if (minute == 0) "$displayHour $suffix" else "$displayHour:${minute.toString().padStart(2, '0')} $suffix"
    }
}
