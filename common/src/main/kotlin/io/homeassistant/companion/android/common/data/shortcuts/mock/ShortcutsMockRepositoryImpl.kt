package io.homeassistant.companion.android.common.data.shortcuts.mock

import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.MAX_DYNAMIC_SHORTCUTS
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServerData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServersResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import timber.log.Timber

internal class ShortcutsMockRepositoryImpl @Inject constructor(private val prefsRepository: PrefsRepository) :
    ShortcutsRepository {

    override val maxDynamicShortcuts: Int = MAX_DYNAMIC_SHORTCUTS

    override val canPinShortcuts: Boolean
        get() = runBlocking { prefsRepository.isShortcutsV2MockPinSupportEnabled() }

    override suspend fun currentServerId(): Int = ShortcutsMock.defaultServerId

    override suspend fun getServers(): ServersResult {
        val servers = ShortcutsMock.servers
        if (servers.isEmpty()) return ServersResult.NoServers
        val currentId = currentServerId()
        val defaultId = servers.firstOrNull { it.id == currentId }?.id ?: servers.first().id
        return ServersResult.Success(servers, defaultId)
    }

    override suspend fun loadServerData(serverId: Int): ServerData {
        return ServerData(
            entities = ShortcutsMock.entitiesByServer[serverId].orEmpty(),
            entityRegistry = ShortcutsMock.entityRegistryByServer[serverId].orEmpty(),
            deviceRegistry = ShortcutsMock.deviceRegistryByServer[serverId].orEmpty(),
            areaRegistry = ShortcutsMock.areaRegistryByServer[serverId].orEmpty(),
        )
    }

    override suspend fun loadDynamicShortcuts(): Map<Int, ShortcutDraft> {
        return buildMap {
            for (item in ShortcutsMock.dynamicShortcuts()) {
                val index = MockDynamicShortcutId.parse(item.id)
                if (index == null) {
                    Timber.Forest.w("Skipping dynamic shortcut with unexpected id=%s", item.id)
                    continue
                }
                if (index !in 0 until maxDynamicShortcuts) {
                    Timber.Forest.w("Skipping dynamic shortcut with out-of-range index=%d id=%s", index, item.id)
                    continue
                }
                put(index, item)
            }
        }
    }

    override suspend fun loadPinnedShortcuts(): List<ShortcutDraft> {
        if (!canPinShortcuts) return emptyList()
        return ShortcutsMock.pinnedShortcuts()
    }

    override suspend fun upsertDynamicShortcut(index: Int, shortcut: ShortcutDraft) {
        ShortcutsMock.upsertDynamic(index, shortcut)
    }

    override suspend fun deleteDynamicShortcut(index: Int) {
        ShortcutsMock.removeDynamic(index)
    }

    override suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): PinResult {
        if (!canPinShortcuts) return PinResult.NotSupported
        val existed = ShortcutsMock.pinnedShortcuts().any { it.id == shortcut.id && shortcut.id.isNotBlank() }
        ShortcutsMock.upsertPinned(shortcut)
        return if (existed) PinResult.Updated else PinResult.Requested
    }

    override suspend fun deletePinnedShortcut(shortcutId: String) {
        ShortcutsMock.removePinned(shortcutId)
    }
}
