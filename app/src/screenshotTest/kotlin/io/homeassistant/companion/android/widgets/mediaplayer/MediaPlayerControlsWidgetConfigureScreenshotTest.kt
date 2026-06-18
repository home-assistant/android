package io.homeassistant.companion.android.widgets.mediaplayer

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
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
    fun `MediaPlayerControlsWidgetConfigureScreen single server`() {
        HAThemeForPreview {
            MediaPlayerControlsWidgetConfigureView(
                state = MediaPlayerControlsWidgetConfigureViewState(
                    selectedServerId = previewServer1.id,
                    selectedEntityId = previewEntity1.entityId,
                    label = "Living room",
                    showVolume = true,
                    showSkip = true,
                    showSeek = false,
                    showSource = true,
                    backgroundType = WidgetBackgroundType.DAYNIGHT,
                    isUpdateWidget = false,
                ),
                servers = listOf(previewServer1),
                entities = listOf(previewEntity1),
                dynamicColorAvailable = true,
                onServerSelected = {},
                onEntitySelected = {},
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

    @PreviewTest
    @HAPreviews
    @Composable
    fun `MediaPlayerControlsWidgetConfigureScreen multiple servers updating`() {
        HAThemeForPreview {
            MediaPlayerControlsWidgetConfigureView(
                state = MediaPlayerControlsWidgetConfigureViewState(
                    selectedServerId = previewServer2.id,
                    selectedEntityId = previewEntity2.entityId,
                    label = "",
                    showVolume = true,
                    showSkip = false,
                    showSeek = true,
                    showSource = false,
                    backgroundType = WidgetBackgroundType.TRANSPARENT,
                    isUpdateWidget = true,
                ),
                servers = listOf(previewServer1, previewServer2),
                entities = listOf(previewEntity1, previewEntity2),
                dynamicColorAvailable = true,
                onServerSelected = {},
                onEntitySelected = {},
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
}
