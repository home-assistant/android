package io.homeassistant.companion.android.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.home.views.DEEPLINK_PREFIX_SET_CAMERA_TILE
import io.homeassistant.companion.android.home.views.DEEPLINK_PREFIX_SET_SHORTCUT_TILE
import io.homeassistant.companion.android.home.views.DEEPLINK_PREFIX_SET_TEMPLATE_TILE
import io.homeassistant.companion.android.home.views.LoadHomePage
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeActivity : ComponentActivity(), HomeView {

    @Inject
    lateinit var presenter: HomePresenter

    private val mainViewModel by viewModels<MainViewModel>()

    private var entityUpdateJob: Job? = null

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        mainViewModel.refreshNotificationPermission()
    }

    companion object {
        private const val EXTRA_FROM_ONBOARDING = "from_onboarding"

        fun newInstance(context: Context, fromOnboarding: Boolean = false): Intent {
            return Intent(context, HomeActivity::class.java).apply {
                putExtra(EXTRA_FROM_ONBOARDING, fromOnboarding)
            }
        }

        fun getCameraTileSettingsIntent(
            context: Context,
            tileId: Int,
        ) = Intent(
            Intent.ACTION_VIEW,
            "$DEEPLINK_PREFIX_SET_CAMERA_TILE/$tileId".toUri(),
            context,
            HomeActivity::class.java,
        )

        fun getShortcutsTileSettingsIntent(
            context: Context,
            tileId: Int,
        ) = Intent(
            Intent.ACTION_VIEW,
            "$DEEPLINK_PREFIX_SET_SHORTCUT_TILE/$tileId".toUri(),
            context,
            HomeActivity::class.java,
        )

        fun getTemplateTileSettingsIntent(
            context: Context,
            tileId: Int,
        ) = Intent(
            Intent.ACTION_VIEW,
            "$DEEPLINK_PREFIX_SET_TEMPLATE_TILE/$tileId".toUri(),
            context,
            HomeActivity::class.java,
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
        if (
            intent.getBooleanExtra(EXTRA_FROM_ONBOARDING, false) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(this@HomeActivity).areNotificationsEnabled()
        ) {
            permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            intent.removeExtra(EXTRA_FROM_ONBOARDING)
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
