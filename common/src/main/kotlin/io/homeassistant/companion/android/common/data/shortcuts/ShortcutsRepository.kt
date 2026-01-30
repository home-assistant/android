package io.homeassistant.companion.android.common.data.shortcuts

import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServerData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServersResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft

interface ShortcutsRepository {
    val maxDynamicShortcuts: Int

    val canPinShortcuts: Boolean

    suspend fun currentServerId(): Int

    suspend fun getServers(): ServersResult

    suspend fun loadServerData(serverId: Int): ServerData

    suspend fun loadDynamicShortcuts(): Map<Int, ShortcutDraft>

    suspend fun loadPinnedShortcuts(): List<ShortcutDraft>

    suspend fun upsertDynamicShortcut(index: Int, shortcut: ShortcutDraft)

    suspend fun deleteDynamicShortcut(index: Int)

    suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): PinResult

    suspend fun deletePinnedShortcut(shortcutId: String)
}
