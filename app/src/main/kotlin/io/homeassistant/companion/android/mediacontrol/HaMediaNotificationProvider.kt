package io.homeassistant.companion.android.mediacontrol

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import io.homeassistant.companion.android.common.R as commonR

/**
 * Extends [DefaultMediaNotificationProvider] to display the Home Assistant notification icon
 * and the media player entity's friendly name as the notification sub-text.
 *
 * @param sessionEntityName Maps a session ID to the friendly name of its media_player entity.
 *   May return null if the entity name is not yet known.
 */
@UnstableApi
class HaMediaNotificationProvider(context: Context, private val sessionEntityName: (sessionId: String) -> String?) :
    DefaultMediaNotificationProvider(context) {

    init {
        setSmallIcon(commonR.drawable.ic_stat_ic_notification)
    }

    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory,
    ): IntArray {
        sessionEntityName(mediaSession.id)?.let { builder.setSubText(it) }
        return super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
    }
}
