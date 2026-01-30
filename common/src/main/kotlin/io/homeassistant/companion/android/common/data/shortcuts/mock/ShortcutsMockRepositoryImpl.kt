package io.homeassistant.companion.android.common.data.shortcuts.mock

import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.MAX_DYNAMIC_SHORTCUTS
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.DynamicEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.DynamicShortcutsData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinnedEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServerData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServersData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutRepositoryError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutRepositoryResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutsListData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.empty
import javax.inject.Inject
import timber.log.Timber

internal class ShortcutsMockRepositoryImpl @Inject constructor(private val prefsRepository: PrefsRepository) :
    ShortcutsRepository {

    private val maxDynamicShortcuts: Int = MAX_DYNAMIC_SHORTCUTS

    private suspend fun currentServerId(): Int = ShortcutsMock.defaultServerId

    override suspend fun getServers(): ShortcutRepositoryResult<ServersData> {
        val servers = ShortcutsMock.servers
        if (servers.isEmpty()) return ShortcutRepositoryResult.Error(ShortcutRepositoryError.NoServers)
        val currentId = currentServerId()
        val defaultId = servers.firstOrNull { it.id == currentId }?.id ?: servers.first().id
        return ShortcutRepositoryResult.Success(ServersData(servers, defaultId))
    }

    private suspend fun loadServerData(serverId: Int): ServerData {
        return ServerData(
            entities = ShortcutsMock.entitiesByServer[serverId].orEmpty(),
            entityRegistry = ShortcutsMock.entityRegistryByServer[serverId].orEmpty(),
            deviceRegistry = ShortcutsMock.deviceRegistryByServer[serverId].orEmpty(),
            areaRegistry = ShortcutsMock.areaRegistryByServer[serverId].orEmpty(),
        )
    }

    private suspend fun loadDynamicShortcuts(): DynamicShortcutsData {
        val shortcutsByIndex = buildMap {
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
        return DynamicShortcutsData(
            maxDynamicShortcuts = maxDynamicShortcuts,
            shortcuts = shortcutsByIndex,
        )
    }

    override suspend fun loadShortcutsList(): ShortcutRepositoryResult<ShortcutsListData> {
        val dynamic = loadDynamicShortcuts()
        if (!canPinShortcuts()) {
            return ShortcutRepositoryResult.Success(
                ShortcutsListData(
                    dynamic = dynamic,
                    pinned = emptyList(),
                    pinnedError = ShortcutRepositoryError.PinnedNotSupported,
                ),
            )
        }

        return ShortcutRepositoryResult.Success(
            ShortcutsListData(
                dynamic = dynamic,
                pinned = ShortcutsMock.pinnedShortcuts(),
            ),
        )
    }

    override suspend fun loadEditorData(): ShortcutRepositoryResult<ShortcutEditorData> {
        return when (val serversResult = getServers()) {
            is ShortcutRepositoryResult.Success -> {
                val dataById = serversResult.data.servers.associate { server ->
                    server.id to loadServerData(server.id)
                }
                ShortcutRepositoryResult.Success(
                    ShortcutEditorData(
                        servers = serversResult.data.servers,
                        serverDataById = dataById,
                    ),
                )
            }

            is ShortcutRepositoryResult.Error -> ShortcutRepositoryResult.Error(serversResult.error)
        }
    }

    override suspend fun loadDynamicEditor(index: Int): ShortcutRepositoryResult<DynamicEditorData> {
        if (index !in 0 until maxDynamicShortcuts) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.InvalidIndex)
        }
        val defaultServerId = when (val servers = getServers()) {
            is ShortcutRepositoryResult.Success -> servers.data.defaultServerId
            is ShortcutRepositoryResult.Error -> return ShortcutRepositoryResult.Error(servers.error)
        }
        val shortcuts = loadDynamicShortcuts().shortcuts
        val existingDraft = shortcuts[index]
        val draft = existingDraft ?: ShortcutDraft.empty(index).copy(serverId = defaultServerId)
        val data = if (existingDraft != null) {
            DynamicEditorData.Edit(index = index, draftSeed = draft)
        } else {
            DynamicEditorData.Create(index = index, draftSeed = draft)
        }
        return ShortcutRepositoryResult.Success(data)
    }

    override suspend fun loadDynamicEditorFirstAvailable(): ShortcutRepositoryResult<DynamicEditorData> {
        val defaultServerId = when (val servers = getServers()) {
            is ShortcutRepositoryResult.Success -> servers.data.defaultServerId
            is ShortcutRepositoryResult.Error -> return ShortcutRepositoryResult.Error(servers.error)
        }
        val shortcuts = loadDynamicShortcuts().shortcuts
        val firstAvailableIndex = (0 until maxDynamicShortcuts).firstOrNull { candidate ->
            !shortcuts.containsKey(candidate)
        } ?: return ShortcutRepositoryResult.Error(ShortcutRepositoryError.SlotsFull)
        val draft = ShortcutDraft.empty(firstAvailableIndex).copy(serverId = defaultServerId)
        return ShortcutRepositoryResult.Success(
            DynamicEditorData.Create(index = firstAvailableIndex, draftSeed = draft),
        )
    }

    override suspend fun loadPinnedEditor(shortcutId: String): ShortcutRepositoryResult<PinnedEditorData> {
        if (!canPinShortcuts()) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.PinnedNotSupported)
        }
        if (shortcutId.isBlank()) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.InvalidInput)
        }
        val defaultServerId = when (val servers = getServers()) {
            is ShortcutRepositoryResult.Success -> servers.data.defaultServerId
            is ShortcutRepositoryResult.Error -> return ShortcutRepositoryResult.Error(servers.error)
        }
        val pinnedShortcuts = ShortcutsMock.pinnedShortcuts()
        val pinned = pinnedShortcuts.firstOrNull { it.id == shortcutId }
        val draft = pinned ?: ShortcutDraft.empty(shortcutId).copy(serverId = defaultServerId)
        val data = if (pinned != null) {
            PinnedEditorData.Edit(draftSeed = draft)
        } else {
            PinnedEditorData.Create(draftSeed = draft)
        }
        return ShortcutRepositoryResult.Success(data)
    }

    override suspend fun loadPinnedEditorForCreate(): ShortcutRepositoryResult<PinnedEditorData> {
        if (!canPinShortcuts()) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.PinnedNotSupported)
        }
        val defaultServerId = when (val servers = getServers()) {
            is ShortcutRepositoryResult.Success -> servers.data.defaultServerId
            is ShortcutRepositoryResult.Error -> return ShortcutRepositoryResult.Error(servers.error)
        }
        val draft = ShortcutDraft.empty("").copy(serverId = defaultServerId)
        return ShortcutRepositoryResult.Success(PinnedEditorData.Create(draftSeed = draft))
    }

    override suspend fun upsertDynamicShortcut(
        index: Int,
        shortcut: ShortcutDraft,
        isEditing: Boolean,
    ): ShortcutRepositoryResult<DynamicEditorData> {
        if (index !in 0 until maxDynamicShortcuts) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.InvalidIndex)
        }
        val shortcuts = loadDynamicShortcuts().shortcuts
        val exists = shortcuts.containsKey(index)
        if (!isEditing && exists) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.SlotsFull)
        }
        ShortcutsMock.upsertDynamic(index, shortcut)
        val normalized = shortcut.copy(id = MockDynamicShortcutId.build(index))
        return ShortcutRepositoryResult.Success(DynamicEditorData.Edit(index = index, draftSeed = normalized))
    }

    override suspend fun deleteDynamicShortcut(index: Int): ShortcutRepositoryResult<Unit> {
        if (index !in 0 until maxDynamicShortcuts) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.InvalidIndex)
        }
        ShortcutsMock.removeDynamic(index)
        return ShortcutRepositoryResult.Success(Unit)
    }

    override suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): ShortcutRepositoryResult<PinResult> {
        if (!canPinShortcuts()) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.PinnedNotSupported)
        }
        val existed = ShortcutsMock.pinnedShortcuts().any { it.id == shortcut.id && shortcut.id.isNotBlank() }
        ShortcutsMock.upsertPinned(shortcut)
        val result = if (existed) PinResult.Updated else PinResult.Requested
        return ShortcutRepositoryResult.Success(result)
    }

    override suspend fun deletePinnedShortcut(shortcutId: String): ShortcutRepositoryResult<Unit> {
        if (!canPinShortcuts()) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.PinnedNotSupported)
        }
        if (shortcutId.isBlank()) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.InvalidInput)
        }
        ShortcutsMock.removePinned(shortcutId)
        return ShortcutRepositoryResult.Success(Unit)
    }

    private suspend fun canPinShortcuts(): Boolean {
        return prefsRepository.isShortcutsV2MockPinSupportEnabled()
    }
}
