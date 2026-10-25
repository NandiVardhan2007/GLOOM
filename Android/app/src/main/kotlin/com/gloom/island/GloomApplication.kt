package com.gloom.island

import android.app.Application
import android.os.Build
import android.util.Log
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

        try {
            GloomIslandSettings.instance.load(this)
        } catch (e: Throwable) {
            Log.w("GloomApplication", "Could not load settings", e)
        }
    }
}
