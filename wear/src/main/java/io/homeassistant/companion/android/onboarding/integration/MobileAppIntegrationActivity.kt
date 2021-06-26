package io.homeassistant.companion.android.onboarding.integration

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.OnboardingActivity

class MobileAppIntegrationActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MobileAppIntegrationActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, MobileAppIntegrationActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_integration)
    }
}