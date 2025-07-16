package io.homeassistant.companion.android.authenticator

import android.content.Context
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import timber.log.Timber

class Authenticator(context: Context, fragmentActivity: FragmentActivity, callback: (Int) -> Unit) {
    companion object {
        const val CANCELED = 2
        const val SUCCESS = 1
        const val ERROR = 0

        const val AUTH_TYPES = Authenticators.DEVICE_CREDENTIAL or Authenticators.BIOMETRIC_WEAK
    }

    private val executor = ContextCompat.getMainExecutor(context)
    private val biometricPrompt = BiometricPrompt(
        fragmentActivity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Timber.d("Unlock onAuthenticationError -> $errorCode :: $errString")
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    callback(CANCELED)
                } else {
                    callback(ERROR)
                }
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback(ERROR)
            }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                callback(SUCCESS)
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
