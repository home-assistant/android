package io.homeassistant.companion.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.navigation.compose.rememberNavController
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.onboarding.OnboardingRoute

class HAAppScreenshotTest {

    @PreviewTest
    @PreviewLightDark
    @Composable
    fun `HAApp startDestination OnboardingRoute`() {
        HATheme {
            HAApp(
                navController = rememberNavController(),
                startDestination = OnboardingRoute,
            )
        }
    }

    @PreviewTest
    @PreviewLightDark
    @Composable
    fun `HAApp startDestination FrontendRoute`() {
        HATheme {
            HAApp(
                navController = rememberNavController(),
                startDestination = FrontendRoute,
            )
        }
    }
}
