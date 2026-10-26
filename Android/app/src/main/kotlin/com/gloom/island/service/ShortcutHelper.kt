package com.gloom.island.service

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.gloom.island.R

object ShortcutHelper {
    fun pinAssistantShortcut(context: Context) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(
                context,
                "Pinned shortcuts are not supported by your current launcher",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val intent = Intent(context, VoiceUnlockActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pinShortcutInfo = ShortcutInfoCompat.Builder(context, "gloom_siri_assistant_pinned")
            .setShortLabel(context.getString(R.string.shortcut_siri_short))
            .setLongLabel(context.getString(R.string.shortcut_siri_long))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        val pinned = ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, null)
        if (pinned) {
            Toast.makeText(
                context,
                "Shortcut requested! Place it on your home screen.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
