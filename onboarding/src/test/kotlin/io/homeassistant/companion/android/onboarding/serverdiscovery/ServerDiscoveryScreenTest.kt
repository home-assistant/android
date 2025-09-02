package io.homeassistant.companion.android.onboarding.serverdiscovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.stringResource
import java.net.URL
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

private val haVersion = HomeAssistantVersion(2025, 1, 1)

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ServerDiscoveryScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setup() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
    }

    @Test
    fun `Given Started state when server discovery is displayed then show loading and handle clicks`() {
        simpleTest(Started)
    }

    @Test
    fun `Given NoServerFound state when server discovery is displayed then show loading and handle clicks`() {
        simpleTest(NoServerFound)
    }

    private fun simpleTest(state: DiscoveryState) {
        composeTestRule.apply {
            var backClicked = false
            var helpClicked = false
            var manualSetupClicked = false

            setContent {
                ServerDiscoveryScreen(
                    state,
                    onBackClick = { backClicked = true },
                    onConnectClick = {},
                    onDismissOneServerFound = {},
                    onHelpClick = { helpClicked = true },
                    onManualSetupClick = { manualSetupClicked = true },
                )
            }

            onNodeWithText(stringResource(R.string.searching_home_network)).assertIsDisplayed()
            // TODO find a way to test the alpha layer or use a custom semantic with the value
            onNodeWithText(stringResource(R.string.server_discovery_no_server_info)).assertIsDisplayed()

            onNodeWithText(stringResource(commonR.string.manual_setup)).assertIsDisplayed().performClick()
            assertTrue(manualSetupClicked)

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsDisplayed().performClick()
            assertTrue(backClicked)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).assertIsDisplayed().performClick()
            assertTrue(helpClicked)
        }
    }

    @Test
    fun `Given ServerDiscovered state when server discovery is displayed then show server and handle clicks`() {
        val serverName = "Home"
        val url = URL("http://192.168.0.1")
        simpleTest(ServerDiscovered(serverName, url, haVersion))
        composeTestRule.apply {
            onNodeWithText(serverName).assertIsDisplayed()
            onNodeWithText(url.toString()).assertIsDisplayed()
            // TODO test that when we click on the server it send the value in the call
        }
    }

    // TODO add test for multiple server to verify that we get the right URL where we click

    // TODO verify that we are not missing tests for server discovery and open the PR
}
