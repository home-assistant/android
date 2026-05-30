package io.homeassistant.companion.android.authenticator

import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import timber.log.Timber

class Authenticator(fragmentActivity: FragmentActivity, callback: (AuthenticationResult) -> Unit) {
    companion object {
        enum class AuthenticationResult {
            ERROR,
            SUCCESS,
            CANCELED,
        }

        const val AUTH_TYPES = Authenticators.DEVICE_CREDENTIAL or Authenticators.BIOMETRIC_WEAK
    }

    private val executor = ContextCompat.getMainExecutor(fragmentActivity)
    private val biometricPrompt = BiometricPrompt(
        fragmentActivity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Timber.d("Unlock onAuthenticationError -> $errorCode :: $errString")
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    callback(AuthenticationResult.CANCELED)
                } else {
                    callback(AuthenticationResult.ERROR)
                }
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback(AuthenticationResult.ERROR)
            }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                callback(AuthenticationResult.SUCCESS)
            }
        },
    )

    fun authenticate(title: String) {
        val promptDialog = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setConfirmationRequired(false)
            .setAllowedAuthenticators(AUTH_TYPES)
            .build()

        biometricPrompt.authenticate(promptDialog)
    }
}
