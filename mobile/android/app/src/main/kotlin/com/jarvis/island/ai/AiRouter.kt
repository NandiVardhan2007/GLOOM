package com.jarvis.island.ai

import android.content.Context
import com.jarvis.island.bridge.JarvisLiveBridgeClient
import com.jarvis.island.settings.JarvisIslandSettings

/**
 * One entry point for remote model chat, with the fallback order the app
 * promises: desktop LiveBridge first, then a direct cloud call with the
 * user's own key, then nothing (the caller keeps the local brain).
 */
object AiRouter {

    const val SOURCE_DESKTOP = "desktop"
    const val SOURCE_CLOUD = "cloud"
    const val SOURCE_NONE = "none"

    fun isDesktopConnected(): Boolean {
        val client = JarvisLiveBridgeClient.activeClient ?: return false
        return client.isConnected && client.desktopIp != null
    }

    fun isCloudConfigured(context: Context): Boolean = SecureKeyStore.hasCloudKeys(context)

    /** True when a remote model could be reached right now. */
    fun canReachModel(context: Context): Boolean =
        isDesktopConnected() || isCloudConfigured(context)

    /**
     * Sends [messages] through the best available transport.
     *
     * `local` never leaves the phone. `remote` is desktop-only by choice.
     * `auto` uses the desktop when paired and falls through to the saved cloud
     * key when the desktop does not answer. [onResult] lands on the main thread.
     */
    fun request(
        context: Context,
        messages: List<Pair<String, String>>,
        onResult: (reply: String?, error: String?, source: String) -> Unit
    ) {
        val brain = JarvisIslandSettings.instance.aiBrain
        if (brain == "local") {
            onResult(null, "The on-device brain is selected.", SOURCE_NONE)
            return
        }

        val client = JarvisLiveBridgeClient.activeClient
        val desktopReady = client != null && client.isConnected && client.desktopIp != null
        if (desktopReady) {
            client.requestAi(messages) { reply, error ->
                if (reply != null) {
                    onResult(reply, null, SOURCE_DESKTOP)
                } else if (brain == "auto" && isCloudConfigured(context)) {
                    StandaloneAiClient.request(context, messages) { cloudReply, cloudError, _ ->
                        onResult(cloudReply, cloudError, if (cloudReply != null) SOURCE_CLOUD else SOURCE_NONE)
                    }
                } else {
                    onResult(null, error, SOURCE_NONE)
                }
            }
            return
        }

        if (brain == "remote") {
            onResult(null, "The desktop isn't connected.", SOURCE_NONE)
            return
        }

        if (!isCloudConfigured(context)) {
            onResult(null, "No cloud API key saved. Add one in Settings > AI & Brain.", SOURCE_NONE)
            return
        }

        StandaloneAiClient.request(context, messages) { reply, error, _ ->
            onResult(reply, error, if (reply != null) SOURCE_CLOUD else SOURCE_NONE)
        }
    }
}
