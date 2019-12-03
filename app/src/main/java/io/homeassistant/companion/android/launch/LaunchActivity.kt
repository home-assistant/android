package io.homeassistant.companion.android.launch

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.background.LocationBroadcastReceiver
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject

class LaunchActivity : AppCompatActivity(), LaunchView {

    @Inject
    lateinit var presenter: LaunchPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        val intent = Intent(this, LocationBroadcastReceiver::class.java)
        intent.action = LocationBroadcastReceiver.ACTION_REQUEST_LOCATION_UPDATES

        sendBroadcast(intent)

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
