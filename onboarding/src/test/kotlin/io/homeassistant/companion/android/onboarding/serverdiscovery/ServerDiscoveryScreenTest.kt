package io.homeassistant.companion.android.onboarding.serverdiscovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.compose.assertAlpha
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.stringResource
import java.net.URL
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
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
        composeTestRule.apply {
            testScreen(Started) {
                onNodeWithText(stringResource(R.string.server_discovery_no_server_info))
                    .assertIsDisplayed().assertAlpha(0f)
            }
        }
    }

    @Test
    fun `Given NoServerFound state when server discovery is displayed then show loading and handle clicks`() {
        composeTestRule.apply {
            testScreen(NoServerFound) {
                onNodeWithText(stringResource(R.string.server_discovery_no_server_info))
                    .assertIsDisplayed().assertAlpha(1f)
            }
        }
    }

    @Test
    fun `Given ServerDiscovered state when server discovery is displayed then show server and handle clicks`() {
        val serverName = "Home"
        val url = URL("http://192.168.0.1")
        composeTestRule.apply {
            testScreen(ServerDiscovered(serverName, url, haVersion)) {
                onNodeWithText(stringResource(R.string.server_discovery_no_server_info))
                    .assertIsDisplayed().assertAlpha(0f)
                onNodeWithText(url.toString()).assertIsDisplayed()
                onNodeWithText(serverName).assertIsDisplayed()
                onNodeWithText(stringResource(R.string.server_discovery_connect)).performClick()

                assertEquals(url, connectClickedWithUrl)
            }
        }
    }

    @Test
    fun `Given ServersDiscovered state when server discovery is displayed then show servers and handle clicks`() {
        val server1 = ServerDiscovered("Hello", URL("http://192.168.0.1"), haVersion)
        val server2 = ServerDiscovered("World", URL("http://192.168.0.2"), haVersion)

        composeTestRule.apply {
            testScreen(ServersDiscovered(listOf(server1, server2))) {
                onNodeWithText(stringResource(R.string.server_discovery_no_server_info)).assertIsNotDisplayed()

                fun assertServer(server: ServerDiscovered) {
                    onNodeWithText(server.name).assertIsDisplayed()
                    onNodeWithText(server.url.toString()).assertIsDisplayed().performClick()
                    assertEquals(server.url, connectClickedWithUrl)
                }

                assertServer(server1)
                assertServer(server2)
            }
        }
    }

    private class TestHelper {
        var backClicked = false
        var helpClicked = false
        var manualSetupClicked = false

        var connectClickedWithUrl: URL? = null
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(state: DiscoveryState, dsl: TestHelper.() -> Unit = {}) {
        TestHelper().apply {
            setContent {
                ServerDiscoveryScreen(
                    state,
                    onBackClick = { backClicked = true },
                    onConnectClick = { connectClickedWithUrl = it },
                    onDismissOneServerFound = {
                        // We don't know how to test dismiss of the modal
                    },
                    onHelpClick = { helpClicked = true },
                    onManualSetupClick = { manualSetupClicked = true },
                )
            }

            onNodeWithText(stringResource(R.string.searching_home_network)).assertIsDisplayed()

            onNodeWithText(stringResource(commonR.string.manual_setup)).performScrollTo().assertIsDisplayed().performClick()
            assertTrue(manualSetupClicked)

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsDisplayed().performClick()
            assertTrue(backClicked)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).assertIsDisplayed().performClick()
            assertTrue(helpClicked)

            dsl()
        }
    }
}
