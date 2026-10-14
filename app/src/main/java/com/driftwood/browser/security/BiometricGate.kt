package com.driftwood.browser.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Asks for fingerprint/face, or the phone's PIN/pattern/password as a fallback.
 * Created once in the activity's onCreate, as the biometric library requires.
 */
class BiometricGate(activity: FragmentActivity) {
    private var onSuccess: (() -> Unit)? = null
    private var onFailure: ((String) -> Unit)? = null

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess?.invoke()
                clear()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure?.invoke(errString.toString())
                clear()
            }
        },
    )

    fun authenticate(title: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        this.onSuccess = onSuccess
        this.onFailure = onFailure
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Confirm it's you")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    private fun clear() {
        onSuccess = null
        onFailure = null
    }

    companion object {
        private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        /** False when the phone has no screen lock, in which case passwords stay hidden. */
        fun isAvailable(context: Context): Boolean =
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
