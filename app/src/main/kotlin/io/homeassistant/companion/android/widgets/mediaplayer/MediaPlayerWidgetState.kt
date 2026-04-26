package io.homeassistant.companion.android.widgets.mediaplayer

import androidx.compose.runtime.Composable
import androidx.glance.color.ColorProviders
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme

internal sealed interface MediaPlayerWidgetState {
    fun getColors(): ColorProviders? = null
}

internal object LoadingMediaPlayerState : MediaPlayerWidgetState

internal object EmptyMediaPlayerState : MediaPlayerWidgetState

internal data class MediaPlayerStateWithData(
    val serverId: Int,
    val entityId: String,
    val name: String?,
    val state: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val entityPictureUrl: String?,
    val showSkip: Boolean,
    val showSeek: Boolean,
    val showVolume: Boolean,
    val showSource: Boolean,
    val backgroundType: WidgetBackgroundType,
    val textColor: String?,
) : MediaPlayerWidgetState {
    override fun getColors(): ColorProviders? {
        return if (backgroundType == WidgetBackgroundType.DYNAMICCOLOR) {
            null // Use system dynamic colors
        } else {
            // In a real implementation, we would map backgroundType and textColor to ColorProviders
            null
        }
    }
}
