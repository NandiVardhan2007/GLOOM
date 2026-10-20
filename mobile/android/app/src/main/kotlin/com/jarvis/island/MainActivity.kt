package com.jarvis.island

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import com.jarvis.island.service.JarvisIslandOverlayService

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple onboarding & permission screen
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            setBackgroundColor(android.graphics.Color.parseColor("#0d0d14"))
        }

        val title = TextView(this).apply {
            text = "JARVIS Island"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        layout.addView(title)

        val desc = TextView(this).apply {
            text = "Apple & HarmonyOS-grade Dynamic Island with real-time PC LiveBridge continuity."
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#94a3b8"))
            setPadding(0, 16, 0, 32)
        }
        layout.addView(desc)

        val btnOverlay = Button(this).apply {
            text = "Enable Overlay Permission"
            setBackgroundColor(android.graphics.Color.parseColor("#0284c7"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    startOverlayService()
                }
            }
        }
        layout.addView(btnOverlay)

        setContentView(layout)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, JarvisIslandOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
