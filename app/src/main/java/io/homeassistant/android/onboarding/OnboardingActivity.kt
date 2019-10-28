package io.homeassistant.android.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.android.onboarding.authentication.AuthenticationFragment
import io.homeassistant.android.onboarding.authentication.AuthenticationListener
import io.homeassistant.android.webview.WebviewActivity


class OnboardingActivity : AppCompatActivity(), DiscoveryListener, ManualSetupListener, AuthenticationListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(io.homeassistant.android.R.layout.activity_onboarding)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .add(io.homeassistant.android.R.id.content, DiscoveryFragment.newInstance())
                .commit()
        }
    }

    override fun onSelectManualSetup() {
        supportFragmentManager
            .beginTransaction()
            .replace(io.homeassistant.android.R.id.content, ManualSetupFragment.newInstance())
            .commit()
    }

    override fun onHomeAssistantDiscover() {
        throw NotImplementedError()
    }

    override fun onSelectUrl(url: String) {
        supportFragmentManager
            .beginTransaction()
            .replace(io.homeassistant.android.R.id.content, AuthenticationFragment.newInstance(url))
            .commit()
    }

    override fun onAuthenticationSuccess(url: String) {
        throw NotImplementedError()
    }

}