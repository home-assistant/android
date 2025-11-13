package io.homeassistant.companion.android.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.produceState
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.automotive.navigation.AutomotiveRoute
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.compose.HAApp
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.onboarding.OnboardingRoute
import io.homeassistant.companion.android.onboarding.WearOnboardingRoute
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import kotlinx.coroutines.flow.first
import kotlinx.parcelize.Parcelize

private const val DEEP_LINK_KEY = "deep_link_key"

/**
 * Main entry point of the application, it is mostly responsible to hold the whole navigation of the application.
 * It also handles the splash screen display based on a condition exposed by the [LauncherViewModel].
 */
@AndroidEntryPoint
class LauncherActivity : AppCompatActivity() {
    /**
     * Represents deep link actions that can be passed to [LauncherActivity] to navigate to specific destinations.
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
            return Intent(context, LauncherActivity::class.java).apply {
                if (deepLink != null) {
                    putExtra(DEEP_LINK_KEY, deepLink)
                }
            }
        }
    }

    private val viewModel: LauncherViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<LauncherViewModelFactory> {
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
                val startDestinationState =
                    produceState<HAStartDestinationRoute?>(null, viewModel) {
                        val event = viewModel.navigationEventsFlow.first()
                        value = when (event) {
                            is LauncherNavigationEvent.Frontend -> {
                                if (isAutomotive()) {
                                    AutomotiveRoute
                                } else {
                                    FrontendRoute(event.path, event.serverId)
                                }
                            }

                            is LauncherNavigationEvent.Onboarding -> OnboardingRoute(
                                hasLocationTracking = event.hasLocationTrackingSupport,
                                urlToOnboard = event.urlToOnboard,
                                hideExistingServers = event.hideExistingServers,
                                skipWelcome = event.skipWelcome,
                            )

                            is LauncherNavigationEvent.WearOnboarding -> {
                                // TODO fail when not in FULL variant (maybe make a dedicated screen explaining that
                                //  the wear only work with FULL variant)
                                WearOnboardingRoute(
                                    wearName = event.wearName,
                                    urlToOnboard = event.urlToOnboard,
                                )
                            }
                        }
                    }

                HAApp(navController, startDestinationState.value)
            }
        }
    }
}
