package io.homeassistant.companion.android.launch

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.api.Session
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.webview.WebViewActivity


class LaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Session.getInstance().url.isNullOrBlank()) {
            startActivity(OnboardingActivity.newInstance(this))
        } else {
            startActivity(WebViewActivity.newInstance(this))
        }
        finish()
    }
}