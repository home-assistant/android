package io.homeassistant.companion.android.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.home.views.LoadHomePage
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : ComponentActivity(), HomeView {

    @Inject
    lateinit var presenter: HomePresenter

    private val mainViewModel by viewModels<MainViewModel>()

    private var entityUpdateJob: Job? = null

    companion object {
        private const val TAG = "HomeActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, HomeActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Get rid of me!
        presenter.init(this)

        presenter.onViewReady()
        setContent {
            LoadHomePage(mainViewModel)
        }

        mainViewModel.init(presenter)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    mainViewModel.supportedEntities.collect {
                        if (entityUpdateJob?.isActive == true) entityUpdateJob?.cancel()
                        entityUpdateJob = launch { mainViewModel.entityUpdates() }
                    }
                }
                launch { mainViewModel.entityRegistryUpdates() }
                if (!mainViewModel.isFavoritesOnly) {
                    launch { mainViewModel.areaUpdates() }
                    launch { mainViewModel.deviceUpdates() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SensorWorker.start(this)

        mainViewModel.initAllSensors()

        lifecycleScope.launch {
            if (mainViewModel.loadingState.value == MainViewModel.LoadingState.READY)
                mainViewModel.updateUI()
        }
    }

    override fun onPause() {
        super.onPause()
        SensorReceiver.updateAllSensors(this)
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
