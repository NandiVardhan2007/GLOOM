package com.gloom.island

import android.app.Application
import android.os.Build
import android.util.Log
import com.gloom.island.root.RootBridge
import com.gloom.island.settings.GloomIslandSettings
import org.lsposed.hiddenapibypass.HiddenApiBypass

class GloomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("")
                Log.i("GloomApplication", "Hidden API exemptions applied successfully")
            } catch (e: Throwable) {
                Log.w("GloomApplication", "Could not apply hidden API exemptions", e)
            }
        }

        // Apply KernelSU privileged features (immortal OOM priority & exclusive island heads-up)
        try {
            GloomIslandSettings.instance.load(this)
            Thread {
                if (RootBridge.isRootAvailable(forceRefresh = true)) {
                    if (GloomIslandSettings.instance.rootImmortalProcess) {
                        val success = RootBridge.applyImmortalOomPriority()
                        Log.i("GloomApplication", "Immortal OOM priority applied: $success")
                    }
                    if (GloomIslandSettings.instance.suppressNativeHeadsUp) {
                        kotlinx.coroutines.runBlocking {
                            RootBridge.setNativeHeadsUpSuppressed(true)
                        }
                    }
                }
            }.start()
        } catch (e: Throwable) {
            Log.w("GloomApplication", "Could not apply root configurations", e)
        }
    }
}
