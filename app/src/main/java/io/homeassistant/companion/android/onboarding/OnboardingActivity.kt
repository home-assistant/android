package io.homeassistant.companion.android.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuInflater
import androidx.appcompat.app.AppCompatActivity
import com.lokalise.sdk.LokaliseContextWrapper
import com.lokalise.sdk.menu_inflater.LokaliseMenuInflater
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationFragment
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationListener
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryFragment
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryListener
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationFragment
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationListener
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment
import io.homeassistant.companion.android.onboarding.manual.ManualSetupListener
import io.homeassistant.companion.android.webview.WebViewActivity

class OnboardingActivity : AppCompatActivity(), DiscoveryListener, ManualSetupListener,
    AuthenticationListener, MobileAppIntegrationListener {

    companion object {
        const val SESSION_CONNECTED = "is_registered"
        private const val TAG = "OnboardingActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, OnboardingActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val sessionConnected = intent.extras?.getBoolean(SESSION_CONNECTED) ?: false

        if (sessionConnected) {
            supportFragmentManager
                .beginTransaction()
                .add(R.id.content, MobileAppIntegrationFragment.newInstance())
                .commit()
        } else {
            supportFragmentManager
                .beginTransaction()
                .add(R.id.content, DiscoveryFragment.newInstance())
                .commit()
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSelectManualSetup() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, ManualSetupFragment.newInstance())
            .commit()
    }

    override fun onHomeAssistantDiscover() {
        throw NotImplementedError()
    }

    override fun onSelectUrl() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, AuthenticationFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

    override fun onAuthenticationSuccess() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, MobileAppIntegrationFragment.newInstance())
            .commit()
    }

    override fun onIntegrationRegistrationComplete() {
        startWebView()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LokaliseContextWrapper.wrap(newBase))
    }

    override fun getMenuInflater(): MenuInflater {
        return LokaliseMenuInflater(this)
    }

    private fun startWebView() {
        startActivity(WebViewActivity.newInstance(this))
        finish()
    }
}
