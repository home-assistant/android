package io.homeassistant.companion.android.launch

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.ViewTreeObserver
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult.ActionPerformed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.homeassistant.companion.android.WIPFeature
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.authenticator.Authenticator.Companion.AuthenticationResult
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.sensors.SensorWorker
import io.homeassistant.companion.android.common.util.CheckLocalNetworkPermissionUseCase
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.launch.applock.HazeLockOverlay
import io.homeassistant.companion.android.sensors.SensorReceiver
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
 * Fully qualified class name of the non-exported `<activity-alias>` declared in the manifest.
 *
 * Trusted in-process callers route through this alias to bring up the dashboard over the
 * keyguard. [LaunchActivity.onCreate] only calls [android.app.Activity.setShowWhenLocked] when
 * the inbound intent's component matches it — because the alias is `android:exported="false"`,
 * external apps cannot use it and therefore cannot force the activity to render over the lock
 * screen by themselves.
 */
private const val LOCK_SCREEN_ALIAS_CLASS = "io.homeassistant.companion.android.launch.LaunchOverLockScreen"

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
    internal lateinit var checkLocalNetworkPermission: CheckLocalNetworkPermissionUseCase

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
         * Opens the onboarding flow from an invitation link.
         *
         * @property serverUrl The Home Assistant server URL the invitation wants to connect to.
         */
        data class OpenInvitation(val serverUrl: String) : DeepLink

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
        /**
         * Builds an intent to start [LaunchActivity].
         *
         * @param showWhenLocked when `true`, routes through the non-exported
         *   `LaunchOverLockScreen` activity-alias so the dashboard renders over the keyguard.
         *   Intended for trusted in-process callers (e.g. the device controls panel) — external
         *   apps cannot reach the alias and therefore cannot opt into this behavior.
         */
        fun newInstance(context: Context, deepLink: DeepLink? = null, showWhenLocked: Boolean = false): Intent {
            return Intent().apply {
                component = if (showWhenLocked) {
                    ComponentName(context, LOCK_SCREEN_ALIAS_CLASS)
                } else {
                    ComponentName(context, LaunchActivity::class.java)
                }
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
        // Must run before super.onCreate so the window flag is set before the platform decides
        // whether to draw over the keyguard. Gated on the non-exported [LOCK_SCREEN_ALIAS_CLASS]
        // so external apps reaching the public LAUNCHER intent-filter cannot force this on.
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.O_MR1) &&
            intent.component?.className == LOCK_SCREEN_ALIAS_CLASS
        ) {
            setShowWhenLocked(true)
        }

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
                val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
                val hazeState = rememberHazeState(blurEnabled = isAppLocked)
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
                    onRequestFullscreen = viewModel::onFullscreenRequested,
                    onPipReadinessChanged = viewModel::onPipReadinessChanged,
                    modifier = Modifier.hazeSource(hazeState),
                )

                // We don't apply the overlay on top of the dialogs
                HazeLockOverlay(hazeState)

                when (uiState) {
                    LaunchUiState.NetworkUnavailable -> NetworkUnavailableDialog(onBackClick = ::finish)
                    LaunchUiState.WearUnsupported -> WearUnsupportedDialog(onBackClick = ::finish)
                    LaunchUiState.Loading, is LaunchUiState.Ready -> {
                        AppLockEffect(
                            isAppLocked = isAppLocked,
                            onAuthSucceeded = viewModel::onAuthenticated,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (WIPFeature.USE_FRONTEND_V2) {
            viewModel.refreshAppLockState()
        }
    }

    override fun onResume() {
        super.onResume()
        if (WIPFeature.USE_FRONTEND_V2) {
            SensorWorker.start(this)
            lifecycleScope.launch {
                WebsocketManager.start(this@LaunchActivity)
                checkLocationDisabled()
                checkLocalNetworkPermission()
                changeLog.showChangeLog(this@LaunchActivity, forceShow = false)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing && WIPFeature.USE_FRONTEND_V2) SensorReceiver.updateAllSensors(this)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (WIPFeature.USE_FRONTEND_V2) {
            viewModel.onAppPaused()

            if (!SdkVersion.isAtLeast(Build.VERSION_CODES.O)) return
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
            val readiness = viewModel.pipReadiness.value ?: return
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(readiness.aspectRatio)
                .apply { readiness.sourceRect?.let(::setSourceRectHint) }
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onStop() {
        super.onStop()
        if (WIPFeature.USE_FRONTEND_V2) {
            viewModel.onAppPaused()
        }
    }
}

/**
 * Triggers biometric authentication when the app is locked.
 *
 * Launches the system biometric prompt when [isAppLocked] becomes `true`.
 * On success, calls [onAuthSucceeded] to unlock. On user cancel, closes the app.
 */
@Composable
private fun AppLockEffect(isAppLocked: Boolean, onAuthSucceeded: () -> Unit) {
    val activity = LocalActivity.current as? FragmentActivity ?: return
    val biometricTitle = stringResource(commonR.string.biometric_title)
    val authenticator = remember {
        Authenticator(activity) { result ->
            when (result) {
                AuthenticationResult.ERROR, AuthenticationResult.CANCELED -> activity.finishAffinity()
                AuthenticationResult.SUCCESS -> onAuthSucceeded()
            }
        }
    }

    LaunchedEffect(isAppLocked) {
        if (isAppLocked) {
            authenticator.authenticate(biometricTitle)
        }
    }
}

@Composable
private fun FullscreenEffect(isFullScreen: Boolean) {
    val view = LocalView.current
    val window = LocalActivity.current?.window ?: return
    val controller = remember(window, view) { WindowInsetsControllerCompat(window, view) }

    // Applies the state immediately (the effect re-runs whenever [isFullScreen] changes) and,
    // while fullscreen, re-applies it every time the window regains focus. The system can
    // transiently restore the system bars when focus is lost — a dialog, the notification
    // shade, or the recents switcher — so re-hiding on focus regain keeps the frontend in
    // fullscreen.
    DisposableEffect(view, isFullScreen) {
        fun applyFullscreen() {
            if (isFullScreen) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(systemBars())
            } else {
                controller.show(systemBars())
            }
        }

        applyFullscreen()

        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            // Only re-hide on focus regain while fullscreen. Outside fullscreen the bars are
            // already shown, so reacting to every focus change here would be redundant work.
            if (hasFocus && isFullScreen) {
                applyFullscreen()
            }
        }
        val viewTreeObserver = view.viewTreeObserver

        viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
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
