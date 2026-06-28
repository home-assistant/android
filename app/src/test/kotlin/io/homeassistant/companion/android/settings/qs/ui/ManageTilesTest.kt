package io.homeassistant.companion.android.settings.qs.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.settings.qs.ManageTilesState
import io.homeassistant.companion.android.settings.qs.TileSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ManageTilesTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given add tile state when displayed then optional sections are hidden and submit is disabled`() {
        composeTestRule.apply {
            testScreen(addTileState) {
                onNodeWithTag(MANAGE_TILES_SERVER_DROPDOWN_TAG).assertIsNotDisplayed()
                onNodeWithContentDescription(MANAGE_TILES_RESET_ICON_TAG).assertIsNotDisplayed()
                onNodeWithTag(MANAGE_TILES_SUBTITLE_TAG).performScrollTo().assertIsDisplayed()
                onNodeWithTag(MANAGE_TILES_SUBMIT_TAG).performScrollTo().assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `Given multiple servers state when displayed then server selector and reset are shown and submit is enabled`() {
        composeTestRule.apply {
            testScreen(multipleServersState) {
                onNodeWithTag(MANAGE_TILES_SERVER_DROPDOWN_TAG).performScrollTo().assertIsDisplayed()
                onNodeWithContentDescription(MANAGE_TILES_RESET_ICON_TAG).performScrollTo().assertIsDisplayed()
                onNodeWithTag(MANAGE_TILES_SUBMIT_TAG).performScrollTo().assertIsEnabled()
            }
        }
    }

    @Test
    fun `Given screen when typing label then onTileLabelChange is triggered`() {
        composeTestRule.apply {
            testScreen(addTileState) {
                onNodeWithTag(MANAGE_TILES_LABEL_TAG).performScrollTo().performTextInput("Living room")
                assertEquals("Living room", tileLabel)
            }
        }
    }

    @Test
    fun `Given screen when typing subtitle then onTileSubtitleChange is triggered`() {
        composeTestRule.apply {
            testScreen(addTileState) {
                onNodeWithTag(MANAGE_TILES_SUBTITLE_TAG).performScrollTo().performTextInput("Lights")
                assertEquals("Lights", tileSubtitle)
            }
        }
    }

    @Test
    fun `Given screen when toggling vibrate switch then onShouldVibrateChange is triggered`() {
        composeTestRule.apply {
            testScreen(addTileState) {
                onNodeWithTag(MANAGE_TILES_VIBRATE_SWITCH_TAG).performScrollTo().performClick()
                assertEquals(true, shouldVibrate)
            }
        }
    }

    @Test
    fun `Given screen when toggling auth switch then onAuthRequiredChange is triggered`() {
        composeTestRule.apply {
            testScreen(addTileState) {
                onNodeWithTag(MANAGE_TILES_AUTH_SWITCH_TAG).performScrollTo().performClick()
                assertEquals(true, authRequired)
            }
        }
    }

    @Test
    fun `Given screen when clicking icon button then onShowIconDialog is triggered`() {
        composeTestRule.apply {
            testScreen(addTileState) {
                onNodeWithTag(MANAGE_TILES_ICON_BUTTON_TAG).performScrollTo().performClick()
                assertTrue(iconDialogShown)
            }
        }
    }

    @Test
    fun `Given reset icon shown when clicking reset then onResetIcon is triggered`() {
        composeTestRule.apply {
            testScreen(multipleServersState) {
                onNodeWithContentDescription(MANAGE_TILES_RESET_ICON_TAG).performScrollTo().performClick()
                assertTrue(iconReset)
            }
        }
    }

    @Test
    fun `Given submit enabled when clicking submit then onSubmit is triggered`() {
        composeTestRule.apply {
            testScreen(multipleServersState) {
                onNodeWithTag(MANAGE_TILES_SUBMIT_TAG).performScrollTo().performClick()
                assertTrue(submitted)
            }
        }
    }

    private class TestHelper {
        var tileSelected: Int? = null
        var serverSelected: Int? = null
        var tileLabel: String? = null
        var tileSubtitle: String? = null
        var entitySelected: String? = null
        var entityCleared = false
        var iconDialogShown = false
        var iconReset = false
        var shouldVibrate: Boolean? = null
        var authRequired: Boolean? = null
        var submitted = false
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(
        state: ManageTilesState,
        dsl: TestHelper.() -> Unit,
    ) {
        TestHelper().apply {
            setContent {
                ManageTiles(
                    snackbarHostState = remember { SnackbarHostState() },
                    state = state,
                    submitEnabled = false,
                    onTileSelected = { tileSelected = it },
                    onServerSelected = { serverSelected = it },
                    onTileLabelChange = { tileLabel = it },
                    onTileSubtitleChange = { tileSubtitle = it },
                    onEntitySelectedId = { entitySelected = it },
                    onEntityCleared = { entityCleared = true },
                    onShowIconDialog = { iconDialogShown = true },
                    onResetIcon = { iconReset = true },
                    onShouldVibrateChange = { shouldVibrate = it },
                    onAuthRequiredChange = { authRequired = it },
                    onSubmit = { submitted = true },
                )
            }
            dsl()
        }
    }

    private companion object {
        fun fakeServer(id: Int, name: String) = Server(
            id = id,
            _name = name,
            connection = ServerConnectionInfo(externalUrl = "https://example.com"),
            session = ServerSessionInfo(),
            user = ServerUserInfo(),
        )

        val addTileState = ManageTilesState(
            tileSlots = listOf(
                TileSlot(id = "tile_1", name = "Tile 1"),
                TileSlot(id = "tile_2", name = "Tile 2"),
            ),
            selectedTile = TileSlot(id = "tile_1", name = "Tile 1"),
            servers = emptyList(),
            selectedServerId = 0,
            tileLabel = "",
            tileSubtitle = "",
            selectedEntityId = "",
            entityRegistry = emptyList(),
            deviceRegistry = emptyList(),
            areaRegistry = emptyList(),
            selectedIcon = null,
            submitButtonLabel = commonR.string.tile_add,
        )

        val multipleServersState = addTileState.copy(
            servers = listOf(
                fakeServer(id = 1, name = "Home"),
                fakeServer(id = 2, name = "Vacation home"),
            ),
            selectedServerId = 1,
            tileLabel = "Living room",
            selectedEntityId = "light.living_room",
            submitButtonLabel = commonR.string.tile_save,
        )
    }
}
