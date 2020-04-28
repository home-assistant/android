package io.homeassistant.companion.android.shortcuts

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

class ShortcutsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ShortcutsActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, ShortcutsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcuts)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager
            .beginTransaction()
            .add(R.id.content, ShortcutsFragment.newInstance())
            .commit()
    }

}
