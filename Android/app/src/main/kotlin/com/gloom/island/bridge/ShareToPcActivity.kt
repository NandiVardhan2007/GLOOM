package com.gloom.island.bridge

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Transparent activity handling:
 * 1. System Share-Sheet: "Share to PC" (android.intent.action.SEND and SEND_MULTIPLE)
 * 2. Dynamic Island: Tap "File Sharing" to pick one or multiple files and beam to PC
 */
class ShareToPcActivity : ComponentActivity() {

    private val multiFilePicker = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNullOrEmpty()) {
            finish()
        } else {
            sendUrisToPc(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        when (action) {
            "com.gloom.island.ACTION_PICK_AND_SHARE" -> {
                multiFilePicker.launch("*/*")
            }
            Intent.ACTION_SEND -> {
                val uri = extractSingleUri(intent)
                if (uri != null) {
                    sendUrisToPc(listOf(uri))
                } else {
                    Toast.makeText(this, "No file provided to share", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = extractMultipleUris(intent)
                if (uris.isNotEmpty()) {
                    sendUrisToPc(uris)
                } else {
                    Toast.makeText(this, "No files provided to share", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            else -> {
                finish()
            }
        }
    }

    private fun extractSingleUri(intent: Intent): Uri? {
        val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (streamUri != null) return streamUri

        val clip = intent.clipData
        if (clip != null && clip.itemCount > 0) {
            val clipUri = clip.getItemAt(0).uri
            if (clipUri != null) return clipUri
        }

        return intent.data
    }

    private fun extractMultipleUris(intent: Intent): List<Uri> {
        val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        if (!streamUris.isNullOrEmpty()) {
            return streamUris
        }

        val list = mutableListOf<Uri>()
        val clip = intent.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { list.add(it) }
            }
        }
        return list
    }

    private fun sendUrisToPc(uris: List<Uri>) {
        val client = GloomLiveBridgeClient.activeClient ?: GloomLiveBridgeClient(applicationContext)
        thread(isDaemon = true, name = "Gloom-ShareToPc-Thread") {
            var waited = 0L
            while ((client.desktopIp == null || !client.isConnected) && waited < 4000L) {
                try {
                    Thread.sleep(250)
                } catch (_: Exception) {}
                waited += 250
            }

            val ip = client.desktopIp
            if (ip.isNullOrBlank() || !client.isConnected) {
                runOnUiThread {
                    Toast.makeText(this@ShareToPcActivity, "Desktop bridge not connected. Ensure desktop is active on same Wi-Fi.", Toast.LENGTH_LONG).show()
                    finish()
                }
                return@thread
            }

            runOnUiThread {
                val label = if (uris.size == 1) "Sending file to PC..." else "Sending ${uris.size} files to PC..."
                Toast.makeText(this@ShareToPcActivity, label, Toast.LENGTH_SHORT).show()
            }

            var allSuccess = true
            uris.forEach { uri ->
                val latch = CountDownLatch(1)
                client.beamUriToDesktop(
                    uri = uri,
                    onProgress = { },
                    onComplete = { success ->
                        if (!success) allSuccess = false
                        latch.countDown()
                    }
                )
                try {
                    latch.await(10, TimeUnit.MINUTES)
                } catch (_: Exception) {}
            }

            runOnUiThread {
                val resultMsg = if (allSuccess) {
                    if (uris.size == 1) "File sent to PC successfully!" else "${uris.size} files sent to PC successfully!"
                } else {
                    "Some files failed to transfer to PC"
                }
                Toast.makeText(this@ShareToPcActivity, resultMsg, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
