package io.homeassistant.companion.android.launch.link

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.settings.server.ServerChooserItem
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class LinkActivityScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private val items = listOf(
        ServerChooserItem(serverId = 1, userName = "Alice Smith", serverName = "Home"),
        ServerChooserItem(serverId = 2, userName = "Bob", serverName = "Friends home"),
    )

    @Test
    fun `Given ChoosingServer state then the title and every user and server name are displayed`() {
        composeTestRule.apply {
            setContent {
                LinkActivityScreen(
                    uiState = LinkUiState.ChoosingServer(items = items, target = FrontendTarget.Path("/lovelace")),
                    onServerSelected = {},
                    onServerChooserDismissed = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.server_select)).assertExists()
            onNodeWithText("Alice Smith").assertExists()
            onNodeWithText("Home").assertExists()
            onNodeWithText("Bob").assertExists()
            onNodeWithText("Friends home").assertExists()
        }
    }

    @Test
    fun `Given ChoosingServer state when a server row is clicked then onServerSelected is invoked with its id`() {
        var selectedServerId: Int? = null

        composeTestRule.apply {
            setContent {
                LinkActivityScreen(
                    uiState = LinkUiState.ChoosingServer(items = items, target = FrontendTarget.Path("/lovelace")),
                    onServerSelected = { selectedServerId = it },
                    onServerChooserDismissed = {},
                )
            }

            onNodeWithText("Bob").performClick()

            assertEquals(2, selectedServerId)
        }
    }

    @Test
    fun `Given Loading state then no server chooser is displayed`() {
        composeTestRule.apply {
            setContent {
                LinkActivityScreen(
                    uiState = LinkUiState.Loading,
                    onServerSelected = {},
                    onServerChooserDismissed = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.server_select)).assertDoesNotExist()
            onNodeWithText("Alice Smith").assertDoesNotExist()
            onNodeWithText("Bob").assertDoesNotExist()
        }
    }

    @Test
    fun `Given ChoosingServer state when no row is clicked then onServerSelected is not invoked`() {
        var selectedServerId: Int? = null

        composeTestRule.apply {
            setContent {
                LinkActivityScreen(
                    uiState = LinkUiState.ChoosingServer(items = items, target = FrontendTarget.Path("/lovelace")),
                    onServerSelected = { selectedServerId = it },
                    onServerChooserDismissed = {},
                )
            }

            assertNull(selectedServerId)
        }
    }
}
