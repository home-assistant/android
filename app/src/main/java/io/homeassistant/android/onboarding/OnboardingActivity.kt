package io.homeassistant.android.onboarding

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.android.R


class OnboardingActivity : AppCompatActivity(), DiscoveryListener, ManualSetupListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .add(R.id.content, DiscoveryFragment.newInstance())
                .commit()
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

    override fun onSelectUrl(url: String) {
        throw NotImplementedError()
    }

}