package io.homeassistant.companion.android.launch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.util.compose.HAApp
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import kotlinx.parcelize.Parcelize

private const val DEEP_LINK_KEY = "deep_link_key"

/**
 * Main entry point of the application, it is mostly responsible to hold the whole navigation of the application.
 * It also handles the splash screen display based on a condition exposed by the [LaunchViewModel].
 */
@AndroidEntryPoint
class LaunchActivity : AppCompatActivity() {
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

                when (val state = uiState) {
                    is LaunchUiState.Ready -> HAApp(navController, state.startDestination)
                    LaunchUiState.NetworkUnavailable -> NetworkUnavailableDialog(onBackClick = ::finish)
                    LaunchUiState.WearUnsupported -> WearUnsupportedDialog(onBackClick = ::finish)
                    LaunchUiState.Loading -> {
                        // Splash screen is still showing
                    }
                }
            }
        }
    }
}
