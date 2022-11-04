package io.homeassistant.companion.android.onboarding

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationFragment
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryFragment
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment
import io.homeassistant.companion.android.onboarding.welcome.WelcomeFragment

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val AUTHENTICATION_FRAGMENT = "authentication_fragment"
    }

    private val viewModel by viewModels<OnboardingViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val input = OnboardApp.parseInput(intent)
        viewModel.deviceName.value = input.defaultDeviceName
        viewModel.locationTrackingPossible.value = input.locationTrackingPossible

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(R.id.content, WelcomeFragment::class.java, null)
            }
            if (input.url != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    supportFragmentManager.commit {
                        replace(R.id.content, DiscoveryFragment::class.java, null)
                        addToBackStack(null)
                    }
                }
                if (input.url.isNotBlank() || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    viewModel.onManualUrlUpdated(input.url)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !viewModel.manualContinueEnabled) {
                        supportFragmentManager.commit {
                            replace(R.id.content, ManualSetupFragment::class.java, null)
                            addToBackStack(null)
                        }
                    }
                    if (viewModel.manualContinueEnabled) {
                        supportFragmentManager.commit {
                            replace(R.id.content, AuthenticationFragment::class.java, null)
                            addToBackStack(null)
                        }
                    }
                }
            }
        }
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
