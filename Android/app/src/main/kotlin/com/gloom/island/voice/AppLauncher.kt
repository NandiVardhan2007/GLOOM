package com.gloom.island.voice

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

    private const val TAG = "GloomAppLauncher"

    private data class LaunchableApp(val label: String, val packageName: String, val component: ComponentName)

    private val PACKAGE_ALIASES = mapOf(
        "whatsapp" to "com.whatsapp",
        "whats up" to "com.whatsapp",
        "what's up" to "com.whatsapp",
        "whatsap" to "com.whatsapp",
        "watsapp" to "com.whatsapp",
        "watsap" to "com.whatsapp",
        "whatapp" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "instgram" to "com.instagram.android",
        "istagram" to "com.instagram.android",
        "youtube" to "com.google.android.youtube",
        "you tube" to "com.google.android.youtube",
        "utube" to "com.google.android.youtube",
        "spotify" to "com.spotify.music",
        "spodify" to "com.spotify.music",
        "spotfy" to "com.spotify.music",
        "harmonix" to "com.aniruddh.harmonix",
        "harmonix music" to "com.aniruddh.harmonix",
        "harminix" to "com.aniruddh.harmonix",
        "chrome" to "com.android.chrome",
        "crome" to "com.android.chrome",
        "google crome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "g mail" to "com.google.android.gm",
        "g-mail" to "com.google.android.gm",
        "google maps" to "com.google.android.apps.maps",
        "maps" to "com.google.android.apps.maps",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "netflix" to "com.netflix.mediaclient",
        "netfliks" to "com.netflix.mediaclient",
        "netflex" to "com.netflix.mediaclient",
        "facebook" to "com.facebook.katana",
        "face book" to "com.facebook.katana",
        "fb" to "com.facebook.katana",
        "twitter" to "com.twitter.android",
        "twiter" to "com.twitter.android",
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
            .replace("hey gloom", " ")
            .replace("gloom", " ")
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
     * Resolves an app query to (packageName, displayLabel), or null if not found.
     */
    fun resolvePackage(context: Context, rawQuery: String): Pair<String, String>? {
        val query = rawQuery.lowercase().trim()
        if (query.isEmpty()) return null
        val aliasPackage = PACKAGE_ALIASES[query]
        if (aliasPackage != null) {
            val label = appLabel(context.packageManager, aliasPackage)
            return Pair(aliasPackage, label)
        }
        val apps = launchableApps(context.packageManager)
        val normQuery = phoneticNormalize(query)
        val match = apps.firstOrNull { it.label.equals(query, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(query) }
            ?: apps.firstOrNull { it.label.lowercase().contains(query) }
            ?: apps.firstOrNull { it.packageName.lowercase().contains(query) }
            ?: apps.firstOrNull { phoneticNormalize(it.label) == normQuery }
            ?: apps.firstOrNull { phoneticNormalize(it.label).contains(normQuery) || normQuery.contains(phoneticNormalize(it.label)) }
        return match?.let { Pair(it.packageName, it.label) }
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
        val normQuery = phoneticNormalize(query)
        val match = apps.firstOrNull { it.label.equals(query, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(query) }
            ?: apps.firstOrNull { it.label.lowercase().contains(query) }
            ?: apps.firstOrNull { it.packageName.lowercase().contains(query) }
            // Phonetic & soundex-based fuzzy matching
            ?: apps.firstOrNull { phoneticNormalize(it.label) == normQuery }
            ?: apps.firstOrNull { phoneticNormalize(it.label).contains(normQuery) || normQuery.contains(phoneticNormalize(it.label)) }
            // Levenshtein fuzzy distance matching (tolerates 1-2 character typos)
            ?: apps.minByOrNull { levenshtein(phoneticNormalize(it.label), normQuery) }?.takeIf {
                levenshtein(phoneticNormalize(it.label), normQuery) <= (if (normQuery.length >= 6) 2 else 1)
            }
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

    private fun phoneticNormalize(s: String): String {
        return s.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .replace("ph", "f")
            .replace("ck", "k")
            .replace("c", "k")
            .replace("z", "s")
            .replace("w", "v")
            .replace(Regex("(.)\\1+"), "$1")
    }

    private fun levenshtein(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }
}
