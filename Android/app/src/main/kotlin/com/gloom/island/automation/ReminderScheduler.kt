package com.gloom.island.automation

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gloom.island.MainActivity
import com.gloom.island.service.GloomIslandAccessibilityService

/**
 * Real scheduling for reminders.
 *
 * A reminder is scheduled with AlarmManager before the assistant confirms it.
 * When exact alarms are restricted the reminder still fires, just within the
 * system's inexact window, and the self-check says so.
 */
object ReminderScheduler {

    const val ACTION_FIRE = "com.gloom.island.REMINDER_FIRE"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_TEXT = "reminder_text"
    const val CHANNEL_ID = "gloom_reminders"
    const val NOTIFICATION_ID_BASE = 7_400

    fun schedule(context: Context, reminder: Notebook.Reminder): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        val triggerAt = reminder.triggerAtMs.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        val pending = fireIntent(context, reminder.id, reminder.text)
        return try {
            if (canUseExactAlarms(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(context: Context, id: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(fireIntent(context, id, ""))
    }

    /** Called on boot and app start so reminders survive a restart. */
    fun rescheduleAll(context: Context) {
        for (reminder in Notebook.pendingReminders(context)) {
            schedule(context, reminder)
        }
    }

    private fun canUseExactAlarms(alarmManager: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private fun fireIntent(context: Context, id: String, text: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TEXT, text)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/**
 * Delivers a reminder to the island and to the notification shade. The shade
 * copy is the fallback when the island service is not running.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            ReminderScheduler.rescheduleAll(context)
            return
        }
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID).orEmpty()
        val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT).orEmpty()
        if (text.isBlank()) return

        if (id.isNotBlank()) Notebook.completeReminder(context, id)
        showInShade(context, id, text)
        val accessibility = GloomIslandAccessibilityService.instance
        if (accessibility != null) {
            accessibility.showLocalNotification(
                title = "Reminder",
                text = text,
                contentIntent = openAppIntent(context),
            )
        }
    }

    private fun showInShade(context: Context, id: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(ReminderScheduler.CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    ReminderScheduler.CHANNEL_ID,
                    "Reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Reminders you asked GLOOM to keep."
                }
                manager.createNotificationChannel(channel)
            }
        }
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, id))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(
                ReminderScheduler.NOTIFICATION_ID_BASE + (id.hashCode() and 0xFFF),
                notification,
            )
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS may be off; the island still shows the reminder.
        }
    }

    private fun openAppIntent(context: Context, id: String = "reminder"): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_screen", "home")
        }
        return PendingIntent.getActivity(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
