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
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
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

private const val SELECTED_ENTITY_NAME = "Living room speaker"

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
        setScreen(uiState(isInputValid = false, selectedEntities = emptyList()))

        composeTestRule.onNodeWithText(stringResource(commonR.string.add_widget))
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun `Given a valid selection when the controls are used then the matching callbacks are invoked`() {
        var actionClicked = false
        var closed = false
        var volumeChange: Boolean? = null
        var skipChange: Boolean? = null
        var seekChange: Boolean? = null
        var sourceChange: Boolean? = null
        var removedEntityId: String? = null

        setScreen(
            uiState = uiState(isInputValid = true),
            onShowVolumeChanged = { volumeChange = it },
            onShowSkipChanged = { skipChange = it },
            onShowSeekChanged = { seekChange = it },
            onShowSourceChanged = { sourceChange = it },
            onEntityRemoved = { removedEntityId = it },
            onActionClick = { actionClicked = true },
            onClose = { closed = true },
        )

        composeTestRule.apply {
            // The selected entity is rendered from the resolved view state, not filtered in the UI;
            // the remove click below fails if that row was not rendered.
            clickRow(commonR.string.widget_media_show_volume)
            clickRow(commonR.string.widget_media_show_skip)
            clickRow(commonR.string.widget_media_show_seek)
            clickRow(commonR.string.widget_media_show_source)

            onNodeWithContentDescription(stringResource(commonR.string.delete))
                .performScrollTo()
                .performClick()

            onNodeWithText(stringResource(commonR.string.add_widget))
                .performScrollTo()
                .performClick()

            onNodeWithContentDescription(stringResource(commonR.string.close))
                .performClick()
        }

        // All show-* flags default to true, so toggling them reports false.
        assertEquals(false, volumeChange)
        assertEquals(false, skipChange)
        assertEquals(false, seekChange)
        assertEquals(false, sourceChange)
        assertEquals(previewEntity1.entityId, removedEntityId)
        assertTrue("onActionClick should be invoked", actionClicked)
        assertTrue("onClose should be invoked", closed)
    }

    private fun clickRow(textResId: Int) {
        composeTestRule.onNodeWithText(stringResource(textResId))
            .performScrollTo()
            .performClick()
    }

    private fun setScreen(
        uiState: MediaPlayerControlsWidgetConfigureUiState,
        onServerSelected: (Int) -> Unit = {},
        onEntityAdded: (String) -> Unit = {},
        onEntityRemoved: (String) -> Unit = {},
        onLabelChanged: (String) -> Unit = {},
        onShowVolumeChanged: (Boolean) -> Unit = {},
        onShowSkipChanged: (Boolean) -> Unit = {},
        onShowSeekChanged: (Boolean) -> Unit = {},
        onShowSourceChanged: (Boolean) -> Unit = {},
        onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit = {},
        onActionClick: () -> Unit = {},
        onClose: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            HAThemeForPreview {
                MediaPlayerControlsWidgetConfigureContent(
                    uiState = uiState,
                    dynamicColorAvailable = false,
                    onServerSelected = onServerSelected,
                    onEntityAdded = onEntityAdded,
                    onEntityRemoved = onEntityRemoved,
                    onLabelChanged = onLabelChanged,
                    onShowVolumeChanged = onShowVolumeChanged,
                    onShowSkipChanged = onShowSkipChanged,
                    onShowSeekChanged = onShowSeekChanged,
                    onShowSourceChanged = onShowSourceChanged,
                    onBackgroundTypeSelected = onBackgroundTypeSelected,
                    onActionClick = onActionClick,
                    onClose = onClose,
                )
            }
        }
    }

    private fun uiState(
        isInputValid: Boolean = true,
        selectedEntities: List<SelectedMediaPlayer> = listOf(
            SelectedMediaPlayer(previewEntity1.entityId, SELECTED_ENTITY_NAME),
        ),
        showVolume: Boolean = true,
    ) = MediaPlayerControlsWidgetConfigureUiState(
        config = MediaPlayerControlsWidgetConfigureViewState(
            selectedServerId = previewServer1.id,
            selectedEntityIds = selectedEntities.map { it.entityId },
            showVolume = showVolume,
        ),
        servers = listOf(previewServer1),
        availableEntities = emptyList(),
        selectedEntities = selectedEntities,
        isInputValid = isInputValid,
    )
}
