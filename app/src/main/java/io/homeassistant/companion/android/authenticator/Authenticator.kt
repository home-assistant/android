package io.homeassistant.companion.android.authenticator

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.homeassistant.companion.android.R

class Authenticator(context: Context, fragmentActivity: FragmentActivity, callback: (Int) -> Unit) {
    val CANCELED = 2
    val SUCCESS = 1
    val ERROR = 0

    val executor = ContextCompat.getMainExecutor(context)
    val biometricPrompt = BiometricPrompt(fragmentActivity, executor,
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
        })
    val promptDialog = BiometricPrompt.PromptInfo.Builder()
        .setTitle(fragmentActivity.resources.getString(R.string.biometric_title))
        .setSubtitle(fragmentActivity.resources.getString(R.string.biometric_message))
        .setDeviceCredentialAllowed(true)
        .build()

    fun authenticate() {
        biometricPrompt.authenticate(promptDialog)
    }
}
