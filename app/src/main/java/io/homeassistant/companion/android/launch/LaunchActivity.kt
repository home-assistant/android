package io.homeassistant.companion.android.launch

import android.content.Context
import android.os.Bundle
import android.view.MenuInflater
import androidx.appcompat.app.AppCompatActivity
import com.lokalise.sdk.LokaliseContextWrapper
import com.lokalise.sdk.menu_inflater.LokaliseMenuInflater
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject

class LaunchActivity : AppCompatActivity(), LaunchView {

    @Inject lateinit var presenter: LaunchPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        presenter.onViewReady()
    }

    override fun displayWebview() {
        presenter.setSessionExpireMillis(0)
        startActivity(WebViewActivity.newInstance(this))
        finish()
    }

    override fun displayOnBoarding(sessionConnected: Boolean) {
        val intent = OnboardingActivity.newInstance(this)
        intent.putExtra(OnboardingActivity.SESSION_CONNECTED, sessionConnected)

        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LokaliseContextWrapper.wrap(newBase))
    }

    override fun getMenuInflater(): MenuInflater {
        return LokaliseMenuInflater(this)
    }
}
