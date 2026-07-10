package io.homeassistant.companion.android.settings.qs.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.settings.qs.ManageTilesState
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
                onNodeWithText(activity.getString(commonR.string.tile_server)).assertDoesNotExist()
                onNodeWithContentDescription(activity.getString(commonR.string.undo)).assertDoesNotExist()
                onNodeWithText(activity.getString(commonR.string.tile_subtitle)).performScrollTo().assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.tile_add)).performScrollTo().assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `Given multiple servers state when displayed then server selector and reset are shown and submit is enabled`() {
        composeTestRule.apply {
            testScreen(multipleServersState, submitEnabled = true) {
                onNodeWithText(activity.getString(commonR.string.tile_server), substring = true).performScrollTo().assertIsDisplayed()
                onNodeWithContentDescription(activity.getString(commonR.string.undo)).performScrollTo().assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.tile_save)).performScrollTo().assertIsEnabled()
            }
        }
    }

    @Test
    fun `Given screen when typing label then onTileLabelChange is triggered`() {
        composeTestRule.apply {
            testScreen(addTileState) {
                onNodeWithText(activity.getString(commonR.string.tile_label), substring = true).performScrollTo().performTextInput("Living room")
                assertEquals("Living room", tileLabel)
            }
        }
    }

    @Test
    fun `Given screen when typing subtitle then onTileSubtitleChange is triggered`() {
        composeTestRule.apply {
            testScreen(addTileState) {
                onNodeWithText(activity.getString(commonR.string.tile_subtitle)).performScrollTo().performTextInput("Lights")
                assertEquals("Lights", tileSubtitle)
            }
        }
    }

    @Test
    fun `Given screen when toggling vibrate switch then onShouldVibrateChange is triggered`() {
        composeTestRule.apply {
            testScreen(addTileState) {
                onNodeWithText(activity.getString(commonR.string.tile_vibrate)).performScrollTo().performClick()
                assertEquals(true, shouldVibrate)
            }
        }
    }

    @Test
    fun `Given screen when toggling auth switch then onAuthRequiredChange is triggered`() {
        composeTestRule.apply {
            testScreen(addTileState) {
                onNodeWithText(activity.getString(commonR.string.tile_auth_required)).performScrollTo().performClick()
                assertEquals(true, authRequired)
            }
        }
    }

    @Test
    fun `Given screen when clicking icon button then onShowIconDialog is triggered`() {
        composeTestRule.apply {
            testScreen(addTileState) {
                onNodeWithContentDescription(activity.getString(commonR.string.tile_icon)).performScrollTo().performClick()
                assertTrue(iconDialogShown)
            }
        }
    }

    @Test
    fun `Given reset icon shown when clicking reset then onResetIcon is triggered`() {
        composeTestRule.apply {
            testScreen(multipleServersState) {
                onNodeWithContentDescription(activity.getString(commonR.string.undo)).performScrollTo().performClick()
                assertTrue(iconReset)
            }
        }
    }

    @Test
    fun `Given submit enabled when clicking submit then onSubmit is triggered`() {
        composeTestRule.apply {
            testScreen(multipleServersState, submitEnabled = true) {
                onNodeWithText(activity.getString(commonR.string.tile_save)).performScrollTo().performClick()
                assertTrue(submitted)
            }
        }
    }

    private class TestHelper {
        var tileSelected: String? = null
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
        submitEnabled: Boolean = false,
        dsl: TestHelper.() -> Unit,
    ) {
        TestHelper().apply {
            setContent {
                ManageTilesContent(
                    snackbarHostState = remember { SnackbarHostState() },
                    state = state,
                    submitEnabled = submitEnabled,
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
        val addTileState = ManageTilesState(
            tileSlotsDropdownItems = listOf(
                HADropdownItem(key = "tile_1", label = "Tile 1"),
                HADropdownItem(key = "tile_2", label = "Tile 2"),
            ),
            selectedTileId = "tile_1",
            serversDropdownItems = listOf(HADropdownItem(key = 1, label = "Home")),
            selectedServerId = 1,
            tileLabel = "",
            showSubtitle = true,
            tileSubtitle = "",
            selectedEntityId = "",
            entityRegistry = emptyList(),
            deviceRegistry = emptyList(),
            areaRegistry = emptyList(),
            selectedIcon = null,
            submitButtonLabel = commonR.string.tile_add,
        )

        val multipleServersState = addTileState.copy(
            serversDropdownItems = listOf(
                HADropdownItem(key = 1, label = "Home"),
                HADropdownItem(key = 2, label = "Vacation home"),
            ),
            selectedServerId = 1,
            selectedIconId = "mdi:home",
            tileLabel = "Living room",
            selectedEntityId = "light.living_room",
            submitButtonLabel = commonR.string.tile_save,
        )
    }
}
