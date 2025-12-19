package io.homeassistant.companion.android.onboarding

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import io.homeassistant.companion.android.launch.intentLaunchWearOnboarding

class WearOnboardApp : ActivityResultContract<WearOnboardApp.Input, WearOnboardApp.Output?>() {
    data class Input(val url: String? = null, val defaultDeviceName: String = Build.MODEL)

    data class Output(
        val url: String,
        val authCode: String,
        val deviceName: String,
        val tlsClientCertificateUri: String?,
        val tlsClientCertificatePassword: String?,
    ) {
        fun toIntent(): Intent {
            return Intent().apply {
                putExtra(EXTRA_OUTPUT_URL, url)
                putExtra(EXTRA_OUTPUT_AUTH_CODE, authCode)
                putExtra(EXTRA_OUTPUT_DEVICE_NAME, deviceName)
                putExtra(EXTRA_OUTPUT_TLS_CLIENT_CERTIFICATE_URI, tlsClientCertificateUri)
                putExtra(EXTRA_OUTPUT_TLS_CLIENT_CERTIFICATE_PASSWORD, tlsClientCertificatePassword)
            }
        }

        companion object {
            private const val EXTRA_OUTPUT_URL = "URL"
            private const val EXTRA_OUTPUT_AUTH_CODE = "AuthCode"
            private const val EXTRA_OUTPUT_DEVICE_NAME = "DeviceName"
            private const val EXTRA_OUTPUT_TLS_CLIENT_CERTIFICATE_URI = "TLSClientCertificateUri"
            private const val EXTRA_OUTPUT_TLS_CLIENT_CERTIFICATE_PASSWORD = "TLSClientCertificatePassword"

            fun fromIntent(intent: Intent): Output {
                return Output(
                    url = intent.getStringExtra(EXTRA_OUTPUT_URL).toString(),
                    authCode = intent.getStringExtra(EXTRA_OUTPUT_AUTH_CODE).toString(),
                    deviceName = intent.getStringExtra(EXTRA_OUTPUT_DEVICE_NAME).toString(),
                    tlsClientCertificateUri = intent.getStringExtra(EXTRA_OUTPUT_TLS_CLIENT_CERTIFICATE_URI),
                    tlsClientCertificatePassword = intent.getStringExtra(EXTRA_OUTPUT_TLS_CLIENT_CERTIFICATE_PASSWORD),
                )
            }
        }
    }

    override fun createIntent(context: Context, input: Input): Intent {
        return context.intentLaunchWearOnboarding(input.defaultDeviceName, input.url)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Output? {
        if (intent == null) {
            return null
        }

        return Output.fromIntent(intent)
    }
}
