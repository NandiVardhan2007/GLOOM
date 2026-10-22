package com.gloom.island.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * Uses the platform biometric prompt so the app can keep ComponentActivity as
 * its Activity base (FragmentActivity rejects Activity Result request codes
 * above 16 bits, which breaks the QR scanner and permission launchers).
 */
object BiometricAuth {

    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
            keyguard?.isDeviceSecure == true
    }

    fun prompt(
        activity: Activity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallback: (() -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError("Fingerprint unlock requires Android 9 or newer")
            return
        }
        val executor = activity.mainExecutor
        val builder = BiometricPrompt.Builder(activity)
            .setTitle("Unlock GLOOM")
            .setSubtitle("Confirm with your fingerprint")
            .setNegativeButton("Use pattern", executor) { _, _ ->
                onFallback?.invoke() ?: onError("Cancelled")
            }
        val prompt = builder.build()
        prompt.authenticate(
            CancellationSignal(),
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onError(errString?.toString() ?: "Authentication failed")
                }
            }
        )
    }
}
