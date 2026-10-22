package com.jarvis.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jarvis.island.automation.CommandRouter

/**
 * Debug-only entry point for driving the automation router from adb:
 * adb shell am broadcast -a com.jarvis.island.TEST_AUTOMATION --es command "tap search"
 */
class AutomationTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra("command") ?: return
        val pending = goAsync()
        Thread {
            try {
                val appCtx = context.applicationContext
                var result = CommandRouter.handle(appCtx, command)
                if (!result.handled) {
                    result = com.jarvis.island.automation.LocalBrain.answer(appCtx, command)
                }
                if (!result.handled) {
                    result = com.jarvis.island.automation.AiPlanner.run(appCtx, command)
                }
                Log.i(
                    "JarvisAutomationTest",
                    "command=$command handled=${result.handled} success=${result.success} message=${result.message}"
                )
            } catch (e: Exception) {
                Log.e("JarvisAutomationTest", "command failed", e)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
