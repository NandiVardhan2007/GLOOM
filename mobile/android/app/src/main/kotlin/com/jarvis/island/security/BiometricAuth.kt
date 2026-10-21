package com.jarvis.island.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuth {

    private const val ALLOWED = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallback: (() -> Unit)? = null
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock JARVIS")
            .setSubtitle("Confirm with your fingerprint")
            .setAllowedAuthenticators(ALLOWED)
        if (onFallback != null) {
            builder.setNegativeButtonText("Use pattern")
        } else {
            builder.setNegativeButtonText("Cancel")
        }
        val info = builder.build()
        prompt.authenticate(info)
    }
}
