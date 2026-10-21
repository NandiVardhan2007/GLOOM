@file:Suppress("DEPRECATION")
package com.jarvis.island.bridge

import android.content.Context
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Collections
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

/**
 * JARVIS Live Bridge Client for Mobile:
 * Automatically discovers JARVIS Desktop Island on the local network (UDP 53390 + LAN Sweep),
 * streams device telemetry (battery, WiFi, active playback),
 * and handles 2-way Universal Clipboard & high-speed file beaming.
 */
class JarvisLiveBridgeClient(private val context: Context) {

    companion object {
        var activeClient: JarvisLiveBridgeClient? = null
            private set
        @Volatile
        var isDiscoveringGlobally: Boolean = false
            private set

        fun generateSecureNonce(): String {
            val bytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    private val TAG = "JarvisLiveBridge"
    private val mouseExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val telemetryRunning = java.util.concurrent.atomic.AtomicBoolean(false)
    var desktopIp: String? = null
        private set
    var desktopPort: Int = 53391
        private set
    var isConnected: Boolean = false
        private set

    private var multicastLock: WifiManager.MulticastLock? = null

    private fun acquireMulticastLock() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wm?.createMulticastLock("JarvisBridgeMulticast")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire MulticastLock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }

    fun startDiscovery(onConnected: (desktopName: String, ip: String) -> Unit) {
        synchronized(JarvisLiveBridgeClient::class.java) {
            if (isDiscoveringGlobally) return
            isDiscoveringGlobally = true
        }

        activeClient = this
        FileBeamReceiver.getOrInitAuthToken(context)
        acquireMulticastLock()

        // 1. UDP Listener & Mobile Beacon Broadcaster
        thread(isDaemon = true, name = "Jarvis-UDP-Discovery") {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress("0.0.0.0", 53390))
                    broadcast = true
                    soTimeout = 4000
                }
                val buffer = ByteArray(2048)

                Log.i(TAG, "Listening for JARVIS Desktop discovery beacons on UDP 53390...")

                // Send an immediate announcement beacon from mobile to desktop
                broadcastMobileBeacon(socket)

                while (!isConnected) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        // Re-announce so the desktop can see a phone that joined
                        // after the desktop's own beacon cycle.
                        broadcastMobileBeacon(socket)
                        continue
                    }
                    val msg = String(packet.data, 0, packet.length)

                    try {
                        val json = JSONObject(msg)
                        if (json.optString("type") == "jarvis_beacon") {
                            val host = packet.address.hostAddress ?: ""
                            val port = json.optInt("port", 53391)
                            val name = json.optString("name", "JARVIS Desktop")
                            // Only link-local/private peers on the same LAN may claim to be
                            // the desktop; never send the auth token to a public address.
                            if (port !in 1..65535 || !isPrivateIpv4(host)) {
                                Log.w(TAG, "Ignoring discovery beacon from non-LAN address $host:$port")
                                continue
                            }
                            // Pin the desktop's TLS certificate fingerprint before any
                            // connection is made; subsequent traffic refuses any other cert.
                            // An existing pin is never overwritten by a beacon.
                            val fingerprint = json.optString("tlsFingerprint", "")
                            if (fingerprint.isNotEmpty() &&
                                !BridgeTls.pinDesktopFingerprint(context, fingerprint)
                            ) {
                                Log.w(TAG, "Ignoring beacon whose TLS fingerprint does not match the pinned desktop")
                                continue
                            }
                            onDiscovered(name, host, port, onConnected)
                            break
                        }
                    } catch (_: Exception) {
                        // ignore malformed packet
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "UDP Beacon listening error: ${e.message}")
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
                releaseMulticastLock()
                synchronized(JarvisLiveBridgeClient::class.java) {
                    isDiscoveringGlobally = false
                }
            }
        }

        // 2. Fallback Active Subnet Scan (for networks with AP/broadcast isolation)
        thread(isDaemon = true, name = "Jarvis-Subnet-Scanner") {
            while (!isConnected) {
                try {
                    Thread.sleep(2500)
                    if (isConnected) break

                    val localIp = getLocalIpv4Address()
                    if (localIp == null) {
                        // Discovery often starts before Wi-Fi finishes associating.
                        Log.w(TAG, "No private LAN address yet; retrying discovery")
                        continue
                    }
                    val prefix = localIp.substringBeforeLast(".") + "."

                    for (i in 1..254) {
                        if (isConnected) break
                        val target = "$prefix$i"
                        if (target == localIp) continue
                        thread(isDaemon = true) {
                            try {
                                // Discovery probe only: the status endpoint is unauthenticated
                                // and carries no secrets, so it tolerates the yet-unpinned
                                // certificate. The fingerprint it returns is pinned below.
                                val url = URL("https://$target:53391/api/status")
                                val conn = url.openConnection() as HttpsURLConnection
                                BridgeTls.permissiveClientSocketFactory()?.let {
                                    conn.sslSocketFactory = it
                                }
                                conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
                                conn.connectTimeout = 350
                                conn.readTimeout = 350
                                conn.requestMethod = "GET"
                                if (conn.responseCode in 200..299) {
                                    val resp = conn.inputStream.bufferedReader().readText()
                                    conn.disconnect()
                                    if (resp.contains("JARVIS") || resp.contains("LiveBridge")) {
                                        val fingerprint = try {
                                            JSONObject(resp).optString("tlsFingerprint", "")
                                        } catch (_: Exception) {
                                            ""
                                        }
                                        if (fingerprint.isNotEmpty()) {
                                            if (!BridgeTls.pinDesktopFingerprint(context, fingerprint)) {
                                                Log.w(TAG, "Subnet probe at $target does not match the pinned desktop certificate")
                                                return@thread
                                            }
                                        }
                                        onDiscovered("JARVIS Desktop", target, 53391, onConnected)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        Thread.sleep(8)
                    }
                    if (!isConnected) {
                        Log.w(TAG, "No desktop answered on ${prefix}0/24; retrying")
                        Thread.sleep(5000)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Subnet scan error: ${e.message}")
                    Thread.sleep(3000)
                }
            }
        }
    }

    private fun onDiscovered(
        name: String,
        host: String,
        port: Int,
        onConnected: (String, String) -> Unit
    ) {
        if (isConnected) return
        desktopIp = host
        desktopPort = port
        isConnected = true
        // The instance that actually found the desktop becomes the one the UI
        // pairs through; otherwise the dialog can talk to a client with no IP.
        activeClient = this
        releaseMulticastLock()
        Log.i(TAG, "Successfully connected to JARVIS Desktop at $desktopIp:$desktopPort ($name)")
        onConnected(name, host)
        startTelemetryReporting()
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split(".").map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return when {
            parts[0] == 10 -> true
            parts[0] == 172 && parts[1] in 16..31 -> true
            parts[0] == 192 && parts[1] == 168 -> true
            parts[0] == 169 && parts[1] == 254 -> true
            else -> false
        }
    }

    private fun broadcastMobileBeacon(socket: DatagramSocket) {
        try {
            val payload = JSONObject().apply {
                put("type", "jarvis_mobile_beacon")
                put("deviceId", Build.MODEL)
                put("name", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("port", 53391)
            }.toString().toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), 53390)
            socket.send(packet)
        } catch (_: Exception) {}
    }

    private fun getLocalIpv4Address(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            var fallback: String? = null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (addr !is Inet4Address || addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                    val ip = addr.hostAddress ?: continue
                    // Exclude carrier CGNAT (100.64.0.0/10) and other non-LAN
                    // addresses: scanning the cellular subnet never finds the PC.
                    if (!isPrivateIpv4(ip)) continue
                    if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("eth")) {
                        return ip
                    }
                    if (fallback == null) fallback = ip
                }
            }
            return fallback
        } catch (_: Exception) {}
        return null
    }

    private fun startTelemetryReporting() {
        if (!telemetryRunning.compareAndSet(false, true)) return
        thread(isDaemon = true, name = "Jarvis-Telemetry-Loop") {
            try {
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

                    val code = postJson("https://$desktopIp:$desktopPort/api/telemetry", payload)
                    if (code == 401 || code == 403) {
                        Log.w(TAG, "Desktop rejected the bridge token; dropping unverified peer ${desktopIp}")
                        isConnected = false
                        desktopIp = null
                        return@thread
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Telemetry post failed: ${e.message}")
                }
                Thread.sleep(10000)
                }
            } finally {
                telemetryRunning.set(false)
            }
        }
    }

    private fun getWifiSsid(wm: WifiManager): String {
        return try {
            val ssid = wm.connectionInfo?.ssid?.replace("\"", "") ?: ""
            if (ssid.isEmpty() || ssid == "<unknown ssid>") "5G WiFi" else ssid
        } catch (_: Exception) {
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
                postJson("https://$desktopIp:$desktopPort/api/clipboard", payload)
                Log.i(TAG, "Successfully synced clipboard text to JARVIS Desktop")
            } catch (e: Exception) {
                Log.w(TAG, "Clipboard sync failed: ${e.message}")
            }
        }
    }

    fun sendMouseInput(action: String, dx: Float = 0f, dy: Float = 0f, button: String = "left") {
        val ip = desktopIp ?: return
        val port = desktopPort
        mouseExecutor.execute {
            try {
                val payload = JSONObject().apply {
                    put("action", action)
                    put("dx", dx)
                    put("dy", dy)
                    put("button", button)
                }.toString().toByteArray(Charsets.UTF_8)

                val conn = openSecureConnection("https://$ip:$port/api/mouse") ?: return@execute
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Content-Length", payload.size.toString())
                conn.setRequestProperty("X-Jarvis-Auth-Token", FileBeamReceiver.currentAuthToken)
                val now = System.currentTimeMillis()
                conn.setRequestProperty("X-Jarvis-Timestamp", now.toString())
                conn.setRequestProperty("X-Jarvis-Nonce", generateSecureNonce())
                conn.doOutput = true
                conn.connectTimeout = 800
                conn.readTimeout = 800
                conn.outputStream.use { it.write(payload) }
                val _code = conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    fun beamFileToDesktop(
        file: java.io.File,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        val ip = desktopIp
        if (ip == null) {
            onComplete(false)
            return
        }
        FileBeamReceiver.beamFileToDesktop(context, file, ip, desktopPort, onProgress, onComplete)
    }

    /**
     * Starts a PIN pairing challenge with the discovered desktop. The desktop shows a
     * 6-digit PIN which the user then enters via [confirmPairing].
     */
    fun requestPairing(onResult: (challengeId: String?) -> Unit) {
        thread(isDaemon = true, name = "Jarvis-Pairing-Request") {
            // The dialog is often opened while discovery is still sweeping; give
            // the beacon or subnet probe time to land instead of failing at once.
            var waited = 0L
            while (desktopIp == null && waited < 8000) {
                Thread.sleep(250)
                waited += 250
            }
            val ip = desktopIp
            if (ip == null) {
                mainHandler.post { onResult(null) }
                return@thread
            }
            var challengeId: String? = null
            try {
                val payload = JSONObject().apply {
                    put("clientDeviceId", Build.MODEL)
                    put("clientDeviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
                }.toString().toByteArray(Charsets.UTF_8)

                val conn = openSecureConnection("https://$ip:$desktopPort/api/pair/request") ?: run {
                    mainHandler.post { onResult(null) }
                    return@thread
                }
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.outputStream.use { it.write(payload) }
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    challengeId = JSONObject(body).optString("challengeId", "").ifBlank { null }
                } else {
                    Log.w(TAG, "Pairing request rejected with HTTP $code")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Pairing request failed: ${e.message}")
            }
            mainHandler.post { onResult(challengeId) }
        }
    }

    /**
     * Adopts the endpoint and certificate from a scanned pairing QR code, then
     * lets [confirmPairing] complete the handshake without discovery.
     */
    fun adoptDesktopEndpoint(ip: String, port: Int, fingerprint: String) {
        desktopIp = ip
        desktopPort = port
        BridgeTls.storeDesktopFingerprint(context, fingerprint)
        isConnected = true
    }

    /**
     * Sends a chat request to the desktop AI core over the pinned bridge.
     * Provider keys stay on the PC; the phone only receives the reply.
     */
    fun requestAi(
        messages: List<Pair<String, String>>,
        onResult: (reply: String?, error: String?) -> Unit
    ) {
        val ip = desktopIp
        if (ip == null) {
            onResult(null, "No desktop connected")
            return
        }
        thread(isDaemon = true, name = "Jarvis-AI-Request") {
            var reply: String? = null
            var error: String? = null
            try {
                val conn = openSecureConnection("https://$ip:$desktopPort/api/ai/chat")
                if (conn == null) {
                    mainHandler.post { onResult(null, "Pair with the desktop first") }
                    return@thread
                }
                val payload = JSONObject().apply {
                    put("messages", org.json.JSONArray().apply {
                        messages.forEach { (role, content) ->
                            put(JSONObject().apply {
                                put("role", role)
                                put("content", content)
                            })
                        }
                    })
                }.toString().toByteArray(Charsets.UTF_8)

                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Jarvis-Auth-Token", FileBeamReceiver.currentAuthToken)
                conn.setRequestProperty("X-Jarvis-Timestamp", System.currentTimeMillis().toString())
                conn.setRequestProperty("X-Jarvis-Nonce", generateSecureNonce())
                conn.doOutput = true
                conn.connectTimeout = 4000
                conn.readTimeout = 60_000
                conn.outputStream.use { it.write(payload) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()

                if (code in 200..299) {
                    reply = JSONObject(body).optString("content", "").ifBlank { null }
                    if (reply == null) error = "The assistant returned an empty reply."
                } else {
                    error = try {
                        JSONObject(body).optString("error", "AI request failed")
                    } catch (_: Exception) {
                        "AI request failed (HTTP $code)"
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "AI request failed"
            }
            mainHandler.post { onResult(reply, error) }
        }
    }

    /**
     * Drops the pinned desktop identity and the paired token, then restarts
     * discovery so a rotated desktop certificate can be re-pinned.
     */
    fun forgetDesktop() {
        isConnected = false
        desktopIp = null
        FileBeamReceiver.clearPairedToken(context)
        BridgeTls.clearDesktopPin(context)
        synchronized(JarvisLiveBridgeClient::class.java) {
            isDiscoveringGlobally = false
        }
        startDiscovery { _, _ -> }
    }

    fun confirmPairing(challengeId: String, pin: String, onResult: (Boolean) -> Unit) {
        val ip = desktopIp
        if (ip == null) {
            onResult(false)
            return
        }
        thread(isDaemon = true, name = "Jarvis-Pairing-Confirm") {
            var paired = false
            try {
                val payload = JSONObject().apply {
                    put("challengeId", challengeId)
                    put("pin", pin)
                }.toString().toByteArray(Charsets.UTF_8)

                val conn = openSecureConnection("https://$ip:$desktopPort/api/pair/confirm") ?: run {
                    mainHandler.post { onResult(false) }
                    return@thread
                }
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.outputStream.use { it.write(payload) }
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val pairResponse = JSONObject(body)
                    val token = pairResponse.optString("authToken", "")
                    if (token.isNotBlank()) {
                        FileBeamReceiver.storePairedToken(context, token)
                        BridgeTls.storeDesktopFingerprint(
                            context,
                            pairResponse.optString("serverTlsFingerprint", "")
                        )
                        val certPem = pairResponse.optString("clientCertPem", "")
                        val keyPem = pairResponse.optString("clientKeyPem", "")
                        if (certPem.isNotBlank() && keyPem.isNotBlank() &&
                            BridgeTls.storeClientIdentity(context, certPem, keyPem)
                        ) {
                            // Switch the companion server from bootstrap HTTP to pinned TLS.
                            FileBeamReceiver.restartServerWithTls()
                        }
                        paired = true
                        startTelemetryReporting()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Pairing confirmation failed: ${e.message}")
            }
            mainHandler.post { onResult(paired) }
        }
    }

    private fun openSecureConnection(urlString: String): HttpsURLConnection? {
        val factory = BridgeTls.pinnedClientSocketFactory(context) ?: return null
        val conn = URL(urlString).openConnection() as? HttpsURLConnection ?: return null
        conn.sslSocketFactory = factory
        conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        return conn
    }

    private fun postJson(urlString: String, json: JSONObject): Int {
        val conn = openSecureConnection(urlString) ?: return -1
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Jarvis-Auth-Token", FileBeamReceiver.currentAuthToken)
        val now = System.currentTimeMillis()
        conn.setRequestProperty("X-Jarvis-Timestamp", now.toString())
        conn.setRequestProperty("X-Jarvis-Nonce", generateSecureNonce())
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
        return code
    }
}
