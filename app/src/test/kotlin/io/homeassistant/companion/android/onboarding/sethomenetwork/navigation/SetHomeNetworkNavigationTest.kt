package io.homeassistant.companion.android.onboarding.sethomenetwork.navigation

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavDestination.Companion.hasRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.BaseOnboardingNavigationTest
import io.homeassistant.companion.android.testing.unit.stringResource
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation tests for the Set Home Network screen in the onboarding flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class SetHomeNetworkNavigationTest : BaseOnboardingNavigationTest() {

    @Test
    fun `Given SetHomeNetwork when clicking next then onboarding completes`() {
        testNavigation {
            navController.navigateToSetHomeNetworkRoute(42)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<SetHomeNetworkRoute>() == true)

            onNodeWithText(stringResource(commonR.string.set_home_network_next))
                .performScrollTo()
                .performClick()
            assertTrue(onboardingDone)
        }
    }
}
