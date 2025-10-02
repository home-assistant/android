package io.homeassistant.companion.android.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import kotlinx.coroutines.flow.first
import kotlinx.parcelize.Parcelize

private const val DEEP_LINK_KEY = "deep_link_key"

/**
 * Main entry point of the application, it is mostly responsible to hold the whole navigation of the application.
 * It also handles the splash screen display based on a condition exposed by the [LauncherViewModel].
 */
@AndroidEntryPoint
class LauncherActivity : AppCompatActivity() {
    @Parcelize
    sealed interface DeepLink : Parcelable {
        data class Invite(val url: String) : DeepLink
        data class NavigateTo(val path: String?, val serverId: Int) : DeepLink
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

        enableEdgeToEdge()

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
                            is LauncherNavigationEvent.Onboarding -> OnboardingRoute(event.url)
                        }
                    }

                HAApp(navController, startDestinationState.value)
            }
        }
    }
}
