package io.homeassistant.companion.android.settings.server

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.ServerUserAvatarUseCase
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Builds the [ServerChooserItem]s shown in the [ServerChooser] from the given [Server]s.
 *
 * For every server it resolves the current user's name and profile picture (delegated to
 * [ServerUserAvatarUseCase]) and flags the currently active server. Resolution happens concurrently
 * across servers; any server whose picture cannot be resolved simply falls back to a `null` avatar
 * (the view then shows initials).
 */
class ServerChooserItemsUseCase @Inject constructor(
    private val serverManager: ServerManager,
    private val serverUserAvatarUseCase: ServerUserAvatarUseCase,
) {

    suspend operator fun invoke(servers: List<Server>): List<ServerChooserItem> = coroutineScope {
        val activeServerId = serverManager.getServer()?.id
        servers.map { server -> async { server.toChooserItem(isActive = server.id == activeServerId) } }.awaitAll()
    }

    private suspend fun Server.toChooserItem(isActive: Boolean): ServerChooserItem = ServerChooserItem(
        serverId = id,
        userName = user.name?.takeIf { it.isNotBlank() } ?: friendlyName,
        serverName = friendlyName,
        userAvatar = serverUserAvatarUseCase.getUserAvatar(id),
        isActive = isActive,
    )
}
