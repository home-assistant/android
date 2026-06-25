package io.homeassistant.companion.android.settings.server

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.ServerUserAvatarUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Builds the [ServerChooserItem]s shown in the [ServerChooser] from the given [Server]s.
 *
 * The returned [Flow] emits immediately with every server resolved except its avatar (so the
 * chooser can render names/initials without waiting on I/O), then re-emits an updated list each time
 * a server's profile picture finishes loading. Avatars are resolved concurrently (delegated to
 * [ServerUserAvatarUseCase]); a server whose picture cannot be resolved keeps its `null` avatar (the
 * view then shows initials) and produces no further emission. The active server is flagged up front.
 */
class ServerChooserItemsUseCase @Inject constructor(
    private val serverManager: ServerManager,
    private val serverUserAvatarUseCase: ServerUserAvatarUseCase,
) {

    operator fun invoke(servers: List<Server>): Flow<List<ServerChooserItem>> = channelFlow {
        val activeServerId = serverManager.getServer()?.id
        val items = servers.map { it.toChooserItem(isActive = it.id == activeServerId) }.toMutableList()
        send(items.toList())

        // Patch and re-emit as each avatar resolves. A mutex keeps the shared list mutation and the
        // snapshot it sends atomic across the concurrent loaders.
        val mutex = Mutex()
        servers.forEachIndexed { index, server ->
            launch {
                val avatar = serverUserAvatarUseCase.getUserAvatar(server.id) ?: return@launch
                mutex.withLock {
                    items[index] = items[index].copy(userAvatar = avatar)
                    send(items.toList())
                }
            }
        }
    }

    private fun Server.toChooserItem(isActive: Boolean): ServerChooserItem = ServerChooserItem(
        serverId = id,
        userName = user.name?.takeIf { it.isNotBlank() } ?: friendlyName,
        serverName = friendlyName,
        userAvatar = null,
        isActive = isActive,
    )
}
