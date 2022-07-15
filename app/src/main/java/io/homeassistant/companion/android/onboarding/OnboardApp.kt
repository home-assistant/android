package io.homeassistant.companion.android.onboarding

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import io.homeassistant.companion.android.BuildConfig

class OnboardApp : ActivityResultContract<OnboardApp.Input, OnboardApp.Output?>() {

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_DEFAULT_DEVICE_NAME = "extra_default_device_name"
        private const val EXTRA_LOCATION_TRACKING_POSSIBLE = "location_tracking_possible"
        private const val EXTRA_NOTIFICATIONS_POSSIBLE = "notifications_possible"

        fun parseInput(intent: Intent): Input = Input(
            url = intent.getStringExtra(EXTRA_URL),
            defaultDeviceName = intent.getStringExtra(EXTRA_DEFAULT_DEVICE_NAME) ?: Build.MODEL,
            locationTrackingPossible = intent.getBooleanExtra(EXTRA_LOCATION_TRACKING_POSSIBLE, false),
            notificationsPossible = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_POSSIBLE, true)
        )
    }

    data class Input(
        val url: String? = null,
        val defaultDeviceName: String = Build.MODEL,
        val locationTrackingPossible: Boolean = BuildConfig.FLAVOR == "full",
        val notificationsPossible: Boolean = true
    )

    data class Output(
        val url: String,
        val authCode: String,
        val deviceName: String,
        val deviceTrackingEnabled: Boolean,
        val notificationsEnabled: Boolean
    ) {
        fun toIntent(): Intent {
            return Intent().apply {
                putExtra("URL", url)
                putExtra("AuthCode", authCode)
                putExtra("DeviceName", deviceName)
                putExtra("LocationTracking", deviceTrackingEnabled)
                putExtra("Notifications", notificationsEnabled)
            }
        }
    }

    override fun createIntent(context: Context, input: Input): Intent {
        return Intent(context, OnboardingActivity::class.java).apply {
            putExtra(EXTRA_URL, input.url)
            putExtra(EXTRA_DEFAULT_DEVICE_NAME, input.defaultDeviceName)
            putExtra(EXTRA_LOCATION_TRACKING_POSSIBLE, input.locationTrackingPossible)
            putExtra(EXTRA_NOTIFICATIONS_POSSIBLE, input.notificationsPossible)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Output? {
        if (intent == null) {
            return null
        }

        val url = intent.getStringExtra("URL").toString()
        val authCode = intent.getStringExtra("AuthCode").toString()
        val deviceName = intent.getStringExtra("DeviceName").toString()
        val deviceTrackingEnabled = intent.getBooleanExtra("LocationTracking", false)
        val notificationsEnabled = intent.getBooleanExtra("Notifications", true)
        return Output(
            url = url,
            authCode = authCode,
            deviceName = deviceName,
            deviceTrackingEnabled = deviceTrackingEnabled,
            notificationsEnabled = notificationsEnabled
        )
    }
}
