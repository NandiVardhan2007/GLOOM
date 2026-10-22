package com.gloom.island.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gloom.island.MainActivity
import com.gloom.island.service.VoiceUnlockActivity
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Always-on, fully offline "Hey GLOOM" detector powered by Vosk. It runs as a
 * microphone foreground service and opens the Siri-style assistant when the
 * wake phrase is heard.
 */
class WakeWordService : Service() {

    private var running = false
    private var worker: Thread? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!VoicePrefs.isWakeWordEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Microphone permission is missing")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!WakeWordModel.isInstalled(this)) {
            Log.w(TAG, "Wake word model is not installed")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            worker = Thread({ listenLoop() }, "Gloom-WakeWord").apply {
                isDaemon = true
                start()
            }
        }
        return START_STICKY
    }

    private fun listenLoop() {
        val sampleRate = 16000
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ),
            8192
        )
        try {
            model = Model(WakeWordModel.path(this).absolutePath)
            recognizer = Recognizer(model, sampleRate.toFloat())
            val buffer = ShortArray(bufferSize / 2)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "Could not initialise the microphone")
                return
            }
            audioRecord?.startRecording()
            var cooldownUntil = 0L

            while (running) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read <= 0) {
                    Thread.sleep(30)
                    continue
                }
                val now = System.currentTimeMillis()
                if (now < cooldownUntil) {
                    continue
                }
                val recognizer = recognizer ?: break
                val grammar = recognizer.acceptWaveForm(buffer, read)
                val text = if (grammar) {
                    JSONObject(recognizer.result).optString("text")
                } else {
                    JSONObject(recognizer.partialResult).optString("partial")
                }
                if (containsWakeWord(text)) {
                    cooldownUntil = now + 8000
                    recognizer.reset()
                    openAssistant()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wake word listener stopped: ${e.message}")
        } finally {
            releaseAudio()
        }
    }

    private fun containsWakeWord(text: String): Boolean {
        if (text.isBlank()) return false
        val normalized = text.lowercase()
        return normalized.contains("gloom") ||
            normalized.contains("jervis") ||
            normalized.contains("jarvus") ||
            normalized.contains("hey gloom")
    }

    private fun openAssistant() {
        try {
            startActivity(
                Intent(this, VoiceUnlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the assistant: ${e.message}")
        }
    }

    private fun releaseAudio() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        audioRecord?.release()
        audioRecord = null
        try {
            recognizer?.close()
        } catch (_: Exception) {
        }
        recognizer = null
        try {
            model?.close()
        } catch (_: Exception) {
        }
        model = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "GLOOM voice",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Keeps the Hey GLOOM wake word listening"
                        setShowBadge(false)
                    }
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("GLOOM is listening")
            .setContentText("Say “Hey GLOOM”")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        releaseAudio()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GloomWakeWord"
        private const val CHANNEL_ID = "gloom_voice"
        private const val NOTIFICATION_ID = 4103

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WakeWordService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}
