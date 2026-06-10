package io.homeassistant.companion.android.settings.server

import android.graphics.Bitmap

/**
 * Display model for a single server.
 *
 * @param serverId identifier of the server.
 * @param userName name of the current user on the server.
 * @param serverName friendly name of the server.
 * @param userAvatar the current user's profile picture.
 * @param isActive whether this is the currently active server.
 */
data class ServerChooserItem(
    val serverId: Int,
    val userName: String,
    val serverName: String,
    val userAvatar: Bitmap? = null,
    val isActive: Boolean = false,
)
