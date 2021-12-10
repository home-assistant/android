package io.homeassistant.companion.android.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.welcome.WelcomeFragment

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val AUTHENTICATION_FRAGMENT = "authentication_fragment"
        private const val TAG = "OnboardingActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, OnboardingActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

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
