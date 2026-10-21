package com.jarvis.island.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log

/**
 * Resolves "open WhatsApp" style commands on the phone itself so the assistant
 * does not have to ask the desktop model to do something it cannot do.
 */
object AppLauncher {

    private const val TAG = "JarvisAppLauncher"

    private data class LaunchableApp(val label: String, val packageName: String, val component: ComponentName)

    private val PACKAGE_ALIASES = mapOf(
        "whatsapp" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "instagram" to "com.instagram.android",
        "youtube" to "com.google.android.youtube",
        "spotify" to "com.spotify.music",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "google maps" to "com.google.android.apps.maps",
        "maps" to "com.google.android.apps.maps",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "netflix" to "com.netflix.mediaclient",
        "facebook" to "com.facebook.katana",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "google pay" to "com.google.android.apps.nbu.paisa.user",
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "phonepe" to "com.phonepe.app",
        "paytm" to "net.one97.paytm",
        "amazon" to "in.amazon.mShop.android.shopping",
        "zoom" to "us.zoom.videomeetings",
        "teams" to "com.microsoft.teams",
        "slack" to "com.Slack",
        "reddit" to "com.reddit.frontpage",
        "linkedin" to "com.linkedin.android",
    )

    private val SYSTEM_TARGETS = mapOf(
        "camera" to { context: Context ->
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        "settings" to { context: Context ->
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        "phone" to { context: Context ->
            Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        "dialer" to { context: Context ->
            Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        "messages" to { context: Context ->
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_MESSAGING)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        "browser" to { context: Context ->
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )

    /** Returns the app name in "open <app>" / "launch <app>" / "start <app>". */
    fun extractAppQuery(text: String): String? {
        val normalized = text.lowercase()
            .replace("hey jarvis", " ")
            .replace("jarvis", " ")
            .trim()
            .trim('.', '!', '?', ',')
        val prefixes = listOf("open up ", "open the ", "open ", "launch ", "start ", "run ")
        for (prefix in prefixes) {
            if (normalized.startsWith(prefix)) {
                val rest = normalized.removePrefix(prefix)
                    .removeSuffix(" app")
                    .trim()
                if (rest.isNotEmpty()) return rest
            }
        }
        return null
    }

    /**
     * Launches the best match and returns its display label, or null when the
     * query does not match any installed app.
     */
    fun launch(context: Context, rawQuery: String): String? {
        val query = rawQuery.lowercase().trim()
        if (query.isEmpty()) return null

        SYSTEM_TARGETS[query]?.let { build ->
            return try {
                context.startActivity(build(context))
                query.replaceFirstChar { it.uppercase() }
            } catch (e: Exception) {
                Log.w(TAG, "System action for '$query' failed: ${e.message}")
                null
            }
        }

        val packageManager = context.packageManager
        val aliasPackage = PACKAGE_ALIASES[query]
        if (aliasPackage != null) {
            packageManager.getLaunchIntentForPackage(aliasPackage)?.let { intent ->
                return try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    appLabel(packageManager, aliasPackage)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not launch $aliasPackage: ${e.message}")
                    null
                }
            }
        }

        val apps = launchableApps(packageManager)
        val match = apps.firstOrNull { it.label.equals(query, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(query) }
            ?: apps.firstOrNull { it.label.lowercase().contains(query) }
            ?: apps.firstOrNull { it.packageName.lowercase().contains(query) }
            ?: return null

        return try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(match.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            match.label
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch ${match.packageName}: ${e.message}")
            null
        }
    }

    private fun launchableApps(packageManager: PackageManager): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0).mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
            if (label.isEmpty()) return@mapNotNull null
            LaunchableApp(label, activity.packageName, ComponentName(activity.packageName, activity.name))
        }
    }

    private fun appLabel(packageManager: PackageManager, packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
