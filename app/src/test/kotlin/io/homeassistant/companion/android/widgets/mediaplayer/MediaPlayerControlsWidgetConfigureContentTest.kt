package io.homeassistant.companion.android.widgets.mediaplayer

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
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import java.time.LocalDateTime
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
class MediaPlayerControlsWidgetConfigureContentTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given no entity selected when displayed then the add action is disabled and no configuration available`() {
        composeTestRule.apply {
            testScreen(newWidgetState) {
                onNodeWithText(activity.getString(commonR.string.widget_media_show_volume)).assertDoesNotExist()
                onNodeWithText(activity.getString(commonR.string.label_label), substring = true).assertDoesNotExist()
                onNodeWithText(activity.getString(commonR.string.widget_background_type_label), substring = true)
                    .assertDoesNotExist()
                onNodeWithText(activity.getString(commonR.string.add_widget))
                    .performScrollTo()
                    .assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `Given entities selected when displayed then the configuration and update action are shown`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.widget_media_selected_entities))
                    .performScrollTo()
                    .assertIsDisplayed()
                onNodeWithText("Living room speaker").performScrollTo().assertIsDisplayed()
                onNodeWithText("Kitchen", substring = true).performScrollTo().assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.widget_media_show_volume))
                    .performScrollTo()
                    .assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.widget_background_type_label), substring = true)
                    .performScrollTo()
                    .assertIsDisplayed()
                onNodeWithText(activity.getString(commonR.string.update_widget))
                    .performScrollTo()
                    .assertIsEnabled()
            }
        }
    }

    @Test
    fun `Given a selected entity when its remove button is clicked then onEntityRemoved is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithContentDescription(activity.getString(commonR.string.delete))
                    .performScrollTo()
                    .performClick()
                assertEquals(ENTITY.entityId, entityRemoved)
            }
        }
    }

    @Test
    fun `Given the screen is the task root when displayed then it offers close`() {
        composeTestRule.apply {
            testScreen(configuredState, canNavigateBack = false) {
                onNodeWithContentDescription(activity.getString(commonR.string.navigate_up)).assertDoesNotExist()
                onNodeWithContentDescription(activity.getString(commonR.string.close)).performClick()
                assertTrue(navigated)
            }
        }
    }

    @Test
    fun `Given the screen has a back stack when displayed then it offers back`() {
        composeTestRule.apply {
            testScreen(configuredState, canNavigateBack = true) {
                onNodeWithContentDescription(activity.getString(commonR.string.close)).assertDoesNotExist()
                onNodeWithContentDescription(activity.getString(commonR.string.navigate_up)).performClick()
                assertTrue(navigated)
            }
        }
    }

    @Test
    fun `Given a valid configuration when the action is clicked then onActionClick is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.update_widget))
                    .performScrollTo()
                    .performClick()
                assertTrue(actionClicked)
            }
        }
    }

    @Test
    fun `Given a single server when displayed then the server selector is hidden`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.server_select)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Given several servers when displayed then the server selector is shown`() {
        composeTestRule.apply {
            testScreen(multipleServersState) {
                onNodeWithText(activity.getString(commonR.string.server_select), substring = true)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given the show options when their rows are clicked then the matching callbacks report the toggle`() {
        composeTestRule.apply {
            testScreen(configuredState) {
                onNodeWithText(activity.getString(commonR.string.widget_media_show_volume))
                    .performScrollTo()
                    .performClick()
                onNodeWithText(activity.getString(commonR.string.widget_media_show_skip))
                    .performScrollTo()
                    .performClick()
                onNodeWithText(activity.getString(commonR.string.widget_media_show_seek))
                    .performScrollTo()
                    .performClick()
                onNodeWithText(activity.getString(commonR.string.widget_media_show_source))
                    .performScrollTo()
                    .performClick()

                // All show options start enabled in the state, so toggling reports false
                assertEquals(false, showVolume)
                assertEquals(false, showSkip)
                assertEquals(false, showSeek)
                assertEquals(false, showSource)
            }
        }
    }

    @Test
    fun `Given screen when typing a label then onLabelChanged is triggered`() {
        composeTestRule.apply {
            testScreen(configuredState.copy(label = "")) {
                onNodeWithText(activity.getString(commonR.string.label_label), substring = true)
                    .performScrollTo()
                    .performTextInput("Kitchen")
                assertEquals("Kitchen", label)
            }
        }
    }

    private class TestHelper {
        var serverSelected: Int? = null
        var entityAdded: String? = null
        var entityRemoved: String? = null
        var label: String? = null
        var showVolume: Boolean? = null
        var showSkip: Boolean? = null
        var showSeek: Boolean? = null
        var showSource: Boolean? = null
        var backgroundType: WidgetBackgroundType? = null
        var actionClicked = false
        var navigated = false
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(
        state: MediaPlayerControlsWidgetConfigureState,
        canNavigateBack: Boolean = false,
        dsl: TestHelper.() -> Unit,
    ) {
        TestHelper().apply {
            setContent {
                HATheme {
                    MediaPlayerControlsWidgetConfigureContent(
                        state = state,
                        snackbarHostState = remember { SnackbarHostState() },
                        canNavigateBack = canNavigateBack,
                        onNavigate = { navigated = true },
                        onServerSelected = { serverSelected = it },
                        onEntityAdded = { entityAdded = it },
                        onEntityRemoved = { entityRemoved = it },
                        onLabelChanged = { label = it },
                        onShowVolumeChanged = { showVolume = it },
                        onShowSkipChanged = { showSkip = it },
                        onShowSeekChanged = { showSeek = it },
                        onShowSourceChanged = { showSource = it },
                        onBackgroundTypeSelected = { backgroundType = it },
                        onActionClick = { actionClicked = true },
                    )
                }
            }
            dsl()
        }
    }

    private companion object {
        val ENTITY = Entity(
            entityId = "media_player.living_room",
            state = "playing",
            attributes = mapOf("friendly_name" to "Living room speaker"),
            lastChanged = LocalDateTime.MIN,
            lastUpdated = LocalDateTime.MIN,
        )

        val newWidgetState = MediaPlayerControlsWidgetConfigureState(
            serversDropdownItems = listOf(HADropdownItem(key = 1, label = "Home")),
            selectedServerId = 1,
            entityDisplayState = EntityDisplayState.Loaded(
                listOf(EntityDisplayWithContext(EntityDisplayWithoutContext(ENTITY), areaName = "Kitchen")),
            ),
        )

        val configuredState = newWidgetState.copy(
            selectedEntityIds = listOf(ENTITY.entityId),
            label = "Living room",
            isUpdateWidget = true,
        )

        val multipleServersState = configuredState.copy(
            serversDropdownItems = listOf(
                HADropdownItem(key = 1, label = "Home"),
                HADropdownItem(key = 2, label = "Vacation home"),
            ),
        )
    }
}
