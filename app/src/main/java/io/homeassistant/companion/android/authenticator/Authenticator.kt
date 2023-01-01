package io.homeassistant.companion.android.authenticator

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class Authenticator(context: Context, fragmentActivity: FragmentActivity, callback: (Int) -> Unit) {
    companion object {
        const val CANCELED = 2
        const val SUCCESS = 1
        const val ERROR = 0
    }

    private val executor = ContextCompat.getMainExecutor(context)
    private val biometricPrompt = BiometricPrompt(
        fragmentActivity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.d("Unlock", "onAuthenticationError -> $errorCode :: $errString")
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED)
                    callback(CANCELED)
                else
                    callback(ERROR)
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback(ERROR)
            }
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                super.onAuthenticationSucceeded(result)
                callback(SUCCESS)
            }
        }
    )

    fun authenticate(title: String) {
        val promptDialog = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setConfirmationRequired(false)
            .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptDialog)
    }
}
