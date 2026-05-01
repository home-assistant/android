package io.homeassistant.companion.android.launch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult.ActionPerformed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.homeassistant.companion.android.WIPFeature
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import io.homeassistant.companion.android.util.ChangeLog
import io.homeassistant.companion.android.util.CheckLocationDisabledUseCase
import io.homeassistant.companion.android.util.PLAY_SERVICES_FLAVOR_DOC_URL
import io.homeassistant.companion.android.util.PlayServicesAvailability
import io.homeassistant.companion.android.util.compose.HAApp
import io.homeassistant.companion.android.util.compose.navigateToUri
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import io.homeassistant.companion.android.websocket.WebsocketManager
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

private const val DEEP_LINK_KEY = "deep_link_key"

/**
 * Main entry point of the application, responsible for holding the whole navigation graph
 * and triggering lifecycle-based refresh of background work.
 *
 * It also handles the splash screen display based on a condition exposed by the [LaunchViewModel].
 *
 * On resume, refreshes the scheduling of periodic sensor collection via [SensorWorker]
 * and the background WebSocket work via [WebsocketManager].
 * These jobs are managed outside the Activity and may continue beyond this lifecycle.
 * On pause, triggers an immediate sensor update via [SensorReceiver] so the server
 * has fresh data before the app goes to the background.
 */
@AndroidEntryPoint
class LaunchActivity : AppCompatActivity() {
    @Inject
    internal lateinit var playServicesAvailability: PlayServicesAvailability

    @Inject
    internal lateinit var checkLocationDisabled: CheckLocationDisabledUseCase

    @Inject
    internal lateinit var changeLog: ChangeLog

    /**
     * Represents deep link actions that can be passed to [LaunchActivity] to navigate to specific destinations.
     */
    @Parcelize
    sealed interface DeepLink : Parcelable {
        /**
         * Opens the onboarding flow for a new Home Assistant server.
         * @property urlToOnboard Optional server URL to connect to directly. If null, shows server discovery.
         * @property hideExistingServers When true, hides already registered servers from discovery results.
         * @property skipWelcome When true, skips the welcome screen and navigates directly to server discovery,
         *  or to the connection screen if [urlToOnboard] is provided.
         */
        data class OpenOnboarding(
            val urlToOnboard: String?,
            val hideExistingServers: Boolean,
            val skipWelcome: Boolean,
        ) : DeepLink

        /**
         * Navigates to a specific path within the webview.
         * @property path The path to navigate to within the Home Assistant interface.
         * @property serverId The ID of the server to use for navigation.
         */
        data class NavigateTo(val path: String?, val serverId: Int) : DeepLink

        /**
         * Opens the Wear OS device onboarding flow.
         * @property wearName The name of the Wear device being onboarded.
         * @property urlToOnboard Optional server URL to connect to directly. If null, shows server discovery.
         */
        data class OpenWearOnboarding(val wearName: String, val urlToOnboard: String?) : DeepLink
    }

    companion object {
        fun newInstance(context: Context, deepLink: DeepLink? = null): Intent {
            return Intent(context, LaunchActivity::class.java).apply {
                if (deepLink != null) {
                    putExtra(DEEP_LINK_KEY, deepLink)
                }
            }
        }
    }

    private val viewModel: LaunchViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<LaunchViewModelFactory> {
                it.create(IntentCompat.getParcelableExtra(intent, DEEP_LINK_KEY, DeepLink::class.java))
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()

        splashScreen.setKeepOnScreenCondition {
            viewModel.shouldShowSplashScreen()
        }

        enableEdgeToEdgeCompat()

        setContent {
            HATheme {
                val navController = rememberNavController()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val isFullScreen by viewModel.isFullScreen.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                FullscreenEffect(isFullScreen = isFullScreen)

                MissingPlayServicesNotice(
                    isMissingRequiredPlayServices = playServicesAvailability.isMissingRequiredPlayServices(),
                    snackbarHostState = snackbarHostState,
                    navController = navController,
                )

                HAApp(
                    navController = navController,
                    startDestination = (uiState as? LaunchUiState.Ready)?.startDestination,
                    snackbarHostState = snackbarHostState,
                )

                when (uiState) {
                    LaunchUiState.NetworkUnavailable -> NetworkUnavailableDialog(onBackClick = ::finish)
                    LaunchUiState.WearUnsupported -> WearUnsupportedDialog(onBackClick = ::finish)
                    LaunchUiState.Loading, is LaunchUiState.Ready -> Unit
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (WIPFeature.USE_FRONTEND_V2) {
            SensorWorker.start(this)
            lifecycleScope.launch {
                WebsocketManager.start(this@LaunchActivity)
                checkLocationDisabled()
                changeLog.showChangeLog(this@LaunchActivity, forceShow = false)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing && WIPFeature.USE_FRONTEND_V2) SensorReceiver.updateAllSensors(this)
    }
}

@Composable
private fun FullscreenEffect(isFullScreen: Boolean) {
    val view = LocalView.current
    val window = LocalActivity.current?.window ?: return
    LaunchedEffect(isFullScreen) {
        val controller = WindowInsetsControllerCompat(window, view)
        if (isFullScreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(systemBars())
        } else {
            controller.show(systemBars())
        }
    }
}

@Composable
private fun MissingPlayServicesNotice(
    isMissingRequiredPlayServices: Boolean,
    snackbarHostState: SnackbarHostState,
    navController: NavController,
) {
    if (isMissingRequiredPlayServices) {
        val message = stringResource(commonR.string.play_services_unavailable_full_flavor)
        val learnMore = stringResource(commonR.string.learn_more)
        LaunchedEffect(message) {
            if (snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Long,
                    actionLabel = learnMore,
                ) == ActionPerformed
            ) {
                navController.navigateToUri(
                    uri = PLAY_SERVICES_FLAVOR_DOC_URL,
                    onShowSnackbar = { snackbarMessage, action ->
                        snackbarHostState.showSnackbar(snackbarMessage, action) == ActionPerformed
                    },
                )
            }
        }
    }
}
