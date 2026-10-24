package io.homeassistant.companion.android.widgets.mediaplayer

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HAPreviews
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity2
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2

class MediaPlayerControlsWidgetConfigureScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `MediaPlayerControlsWidgetConfigureScreen multiple servers updating`() {
        HAThemeForPreview {
            MediaPlayerControlsWidgetConfigureContent(
                state = previewState.copy(
                    selectedServerId = previewServer2.id,
                    label = "",
                    showSkip = false,
                    showSource = false,
                    selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
                    isUpdateWidget = true,
                ),
                canNavigateBack = false,
                onNavigate = {},
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
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `MediaPlayerControlsWidgetConfigureScreen no selected entity`() {
        HAThemeForPreview {
            MediaPlayerControlsWidgetConfigureContent(
                state = previewState.copy(
                    serversDropdownItems = listOf(previewServer1).map {
                        HADropdownItem(key = it.id, label = it.friendlyName)
                    },
                    selectedEntityIds = emptyList(),
                ),
                canNavigateBack = false,
                onNavigate = {},
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
            )
        }
    }
}

private val previewState = MediaPlayerControlsWidgetConfigureState(
    selectedServerId = previewServer1.id,
    serversDropdownItems = listOf(previewServer1, previewServer2).map {
        HADropdownItem(key = it.id, label = it.friendlyName)
    },
    entityDisplayState = EntityDisplayState.Loaded(
        listOf(
            EntityDisplayWithContext(
                EntityDisplayWithoutContext(previewEntity1, name = "Main speaker"),
                areaName = "Kitchen",
            ),
            EntityDisplayWithContext(EntityDisplayWithoutContext(previewEntity2, name = "TV speaker")),
        ),
    ),
    selectedEntityIds = listOf(previewEntity1.entityId, previewEntity2.entityId),
    label = "Living room",
    showVolume = true,
    showSkip = true,
    showSeek = false,
    showSource = true,
    selectedBackgroundType = WidgetBackgroundType.DAYNIGHT,
    dynamicColorAvailable = true,
)
