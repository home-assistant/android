package io.homeassistant.companion.android.launch

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject

@AndroidEntryPoint
class LaunchActivity : BaseActivity(), LaunchView {

    @Inject
    lateinit var presenter: LaunchPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenter.onViewReady()
    }

    override fun displayWebview() {
        presenter.setSessionExpireMillis(0)

        startActivity(WebViewActivity.newInstance(this, intent.data?.path))
        finish()
        overridePendingTransition(0, 0) // Disable activity start/stop animation
    }

    override fun displayOnBoarding(sessionConnected: Boolean) {
        val intent = OnboardingActivity.newInstance(this)
        intent.putExtra(OnboardingActivity.SESSION_CONNECTED, sessionConnected)

        startActivity(intent)
        finish()
        overridePendingTransition(0, 0) // Disable activity start/stop animation
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
