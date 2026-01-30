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
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutsListData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.empty
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.toSummary
import javax.inject.Inject
import timber.log.Timber

internal class ShortcutsMockRepositoryImpl @Inject constructor(private val prefsRepository: PrefsRepository) :
    ShortcutsRepository {

    private val maxDynamicShortcuts: Int = MAX_DYNAMIC_SHORTCUTS

    private suspend fun currentServerId(): Int = ShortcutsMock.defaultServerId

    override suspend fun getServers(): ShortcutResult<ServersData> {
        val servers = ShortcutsMock.servers
        if (servers.isEmpty()) return ShortcutResult.Error(ShortcutError.NoServers)
        val currentId = currentServerId()
        val defaultId = servers.firstOrNull { it.id == currentId }?.id ?: servers.first().id
        return ShortcutResult.Success(ServersData(servers, defaultId))
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

    override suspend fun loadShortcutsList(): ShortcutResult<ShortcutsListData> {
        val dynamic = loadDynamicShortcuts()
        if (!canPinShortcuts()) {
            return ShortcutResult.Success(
                ShortcutsListData(
                    dynamic = dynamic,
                    pinned = emptyList(),
                    pinnedError = ShortcutError.PinnedNotSupported,
                ),
            )
        }

        return ShortcutResult.Success(
            ShortcutsListData(
                dynamic = dynamic,
                pinned = ShortcutsMock.pinnedShortcuts().map { it.toSummary() },
            ),
        )
    }

    override suspend fun loadEditorData(): ShortcutResult<ShortcutEditorData> {
        return when (val serversResult = getServers()) {
            is ShortcutResult.Success -> {
                val dataById = serversResult.data.servers.associate { server ->
                    server.id to loadServerData(server.id)
                }
                ShortcutResult.Success(
                    ShortcutEditorData(
                        servers = serversResult.data.servers,
                        serverDataById = dataById,
                    ),
                )
            }

            is ShortcutResult.Error -> ShortcutResult.Error(serversResult.error)
        }
    }

    override suspend fun loadDynamicEditor(index: Int): ShortcutResult<DynamicEditorData> {
        if (index !in 0 until maxDynamicShortcuts) {
            return ShortcutResult.Error(ShortcutError.InvalidIndex)
        }
        val defaultServerId = when (val servers = getServers()) {
            is ShortcutResult.Success -> servers.data.defaultServerId
            is ShortcutResult.Error -> return ShortcutResult.Error(servers.error)
        }
        val shortcuts = loadDynamicShortcuts().shortcuts
        val existingDraft = shortcuts[index]
        val draft = existingDraft ?: ShortcutDraft.empty(index).copy(serverId = defaultServerId)
        val data = if (existingDraft != null) {
            DynamicEditorData.Edit(index = index, draftSeed = draft)
        } else {
            DynamicEditorData.Create(index = index, draftSeed = draft)
        }
        return ShortcutResult.Success(data)
    }

    override suspend fun loadDynamicEditorFirstAvailable(): ShortcutResult<DynamicEditorData> {
        val defaultServerId = when (val servers = getServers()) {
            is ShortcutResult.Success -> servers.data.defaultServerId
            is ShortcutResult.Error -> return ShortcutResult.Error(servers.error)
        }
        val shortcuts = loadDynamicShortcuts().shortcuts
        val firstAvailableIndex = (0 until maxDynamicShortcuts).firstOrNull { candidate ->
            !shortcuts.containsKey(candidate)
        } ?: return ShortcutResult.Error(ShortcutError.SlotsFull)
        val draft = ShortcutDraft.empty(firstAvailableIndex).copy(serverId = defaultServerId)
        return ShortcutResult.Success(
            DynamicEditorData.Create(index = firstAvailableIndex, draftSeed = draft),
        )
    }

    override suspend fun loadPinnedEditor(shortcutId: String): ShortcutResult<PinnedEditorData> {
        if (!canPinShortcuts()) {
            return ShortcutResult.Error(ShortcutError.PinnedNotSupported)
        }
        if (shortcutId.isBlank()) {
            return ShortcutResult.Error(ShortcutError.InvalidInput)
        }
        val defaultServerId = when (val servers = getServers()) {
            is ShortcutResult.Success -> servers.data.defaultServerId
            is ShortcutResult.Error -> return ShortcutResult.Error(servers.error)
        }
        val pinnedShortcuts = ShortcutsMock.pinnedShortcuts()
        val pinned = pinnedShortcuts.firstOrNull { it.id == shortcutId }
        val draft = pinned ?: ShortcutDraft.empty(shortcutId).copy(serverId = defaultServerId)
        val data = if (pinned != null) {
            PinnedEditorData.Edit(draftSeed = draft)
        } else {
            PinnedEditorData.Create(draftSeed = draft)
        }
        return ShortcutResult.Success(data)
    }

    override suspend fun loadPinnedEditorForCreate(): ShortcutResult<PinnedEditorData> {
        if (!canPinShortcuts()) {
            return ShortcutResult.Error(ShortcutError.PinnedNotSupported)
        }
        val defaultServerId = when (val servers = getServers()) {
            is ShortcutResult.Success -> servers.data.defaultServerId
            is ShortcutResult.Error -> return ShortcutResult.Error(servers.error)
        }
        val draft = ShortcutDraft.empty("").copy(serverId = defaultServerId)
        return ShortcutResult.Success(PinnedEditorData.Create(draftSeed = draft))
    }

    override suspend fun upsertDynamicShortcut(
        index: Int,
        shortcut: ShortcutDraft,
        isEditing: Boolean,
    ): ShortcutResult<DynamicEditorData> {
        if (index !in 0 until maxDynamicShortcuts) {
            return ShortcutResult.Error(ShortcutError.InvalidIndex)
        }
        val shortcuts = loadDynamicShortcuts().shortcuts
        val exists = shortcuts.containsKey(index)
        if (!isEditing && exists) {
            return ShortcutResult.Error(ShortcutError.SlotsFull)
        }
        ShortcutsMock.upsertDynamic(index, shortcut)
        val normalized = shortcut.copy(id = MockDynamicShortcutId.build(index))
        return ShortcutResult.Success(DynamicEditorData.Edit(index = index, draftSeed = normalized))
    }

    override suspend fun deleteDynamicShortcut(index: Int): ShortcutResult<Unit> {
        if (index !in 0 until maxDynamicShortcuts) {
            return ShortcutResult.Error(ShortcutError.InvalidIndex)
        }
        ShortcutsMock.removeDynamic(index)
        return ShortcutResult.Success(Unit)
    }

    override suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): ShortcutResult<PinResult> {
        if (!canPinShortcuts()) {
            return ShortcutResult.Error(ShortcutError.PinnedNotSupported)
        }
        val existed = ShortcutsMock.pinnedShortcuts().any { it.id == shortcut.id && shortcut.id.isNotBlank() }
        ShortcutsMock.upsertPinned(shortcut)
        val result = if (existed) PinResult.Updated else PinResult.Requested
        return ShortcutResult.Success(result)
    }

    override suspend fun deletePinnedShortcut(shortcutId: String): ShortcutResult<Unit> {
        if (!canPinShortcuts()) {
            return ShortcutResult.Error(ShortcutError.PinnedNotSupported)
        }
        if (shortcutId.isBlank()) {
            return ShortcutResult.Error(ShortcutError.InvalidInput)
        }
        ShortcutsMock.removePinned(shortcutId)
        return ShortcutResult.Success(Unit)
    }

    private suspend fun canPinShortcuts(): Boolean {
        return prefsRepository.isShortcutsV2MockPinSupportEnabled()
    }
}
