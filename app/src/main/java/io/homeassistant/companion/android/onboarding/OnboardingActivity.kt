package io.homeassistant.companion.android.onboarding

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.welcome.WelcomeFragment

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val AUTHENTICATION_FRAGMENT = "authentication_fragment"
        private const val TAG = "OnboardingActivity"
        private const val EXTRA_DEFAULT_DEVICE_NAME = "extra_default_device_name"
        private const val EXTRA_LOCATION_TRACKING_POSSIBLE = "location_tracking_possible"

        fun newInstance(context: Context, defaultDeviceName: String = Build.MODEL, locationTrackingPossible: Boolean = BuildConfig.FLAVOR == "full"): Intent {
            return Intent(context, OnboardingActivity::class.java).apply {
                putExtra(EXTRA_DEFAULT_DEVICE_NAME, defaultDeviceName)
                putExtra(EXTRA_LOCATION_TRACKING_POSSIBLE, locationTrackingPossible)
            }
        }
    }

    private val viewModel by viewModels<OnboardingViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        viewModel.deviceName.value = intent.getStringExtra(EXTRA_DEFAULT_DEVICE_NAME) ?: Build.MODEL
        viewModel.locationTrackingPossible.value = intent.getBooleanExtra(EXTRA_LOCATION_TRACKING_POSSIBLE, false)

        supportFragmentManager
            .beginTransaction()
            .add(R.id.content, WelcomeFragment::class.java, null)
            .commit()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        // Temporary workaround to sideload on Android TV and use a remote for basic navigation in WebView
        val fragmentManager = supportFragmentManager.findFragmentByTag(AUTHENTICATION_FRAGMENT)
        if (event?.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN &&
            fragmentManager != null && fragmentManager.isVisible
        ) {
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
            return true
        }

        return super.dispatchKeyEvent(event)
    }
}
