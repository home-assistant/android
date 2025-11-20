package io.homeassistant.companion.android.onboarding.serverdiscovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.compose.assertAlpha
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import java.net.URL
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val haVersion = HomeAssistantVersion(2025, 1, 1)

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ServerDiscoveryScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given Started state when server discovery is displayed then show loading and handle clicks`() {
        composeTestRule.apply {
            testScreen(Started) {
                onNodeWithText(stringResource(commonR.string.server_discovery_no_server_info))
                    .assertIsDisplayed().assertAlpha(0f)
            }
        }
    }

    @Test
    fun `Given NoServerFound state when server discovery is displayed then show loading and handle clicks`() {
        composeTestRule.apply {
            testScreen(NoServerFound) {
                onNodeWithText(stringResource(commonR.string.server_discovery_no_server_info))
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
                onNodeWithText(stringResource(commonR.string.server_discovery_no_server_info))
                    .assertIsDisplayed().assertAlpha(0f)
                onNodeWithText(url.toString()).assertIsDisplayed()
                onNodeWithText(serverName).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.server_discovery_connect)).performClick()

                composeTestRule.mainClock.advanceTimeUntil { connectClickedWithUrl != null }
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
                onNodeWithText(stringResource(commonR.string.server_discovery_no_server_info)).assertIsNotDisplayed()

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

    @Test
    fun `Given ServerDiscover state when getting state ServersDiscovered then bottom sheet is still displayed on top of the server list`() {
        val server1 = ServerDiscovered("Hello", URL("http://192.168.0.1"), haVersion)
        val server2 = ServerDiscovered("World", URL("http://192.168.0.2"), haVersion)

        composeTestRule.apply {
            val state = mutableStateOf<DiscoveryState>(server1)
            setContent {
                val discoveryState by remember(state) { state }
                ServerDiscoveryScreen(
                    discoveryState,
                    onBackClick = { },
                    onConnectClick = { },
                    onDismissOneServerFound = {
                        // We don't know how to test dismiss of the modal
                    },
                    onHelpClick = { },
                    onManualSetupClick = { },
                )
            }

            onNodeWithText(server1.name).assertIsDisplayed()
            onNodeWithTag(ONE_SERVER_FOUND_MODAL_TAG).performTouchInput {
                swipeUp(startY = bottom * 0.9f, endY = centerY, durationMillis = 200)
            }

            waitForIdle()
            onNodeWithText(stringResource(commonR.string.server_discovery_connect)).assertIsDisplayed()

            state.value = ServersDiscovered(listOf(server1, server2))

            onNodeWithText(stringResource(commonR.string.server_discovery_connect)).assertIsDisplayed()
            onNodeWithText(server2.name).assertIsDisplayed()
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
                    discoveryState = state,
                    onBackClick = { backClicked = true },
                    onConnectClick = { connectClickedWithUrl = it },
                    onDismissOneServerFound = {
                        // We don't know how to test dismiss of the modal
                    },
                    onHelpClick = { helpClicked = true },
                    onManualSetupClick = { manualSetupClicked = true },
                )
            }

            onNodeWithText(stringResource(commonR.string.searching_home_network)).assertIsDisplayed()

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
