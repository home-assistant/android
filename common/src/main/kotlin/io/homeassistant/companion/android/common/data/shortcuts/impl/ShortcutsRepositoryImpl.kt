package io.homeassistant.companion.android.common.data.shortcuts.impl

import android.content.Context
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutFactory
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutIntentCodec
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.ShortcutIntentCodecImpl.Companion.EXTRA_SHORTCUT_PATH
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
import io.homeassistant.companion.android.database.IconDialogCompat
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

private const val DEFAULT_MAX_DYNAMIC_SHORTCUTS = 5
internal const val EXTRA_SERVER = "server"
internal const val ASSIST_SHORTCUT_PREFIX = ".ha_assist_"
private const val DYNAMIC_SHORTCUT_PREFIX = "shortcut"
private const val PINNED_SHORTCUT_PREFIX = "pinned"

private object DynamicShortcutId {
    fun build(index: Int): String = "${DYNAMIC_SHORTCUT_PREFIX}_${index + 1}"

    fun parse(shortcutId: String): Int? {
        if (!shortcutId.startsWith("${DYNAMIC_SHORTCUT_PREFIX}_")) return null
        return shortcutId.substringAfterLast("_", missingDelimiterValue = "")
            .toIntOrNull()
            ?.minus(1)
            ?.takeIf { it >= 0 }
    }
}

@Singleton
class ShortcutsRepositoryImpl @Inject constructor(
    @ApplicationContext private val app: Context,
    // TODO Check warning: See https://youtrack.jetbrains.com/issue/KT-73255 for more details.
    private val serverManager: ServerManager,
    private val shortcutFactory: ShortcutFactory,
    private val shortcutIntentCodec: ShortcutIntentCodec,
) : ShortcutsRepository {

    private val maxDynamicShortcuts: Int by lazy {
        runCatching { ShortcutManagerCompat.getMaxShortcutCountPerActivity(app) }
            .onFailure { Timber.w(it, "Failed to query max shortcut count, using fallback value") }
            .getOrNull()
            ?.takeIf { it > 0 }
            ?: DEFAULT_MAX_DYNAMIC_SHORTCUTS
    }

    private val isShortcutsSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1

    private val canPinShortcuts: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            ShortcutManagerCompat.isRequestPinShortcutSupported(app)
    }

    private val iconIdToName: Map<Int, String> by lazy { IconDialogCompat(app.assets).loadAllIcons() }

    private suspend fun currentServerId(): Int = serverManager.getServer()?.id ?: 0

    private suspend fun getServers(): ShortcutResult<ServersData> {
        val servers = serverManager.servers()
        if (servers.isEmpty()) {
            return ShortcutResult.Error(ShortcutError.NoServers)
        }
        val currentId = currentServerId()
        val defaultId = servers.firstOrNull { it.id == currentId }?.id ?: servers.first().id
        return ShortcutResult.Success(ServersData(servers, defaultId))
    }

    private suspend fun loadServerData(serverId: Int): ServerData = coroutineScope {
        val integrationRepository = serverManager.integrationRepository(serverId)
        val webSocketRepository = serverManager.webSocketRepository(serverId)

        val entitiesJob = async {
            safeLoad("entities", serverId) {
                integrationRepository.getEntities().orEmpty().sortedBy { it.entityId }
            }
        }
        val entityRegistryJob = async {
            safeLoad("entity registry", serverId) {
                webSocketRepository.getEntityRegistry().orEmpty()
            }
        }
        val deviceRegistryJob = async {
            safeLoad("device registry", serverId) {
                webSocketRepository.getDeviceRegistry().orEmpty()
            }
        }
        val areaRegistryJob = async {
            safeLoad("area registry", serverId) {
                webSocketRepository.getAreaRegistry().orEmpty()
            }
        }

        ServerData(
            entities = entitiesJob.await(),
            entityRegistry = entityRegistryJob.await(),
            deviceRegistry = deviceRegistryJob.await(),
            areaRegistry = areaRegistryJob.await(),
        )
    }

    private suspend fun loadDynamicShortcuts(): DynamicShortcutsData {
        val shortcuts = ShortcutManagerCompat.getShortcuts(
            app,
            ShortcutManagerCompat.FLAG_MATCH_DYNAMIC,
        )

        val defaultServerId = currentServerId()

        val shortcutsByIndex = buildMap {
            for (item in shortcuts) {
                val index = DynamicShortcutId.parse(item.id)

                if (index == null) {
                    Timber.w("Skipping dynamic shortcut with unexpected id=%s", item.id)
                    continue
                }

                if (index !in 0 until maxDynamicShortcuts) {
                    Timber.w("Skipping dynamic shortcut with out-of-range index=%d id=%s", index, item.id)
                    continue
                }

                put(index, item.toDraft(defaultServerId, iconIdToName))
            }
        }
        return DynamicShortcutsData(
            maxDynamicShortcuts = maxDynamicShortcuts,
            shortcuts = shortcutsByIndex,
        )
    }

    override suspend fun loadShortcutsList(): ShortcutResult<ShortcutsListData> {
        if (!isShortcutsSupported) {
            return ShortcutResult.Error(ShortcutError.ApiNotSupported)
        }
        if (serverManager.servers().isEmpty()) {
            return ShortcutResult.Error(ShortcutError.NoServers)
        }
        return try {
            val dynamic = loadDynamicShortcuts()
            if (!canPinShortcuts) {
                ShortcutResult.Success(
                    ShortcutsListData(
                        dynamic = dynamic,
                        pinned = emptyList(),
                        pinnedError = ShortcutError.PinnedNotSupported,
                    ),
                )
            } else {
                val pinned = loadPinnedShortcutsInternal(currentServerId())
                ShortcutResult.Success(
                    ShortcutsListData(
                        dynamic = dynamic,
                        pinned = pinned.map { it.toSummary() },
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ShortcutResult.Error(ShortcutError.Unknown, e)
        }
    }

    override suspend fun loadEditorData(): ShortcutResult<ShortcutEditorData> = coroutineScope {
        if (!isShortcutsSupported) {
            return@coroutineScope ShortcutResult.Error(ShortcutError.ApiNotSupported)
        }
        when (val serversResult = getServers()) {
            is ShortcutResult.Success -> {
                val dataById = serversResult.data.servers.map { server ->
                    async {
                        runCatching { server.id to loadServerData(server.id) }
                            .onFailure { Timber.e(it, "Failed to load data for serverId=%s", server.id) }
                            .getOrNull()
                    }
                }.awaitAll().filterNotNull().toMap()
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
        if (!isShortcutsSupported) {
            return ShortcutResult.Error(ShortcutError.ApiNotSupported)
        }
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
        if (!isShortcutsSupported) {
            return ShortcutResult.Error(ShortcutError.ApiNotSupported)
        }
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
        if (!isShortcutsSupported) {
            return ShortcutResult.Error(ShortcutError.ApiNotSupported)
        }
        if (!canPinShortcuts) {
            return ShortcutResult.Error(ShortcutError.PinnedNotSupported)
        }
        if (shortcutId.isBlank()) {
            return ShortcutResult.Error(ShortcutError.InvalidInput)
        }
        val defaultServerId = when (val servers = getServers()) {
            is ShortcutResult.Success -> servers.data.defaultServerId
            is ShortcutResult.Error -> return ShortcutResult.Error(servers.error)
        }
        val pinnedShortcuts = loadPinnedShortcutsInternal(currentServerId())
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
        if (!isShortcutsSupported) {
            return ShortcutResult.Error(ShortcutError.ApiNotSupported)
        }
        if (!canPinShortcuts) {
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
        if (!isShortcutsSupported) {
            return ShortcutResult.Error(ShortcutError.ApiNotSupported)
        }
        if (index !in 0 until maxDynamicShortcuts) {
            return ShortcutResult.Error(ShortcutError.InvalidIndex)
        }
        val shortcutsByIndex = loadDynamicShortcuts().shortcuts
        val exists = shortcutsByIndex.containsKey(index)
        if (!isEditing && exists) {
            return ShortcutResult.Error(ShortcutError.SlotsFull)
        }

        return runCatching {
            val normalized = shortcut.copy(id = DynamicShortcutId.build(index))
            val shortcutInfo = shortcutFactory.createShortcutInfo(normalized)
            ShortcutManagerCompat.addDynamicShortcuts(app, listOf(shortcutInfo))
            ShortcutResult.Success(DynamicEditorData.Edit(index = index, draftSeed = normalized))
        }.getOrElse { throwable ->
            ShortcutResult.Error(ShortcutError.Unknown, throwable)
        }
    }

    override suspend fun deleteDynamicShortcut(index: Int): ShortcutResult<Unit> {
        if (!isShortcutsSupported) {
            return ShortcutResult.Error(ShortcutError.ApiNotSupported)
        }
        if (index !in 0 until maxDynamicShortcuts) {
            return ShortcutResult.Error(ShortcutError.InvalidIndex)
        }
        return runCatching {
            ShortcutManagerCompat.removeDynamicShortcuts(
                app,
                listOf(DynamicShortcutId.build(index)),
            )
            ShortcutResult.Success(Unit)
        }.getOrElse { throwable ->
            ShortcutResult.Error(ShortcutError.Unknown, throwable)
        }
    }

    override suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): ShortcutResult<PinResult> {
        if (!isShortcutsSupported) {
            return ShortcutResult.Error(ShortcutError.ApiNotSupported)
        }
        if (!canPinShortcuts) {
            return ShortcutResult.Error(ShortcutError.PinnedNotSupported)
        }
        val defaultServerId = currentServerId()
        return runCatching {
            val normalized = if (shortcut.id.isBlank()) {
                shortcut.copy(id = newPinnedId())
            } else {
                shortcut
            }
            val shortcutInfo = shortcutFactory.createShortcutInfo(normalized)
            val pinnedShortcuts = loadPinnedShortcutsInternal(defaultServerId)

            val exists = pinnedShortcuts.any { it.id == normalized.id }
            val result = if (exists) {
                Timber.d("Updating pinned shortcut: ${normalized.id}")
                ShortcutManagerCompat.updateShortcuts(app, listOf(shortcutInfo))
                PinResult.Updated
            } else {
                Timber.d("Requesting pin for shortcut: ${normalized.id}")
                ShortcutManagerCompat.requestPinShortcut(app, shortcutInfo, null)
                PinResult.Requested
            }
            ShortcutResult.Success(result)
        }.getOrElse { throwable ->
            ShortcutResult.Error(ShortcutError.Unknown, throwable)
        }
    }

    override suspend fun deletePinnedShortcut(shortcutId: String): ShortcutResult<Unit> {
        if (!isShortcutsSupported) {
            return ShortcutResult.Error(ShortcutError.ApiNotSupported)
        }
        if (!canPinShortcuts) {
            return ShortcutResult.Error(ShortcutError.PinnedNotSupported)
        }
        if (shortcutId.isBlank()) {
            return ShortcutResult.Error(ShortcutError.InvalidInput)
        }
        return runCatching {
            ShortcutManagerCompat.disableShortcuts(app, listOf(shortcutId), null)
            ShortcutResult.Success(Unit)
        }.getOrElse { throwable ->
            ShortcutResult.Error(ShortcutError.Unknown, throwable)
        }
    }

    private fun ShortcutInfoCompat.toDraft(defaultServerId: Int, iconIdToName: Map<Int, String>): ShortcutDraft {
        val extras = intent.extras
        val serverId = extras?.getInt(EXTRA_SERVER, defaultServerId) ?: defaultServerId
        val path = extras?.getString(EXTRA_SHORTCUT_PATH) ?: intent.action.orEmpty()

        val selectedIconName = shortcutIntentCodec.parseIcon(extras, iconIdToName)
        val target = shortcutIntentCodec.parseTarget(extras, path)

        return ShortcutDraft(
            id = id,
            serverId = serverId,
            selectedIconName = selectedIconName,
            label = shortLabel.toString(),
            description = longLabel?.toString().orEmpty(),
            target = target,
        )
    }

    private suspend fun <T> safeLoad(what: String, serverId: Int, block: suspend () -> List<T>): List<T> {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Couldn't load $what for server $serverId")
            emptyList()
        }
    }

    private fun newPinnedId(): String {
        return "${PINNED_SHORTCUT_PREFIX}_${UUID.randomUUID()}"
    }

    private fun loadPinnedShortcutsInternal(defaultServerId: Int): List<ShortcutDraft> {
        val pinnedShortcuts = ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .filter { !it.id.startsWith(ASSIST_SHORTCUT_PREFIX) }
        return pinnedShortcuts.map { it.toDraft(defaultServerId, iconIdToName) }
    }
}
