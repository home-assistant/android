package io.homeassistant.companion.android.launch

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.webview.WebViewActivity


class LaunchActivity : AppCompatActivity(), LaunchView {

    lateinit var presenter: LaunchPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenter.onViewReady()
    }

    override fun displayWebview() {
        startActivity(WebViewActivity.newInstance(this))
        finish()
    }

    override fun displayOnBoarding() {
        startActivity(OnboardingActivity.newInstance(this))
        finish()
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}