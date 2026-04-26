package io.homeassistant.companion.android.widgets.mediaplayer

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.Text
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTypography
import io.homeassistant.companion.android.util.compose.glanceStringResource

class MediaPlayerGlanceAppWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface MediaPlayerGlanceWidgetEntryPoint {
        fun stateUpdater(): MediaPlayerWidgetStateUpdater
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val widgetId = manager.getAppWidgetId(id)

        provideContent {
            val entryPoints = remember { EntryPoints.get(context, MediaPlayerGlanceWidgetEntryPoint::class.java) }
            val flow = remember { entryPoints.stateUpdater().stateFlow(widgetId) }
            val state by flow.collectAsState(LoadingMediaPlayerState)

            HomeAssistantGlanceTheme(
                colors = state.getColors() ?: HomeAssistantGlanceTheme.colors,
            ) {
                MediaPlayerScreen(state)
            }
        }
    }
}

@Composable
private fun MediaPlayerScreen(state: MediaPlayerWidgetState) {
    Column(
        modifier = GlanceModifier
            .appWidgetBackground()
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            is LoadingMediaPlayerState -> LoadingScreen()
            is EmptyMediaPlayerState -> EmptyScreen()
            is MediaPlayerStateWithData -> MediaPlayerContent(state)
        }
    }
}

@Composable
private fun LoadingScreen() {
    CircularProgressIndicator(color = GlanceTheme.colors.primary)
}

@Composable
private fun EmptyScreen() {
    Text(
        text = glanceStringResource(commonR.string.widget_no_configuration),
        style = HomeAssistantGlanceTypography.bodyMedium
    )
}

@Composable
private fun MediaPlayerContent(state: MediaPlayerStateWithData) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isPlaying = state.state == "playing"

        if (isPlaying) {
            if (state.title != null) {
                Text(
                    text = state.title,
                    style = HomeAssistantGlanceTypography.titleMedium,
                    maxLines = 1,
                    modifier = GlanceModifier.padding(bottom = 2.dp)
                )
            }
            if (state.artist != null) {
                Text(
                    text = state.artist,
                    style = HomeAssistantGlanceTypography.bodySmall,
                    maxLines = 1,
                    modifier = GlanceModifier.padding(bottom = 8.dp)
                )
            } else if (state.name != null && state.title == null) {
                Text(
                    text = state.name,
                    style = HomeAssistantGlanceTypography.titleSmall,
                    maxLines = 1,
                    modifier = GlanceModifier.padding(bottom = 8.dp)
                )
            }

            Image(
                provider = ImageProvider(R.drawable.ic_play),
                contentDescription = null,
                modifier = GlanceModifier.size(100.dp).padding(16.dp)
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.app_icon_launch),
                    contentDescription = null,
                    modifier = GlanceModifier.size(80.dp).padding(bottom = 8.dp)
                )
                Text(
                    text = state.name ?: glanceStringResource(commonR.string.media_player),
                    style = HomeAssistantGlanceTypography.bodyMedium,
                    modifier = GlanceModifier.padding(bottom = 4.dp)
                )
                Text(
                    text = glanceStringResource(commonR.string.state_paused),
                    style = HomeAssistantGlanceTypography.bodySmall,
                    modifier = GlanceModifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 100)
@Composable
private fun MediaPlayerPreview() {
    HomeAssistantGlanceTheme {
        MediaPlayerScreen(
            MediaPlayerStateWithData(
                serverId = 1,
                entityId = "media_player.living_room",
                name = "Living Room",
                state = "playing",
                title = "Bohemian Rhapsody",
                artist = "Queen",
                album = "A Night at the Opera",
                entityPictureUrl = null,
                showSkip = true,
                showSeek = true,
                showVolume = true,
                showSource = false,
                backgroundType = WidgetBackgroundType.DAYNIGHT,
                textColor = null
            )
        )
    }
}
