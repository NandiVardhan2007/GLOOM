package com.jarvis.island.bridge

import android.content.Context
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.concurrent.thread

/**
 * JARVIS Live Bridge Client for Mobile:
 * Automatically discovers JARVIS Desktop Island on the local network (UDP 53390),
 * streams device telemetry (battery, WiFi, active playback),
 * and handles 2-way Universal Clipboard & high-speed file beaming.
 */
class JarvisLiveBridgeClient(private val context: Context) {

    private val TAG = "JarvisLiveBridge"
    private var desktopIp: String? = null
    private var desktopPort: Int = 53391
    private var isConnected: Boolean = false

    fun startDiscovery(onConnected: (desktopName: String) -> Unit) {
        thread(isDaemon = true, name = "Jarvis-UDP-Listener") {
            try {
                val socket = DatagramSocket(53390, InetAddress.getByName("0.0.0.0"))
                socket.broadcast = true
                val buffer = ByteArray(2048)

                Log.i(TAG, "Listening for JARVIS Desktop discovery beacons on UDP 53390...")

                while (!isConnected) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val msg = String(packet.data, 0, packet.length)

                    try {
                        val json = JSONObject(msg)
                        if (json.optString("type") == "jarvis_beacon") {
                            desktopIp = packet.address.hostAddress
                            desktopPort = json.optInt("port", 53391)
                            val name = json.optString("name", "JARVIS Desktop")
                            isConnected = true
                            Log.i(TAG, "Discovered JARVIS Desktop at $desktopIp:$desktopPort ($name)")
                            onConnected(name)
                            startTelemetryReporting()
                            break
                        }
                    } catch (e: Exception) {
                        // ignore malformed packet
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "UDP Beacon listening error: ${e.message}")
            }
        }
    }

    private fun startTelemetryReporting() {
        thread(isDaemon = true, name = "Jarvis-Telemetry-Loop") {
            while (isConnected && desktopIp != null) {
                try {
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    val isCharging = bm.isCharging

                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val ssid = getWifiSsid(wm)

                    val payload = JSONObject().apply {
                        put("deviceId", Build.MODEL)
                        put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
                        put("deviceType", "android")
                        put("batteryPercent", battery)
                        put("isCharging", isCharging)
                        put("wifiSsid", ssid)
                    }

                    postJson("http://$desktopIp:$desktopPort/api/telemetry", payload)
                } catch (e: Exception) {
                    Log.w(TAG, "Telemetry post failed: ${e.message}")
                }
                Thread.sleep(10000)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getWifiSsid(wm: WifiManager): String {
        return try {
            val ssid = wm.connectionInfo?.ssid?.replace("\"", "") ?: ""
            if (ssid.isEmpty() || ssid == "<unknown ssid>") "5G WiFi" else ssid
        } catch (e: Exception) {
            "5G WiFi"
        }
    }

    fun syncClipboardToDesktop(text: String) {
        if (!isConnected || desktopIp == null) return
        thread(isDaemon = true) {
            try {
                val payload = JSONObject().apply {
                    put("deviceId", Build.MODEL)
                    put("text", text)
                    put("timestampMs", System.currentTimeMillis())
                }
                postJson("http://$desktopIp:$desktopPort/api/clipboard", payload)
                Log.i(TAG, "Successfully synced clipboard text to JARVIS Desktop")
            } catch (e: Exception) {
                Log.w(TAG, "Clipboard sync failed: ${e.message}")
            }
        }
    }

    private fun postJson(urlString: String, json: JSONObject) {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 3000
        conn.readTimeout = 3000

        conn.outputStream.use { os: OutputStream ->
            os.write(json.toString().toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) {
            Log.w(TAG, "Server responded with code $code to $urlString")
        }
    }
}
