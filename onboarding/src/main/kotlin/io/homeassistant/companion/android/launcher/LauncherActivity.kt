package io.homeassistant.companion.android.launcher

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.compose.HAApp
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.onboarding.OnboardingRoute
import kotlinx.coroutines.flow.first

/**
 * Main entry point of the application, it is mostly responsible to hold the whole navigation of the application.
 * It also handles the splash screen display based on a condition exposed by the [LauncherViewModel].
 */
@AndroidEntryPoint
class LauncherActivity : AppCompatActivity() {
    private val viewModel: LauncherViewModel by viewModels()

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

                LaunchedEffect(viewModel) {
                    val startDestination = when (viewModel.navigationEventsFlow.first()) {
                        LauncherNavigationEvent.Frontend -> FrontendRoute
                        LauncherNavigationEvent.Onboarding -> OnboardingRoute
                    }
                    navController.navigate(
                        startDestination,
                        navOptions {
                            // We want to clear the stack to remove the LoadingScreen
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }
                        },
                    )
                }

                HAApp(navController)
            }
        }
    }
}
