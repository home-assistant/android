package io.homeassistant.companion.android.common.data.shortcuts.entities

import io.homeassistant.companion.android.common.data.shortcuts.entities.ShortcutServer

/** Available shortcut servers and server-scoped shortcut metadata. */
data class ShortcutServersSnapshot(val servers: List<ShortcutServer>, val defaultServer: ShortcutServer) {
    private fun find(serverId: Int): ShortcutServer? = servers.firstOrNull { it.id == serverId }

    /** Resolves the server for a persisted selection, falling back to [defaultServer] when [serverId] is unknown. */
    fun resolvePersisted(serverId: Int): ShortcutServer = find(serverId) ?: defaultServer
}
