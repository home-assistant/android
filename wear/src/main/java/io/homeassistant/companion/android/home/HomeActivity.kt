package io.homeassistant.companion.android.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.home.views.DEEPLINK_PREFIX_SET_CAMERA_TILE
import io.homeassistant.companion.android.home.views.DEEPLINK_PREFIX_SET_SHORTCUT_TILE
import io.homeassistant.companion.android.home.views.LoadHomePage
import io.homeassistant.companion.android.onboarding.OnboardingActivity
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

        fun getCameraTileSettingsIntent(
            context: Context,
            tileId: Int
        ) = Intent(
            Intent.ACTION_VIEW,
            "$DEEPLINK_PREFIX_SET_CAMERA_TILE/$tileId".toUri(),
            context,
            HomeActivity::class.java
        )

        fun getShortcutsTileSettingsIntent(
            context: Context,
            tileId: Int
        ) = Intent(
            Intent.ACTION_VIEW,
            "$DEEPLINK_PREFIX_SET_SHORTCUT_TILE/$tileId".toUri(),
            context,
            HomeActivity::class.java
        )
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
            if (mainViewModel.loadingState.value == MainViewModel.LoadingState.READY) {
                mainViewModel.updateUI()
            }
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
}
