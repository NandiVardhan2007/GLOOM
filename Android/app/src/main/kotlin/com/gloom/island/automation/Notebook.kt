package com.gloom.island.automation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local notebook: short notes and reminders, stored only on this phone.
 * "Remember this" and "remind me" must have somewhere real to land before the
 * assistant is allowed to say it saved them.
 */
object Notebook {

    private const val PREFS = "gloom_notebook"
    private const val KEY_NOTES = "notes"
    private const val KEY_REMINDERS = "reminders"
    private const val MAX_ENTRIES = 400

    data class Note(
        val id: String,
        val body: String,
        val createdAtMs: Long,
    )

    data class Reminder(
        val id: String,
        val text: String,
        val triggerAtMs: Long,
        val done: Boolean,
    )

    fun notes(context: Context): List<Note> {
        val raw = prefs(context).getString(KEY_NOTES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    add(
                        Note(
                            id = json.optString("id"),
                            body = json.optString("body"),
                            createdAtMs = json.optLong("createdAtMs"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
            .filter { it.id.isNotEmpty() && it.body.isNotBlank() }
            .sortedByDescending { it.createdAtMs }
    }

    fun addNote(context: Context, body: String): Note {
        val note = Note(
            id = UUID.randomUUID().toString(),
            body = body.trim(),
            createdAtMs = System.currentTimeMillis(),
        )
        val updated = (listOf(note) + notes(context)).take(MAX_ENTRIES)
        writeNotes(context, updated)
        return note
    }

    fun deleteNote(context: Context, id: String) {
        writeNotes(context, notes(context).filterNot { it.id == id })
    }

    fun reminders(context: Context): List<Reminder> {
        val raw = prefs(context).getString(KEY_REMINDERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    add(
                        Reminder(
                            id = json.optString("id"),
                            text = json.optString("text"),
                            triggerAtMs = json.optLong("triggerAtMs"),
                            done = json.optBoolean("done", false),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
            .filter { it.id.isNotEmpty() && it.text.isNotBlank() }
            .sortedBy { it.triggerAtMs }
    }

    fun pendingReminders(context: Context, now: Long = System.currentTimeMillis()): List<Reminder> =
        reminders(context)
            .filter { !it.done && it.triggerAtMs > now }
            .sortedBy { it.triggerAtMs }

    fun addReminder(context: Context, text: String, triggerAtMs: Long): Reminder {
        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            triggerAtMs = triggerAtMs,
            done = false,
        )
        val updated = (reminders(context) + reminder)
            .sortedBy { it.triggerAtMs }
            .take(MAX_ENTRIES)
        writeReminders(context, updated)
        return reminder
    }

    fun completeReminder(context: Context, id: String) {
        val updated = reminders(context).map {
            if (it.id == id) it.copy(done = true) else it
        }
        writeReminders(context, updated)
    }

    fun deleteReminder(context: Context, id: String) {
        writeReminders(context, reminders(context).filterNot { it.id == id })
    }

    fun nextReminder(context: Context): Reminder? =
        pendingReminders(context).firstOrNull()

    private fun writeNotes(context: Context, notes: List<Note>) {
        val array = JSONArray()
        notes.take(MAX_ENTRIES).forEach { note ->
            array.put(
                JSONObject().apply {
                    put("id", note.id)
                    put("body", note.body)
                    put("createdAtMs", note.createdAtMs)
                }
            )
        }
        prefs(context).edit().putString(KEY_NOTES, array.toString()).apply()
    }

    private fun writeReminders(context: Context, reminders: List<Reminder>) {
        val array = JSONArray()
        reminders.take(MAX_ENTRIES).forEach { reminder ->
            array.put(
                JSONObject().apply {
                    put("id", reminder.id)
                    put("text", reminder.text)
                    put("triggerAtMs", reminder.triggerAtMs)
                    put("done", reminder.done)
                }
            )
        }
        prefs(context).edit().putString(KEY_REMINDERS, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
