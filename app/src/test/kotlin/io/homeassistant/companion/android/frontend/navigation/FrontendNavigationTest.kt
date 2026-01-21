package io.homeassistant.companion.android.frontend.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.frontend.FrontendViewState
import io.homeassistant.companion.android.frontend.error.FrontendError
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation tests for the FrontendNavigationHandler.
 *
 * Tests verify that navigation callbacks are triggered with the correct server ID
 * when the view state requires navigation to other screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class FrontendNavigationTest {

    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given SecurityLevelRequired state then onNavigateToSecurityLevel is called with serverId`() {
        val serverId = 42
        var navigatedServerId: Int? = null

        composeTestRule.setContent {
            FrontendNavigationHandler(
                viewState = FrontendViewState.SecurityLevelRequired(serverId = serverId),
                onNavigateToSecurityLevel = { navigatedServerId = it },
                onNavigateToInsecure = {},
            )
        }

        composeTestRule.waitForIdle()
        assertEquals(serverId, navigatedServerId)
    }

    @Test
    fun `Given Insecure state then onNavigateToInsecure is called with serverId`() {
        val serverId = 99
        var navigatedServerId: Int? = null

        composeTestRule.setContent {
            FrontendNavigationHandler(
                viewState = FrontendViewState.Insecure(serverId = serverId),
                onNavigateToSecurityLevel = {},
                onNavigateToInsecure = { navigatedServerId = it },
            )
        }

        composeTestRule.waitForIdle()
        assertEquals(serverId, navigatedServerId)
    }

    @Test
    fun `Given Loading state then no navigation callback is triggered`() {
        var securityLevelServerId: Int? = null
        var insecureServerId: Int? = null

        composeTestRule.setContent {
            FrontendNavigationHandler(
                viewState = FrontendViewState.Loading(serverId = 1, url = "https://example.com"),
                onNavigateToSecurityLevel = { securityLevelServerId = it },
                onNavigateToInsecure = { insecureServerId = it },
            )
        }

        composeTestRule.waitForIdle()
        assertNull(securityLevelServerId)
        assertNull(insecureServerId)
    }

    @Test
    fun `Given Error state then no navigation callback is triggered`() {
        var securityLevelServerId: Int? = null
        var insecureServerId: Int? = null

        composeTestRule.setContent {
            FrontendNavigationHandler(
                viewState = FrontendViewState.Error(
                    serverId = 1,
                    url = "https://example.com",
                    error = FrontendError.UnreachableError(0, "", ""),
                ),
                onNavigateToSecurityLevel = { securityLevelServerId = it },
                onNavigateToInsecure = { insecureServerId = it },
            )
        }

        composeTestRule.waitForIdle()
        assertNull(securityLevelServerId)
        assertNull(insecureServerId)
    }

    @Test
    fun `Given Content state then no navigation callback is triggered`() {
        var securityLevelServerId: Int? = null
        var insecureServerId: Int? = null

        composeTestRule.setContent {
            FrontendNavigationHandler(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
                onNavigateToSecurityLevel = { securityLevelServerId = it },
                onNavigateToInsecure = { insecureServerId = it },
            )
        }

        composeTestRule.waitForIdle()
        assertNull(securityLevelServerId)
        assertNull(insecureServerId)
    }

    @Test
    fun `Given LoadServer state then no navigation callback is triggered`() {
        var securityLevelServerId: Int? = null
        var insecureServerId: Int? = null

        composeTestRule.setContent {
            FrontendNavigationHandler(
                viewState = FrontendViewState.LoadServer(serverId = 1),
                onNavigateToSecurityLevel = { securityLevelServerId = it },
                onNavigateToInsecure = { insecureServerId = it },
            )
        }

        composeTestRule.waitForIdle()
        assertNull(securityLevelServerId)
        assertNull(insecureServerId)
    }
}
