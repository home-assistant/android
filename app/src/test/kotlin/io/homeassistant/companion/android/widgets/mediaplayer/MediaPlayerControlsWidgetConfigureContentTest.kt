package io.homeassistant.companion.android.widgets.mediaplayer

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewServer1
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
    fun `Given an invalid selection then the action button is disabled`() {
        composeTestRule.apply {
            setContent {
                HAThemeForPreview {
                    MediaPlayerControlsWidgetConfigureContent(
                        uiState = uiState(isInputValid = false, selectedEntityIds = emptyList()),
                        dynamicColorAvailable = false,
                        onServerSelected = {},
                        onEntityAdded = {},
                        onEntityRemoved = {},
                        onLabelChanged = {},
                        onShowVolumeChanged = {},
                        onShowSkipChanged = {},
                        onShowSeekChanged = {},
                        onShowSourceChanged = {},
                        onBackgroundTypeSelected = {},
                        onActionClick = {},
                        onClose = {},
                    )
                }
            }

            onNodeWithText(stringResource(commonR.string.add_widget))
                .performScrollTo()
                .assertIsNotEnabled()
        }
    }

    @Test
    fun `Given a valid selection when the action button is clicked then onActionClick is invoked`() {
        var actionClicked = false
        composeTestRule.apply {
            setContent {
                HAThemeForPreview {
                    MediaPlayerControlsWidgetConfigureContent(
                        uiState = uiState(isInputValid = true),
                        dynamicColorAvailable = false,
                        onServerSelected = {},
                        onEntityAdded = {},
                        onEntityRemoved = {},
                        onLabelChanged = {},
                        onShowVolumeChanged = {},
                        onShowSkipChanged = {},
                        onShowSeekChanged = {},
                        onShowSourceChanged = {},
                        onBackgroundTypeSelected = {},
                        onActionClick = { actionClicked = true },
                        onClose = {},
                    )
                }
            }

            onNodeWithText(stringResource(commonR.string.add_widget))
                .performScrollTo()
                .performClick()

            assertTrue("onActionClick should be invoked", actionClicked)
        }
    }

    @Test
    fun `Given the show volume row when it is clicked then onShowVolumeChanged is invoked with the toggled value`() {
        var volumeChange: Boolean? = null
        composeTestRule.apply {
            setContent {
                HAThemeForPreview {
                    MediaPlayerControlsWidgetConfigureContent(
                        uiState = uiState(showVolume = true),
                        dynamicColorAvailable = false,
                        onServerSelected = {},
                        onEntityAdded = {},
                        onEntityRemoved = {},
                        onLabelChanged = {},
                        onShowVolumeChanged = { volumeChange = it },
                        onShowSkipChanged = {},
                        onShowSeekChanged = {},
                        onShowSourceChanged = {},
                        onBackgroundTypeSelected = {},
                        onActionClick = {},
                        onClose = {},
                    )
                }
            }

            onNodeWithText(stringResource(commonR.string.widget_media_show_volume))
                .performScrollTo()
                .performClick()

            assertEquals(false, volumeChange)
        }
    }

    @Test
    fun `Given a selected entity when its remove button is clicked then onEntityRemoved is invoked`() {
        var removedEntityId: String? = null
        composeTestRule.apply {
            setContent {
                HAThemeForPreview {
                    MediaPlayerControlsWidgetConfigureContent(
                        uiState = uiState(selectedEntityIds = listOf(previewEntity1.entityId)),
                        dynamicColorAvailable = false,
                        onServerSelected = {},
                        onEntityAdded = {},
                        onEntityRemoved = { removedEntityId = it },
                        onLabelChanged = {},
                        onShowVolumeChanged = {},
                        onShowSkipChanged = {},
                        onShowSeekChanged = {},
                        onShowSourceChanged = {},
                        onBackgroundTypeSelected = {},
                        onActionClick = {},
                        onClose = {},
                    )
                }
            }

            onNodeWithText(previewEntity1.entityId).assertExists()
            onNodeWithContentDescription(stringResource(commonR.string.delete))
                .performScrollTo()
                .performClick()

            assertEquals(previewEntity1.entityId, removedEntityId)
        }
    }

    private fun uiState(
        isInputValid: Boolean = true,
        selectedEntityIds: List<String> = listOf(previewEntity1.entityId),
        availableEntities: List<Entity> = listOf(previewEntity1),
        showVolume: Boolean = true,
    ) = MediaPlayerControlsWidgetConfigureUiState(
        config = MediaPlayerControlsWidgetConfigureViewState(
            selectedServerId = previewServer1.id,
            selectedEntityIds = selectedEntityIds,
            showVolume = showVolume,
        ),
        servers = listOf(previewServer1),
        availableEntities = availableEntities,
        isInputValid = isInputValid,
    )
}
