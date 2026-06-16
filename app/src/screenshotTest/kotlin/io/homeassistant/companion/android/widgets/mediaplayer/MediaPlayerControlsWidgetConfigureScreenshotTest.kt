package io.homeassistant.companion.android.widgets.mediaplayer

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

private val backgroundOptions = listOf(
    HADropdownItem("Day/Night", "Day/Night"),
    HADropdownItem("Transparent", "Transparent"),
    HADropdownItem("Dynamic color", "Dynamic color"),
)

class MediaPlayerControlsWidgetConfigureScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `MediaPlayerControlsWidgetConfigureScreen single server`() {
        HAThemeForPreview {
            MediaPlayerControlsWidgetConfigureScreen(
                servers = listOf(HADropdownItem(1, "Home")),
                selectedServerId = 1,
                onServerSelected = {},
                entityId = "media_player.living_room",
                onEntityIdChange = {},
                showVolume = true,
                onShowVolumeChange = {},
                showSkip = true,
                onShowSkipChange = {},
                showSeek = false,
                onShowSeekChange = {},
                showSource = true,
                onShowSourceChange = {},
                widgetLabel = "Living room",
                onWidgetLabelChange = {},
                backgroundOptions = backgroundOptions,
                selectedBackgroundKey = "Day/Night",
                onBackgroundSelected = {},
                isUpdate = false,
                onAddWidgetClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `MediaPlayerControlsWidgetConfigureScreen multiple servers updating`() {
        HAThemeForPreview {
            MediaPlayerControlsWidgetConfigureScreen(
                servers = listOf(
                    HADropdownItem(1, "Home"),
                    HADropdownItem(2, "Office"),
                ),
                selectedServerId = 2,
                onServerSelected = {},
                entityId = "media_player.office_speaker",
                onEntityIdChange = {},
                showVolume = true,
                onShowVolumeChange = {},
                showSkip = false,
                onShowSkipChange = {},
                showSeek = true,
                onShowSeekChange = {},
                showSource = false,
                onShowSourceChange = {},
                widgetLabel = "",
                onWidgetLabelChange = {},
                backgroundOptions = backgroundOptions,
                selectedBackgroundKey = "Transparent",
                onBackgroundSelected = {},
                isUpdate = true,
                onAddWidgetClick = {},
            )
        }
    }
}
