package io.homeassistant.companion.android.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
            val mobileAppIntegrationFragment = MobileAppIntegrationFragment.newInstance()
            mobileAppIntegrationFragment.retainInstance = true
            supportFragmentManager
                .beginTransaction()
                .add(R.id.content, mobileAppIntegrationFragment)
                .commit()
        } else {
            val discoveryFragment = DiscoveryFragment.newInstance()
            discoveryFragment.retainInstance = true
            supportFragmentManager
                .beginTransaction()
                .add(R.id.content, discoveryFragment)
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
        val manualSetupFragment = ManualSetupFragment.newInstance()
        manualSetupFragment.retainInstance = true
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, manualSetupFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onHomeAssistantDiscover() {
        val authenticationFragment = AuthenticationFragment.newInstance()
        authenticationFragment.retainInstance = true
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, authenticationFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onSelectUrl() {
        val authenticationFragment = AuthenticationFragment.newInstance()
        authenticationFragment.retainInstance = true
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, authenticationFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onAuthenticationSuccess() {
        val mobileAppIntegrationFragment = MobileAppIntegrationFragment.newInstance()
        mobileAppIntegrationFragment.retainInstance = true
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, mobileAppIntegrationFragment)
            .commit()
    }

    override fun onIntegrationRegistrationComplete() {
        startWebView()
    }

    private fun startWebView() {
        startActivity(WebViewActivity.newInstance(this))
        finish()
    }
}
