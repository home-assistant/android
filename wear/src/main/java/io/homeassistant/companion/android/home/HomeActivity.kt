package io.homeassistant.companion.android.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.home.views.LoadHomePage
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : ComponentActivity(), HomeView {

    @Inject
    lateinit var presenter: HomePresenter

    private val mainViewModel by viewModels<MainViewModel>()

    companion object {
        private const val TAG = "HomeActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, HomeActivity::class.java)
        }
    }

    @ExperimentalComposeUiApi
    @ExperimentalAnimationApi
    @ExperimentalWearMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Get rid of me!
        presenter.init(this)

        presenter.onViewReady()
        setContent {
            LoadHomePage(mainViewModel)
        }

        mainViewModel.init(presenter)
    }

    override fun onResume() {
        super.onResume()
        SensorWorker.start(this)

        initAllSensors()
    }

    private fun initAllSensors() {
        for (manager in SensorReceiver.MANAGERS) {
            for (basicSensor in manager.getAvailableSensors(this)) {
                manager.isEnabled(this, basicSensor.id)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        SensorWorker.start(this)
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    override fun displayOnBoarding() {
        val intent = OnboardingActivity.newInstance(this)
        startActivity(intent)
        finish()
    }

    override fun displayMobileAppIntegration() {
        val intent = MobileAppIntegrationActivity.newInstance(this)
        startActivity(intent)
        finish()
    }
}
