package io.homeassistant.companion.android.common.data.shortcuts.impl

import android.content.Context
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutFactory
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutIntentCodec
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutIntentKeys
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
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
import io.homeassistant.companion.android.database.IconDialogCompat
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

// Android allows at most 5 dynamic shortcuts per app.
internal const val MAX_DYNAMIC_SHORTCUTS = 5
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
internal class ShortcutsRepositoryImpl @Inject constructor(
    @ApplicationContext private val app: Context,
    // TODO Check warning: See https://youtrack.jetbrains.com/issue/KT-73255 for more details.
    private val serverManager: ServerManager,
    private val shortcutFactory: ShortcutFactory,
    private val shortcutIntentCodec: ShortcutIntentCodec,
) : ShortcutsRepository {

    private val maxDynamicShortcuts: Int = MAX_DYNAMIC_SHORTCUTS

    private val canPinShortcuts: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            ShortcutManagerCompat.isRequestPinShortcutSupported(app)
    }

    private val iconIdToName: Map<Int, String> by lazy { IconDialogCompat(app.assets).loadAllIcons() }

    private suspend fun currentServerId(): Int = serverManager.getServer()?.id ?: 0

    override suspend fun getServers(): ShortcutRepositoryResult<ServersData> {
        val servers = serverManager.servers()
        if (servers.isEmpty()) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.NoServers)
        }
        val currentId = currentServerId()
        val defaultId = servers.firstOrNull { it.id == currentId }?.id ?: servers.first().id
        return ShortcutRepositoryResult.Success(ServersData(servers, defaultId))
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

    override suspend fun loadShortcutsList(): ShortcutRepositoryResult<ShortcutsListData> {
        val dynamic = loadDynamicShortcuts()
        if (!canPinShortcuts) {
            return ShortcutRepositoryResult.Success(
                ShortcutsListData(
                    dynamic = dynamic,
                    pinned = emptyList(),
                    pinnedError = ShortcutRepositoryError.PinnedNotSupported,
                ),
            )
        }

        val pinned = loadPinnedShortcutsInternal(currentServerId())
        return ShortcutRepositoryResult.Success(
            ShortcutsListData(
                dynamic = dynamic,
                pinned = pinned,
            ),
        )
    }

    override suspend fun loadEditorData(): ShortcutRepositoryResult<ShortcutEditorData> = coroutineScope {
        when (val serversResult = getServers()) {
            is ShortcutRepositoryResult.Success -> {
                val dataById = serversResult.data.servers.map { server ->
                    async {
                        runCatching { server.id to loadServerData(server.id) }
                            .onFailure { Timber.e(it, "Failed to load data for serverId=%s", server.id) }
                            .getOrNull()
                    }
                }.awaitAll().filterNotNull().toMap()
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
        if (!canPinShortcuts) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.PinnedNotSupported)
        }
        if (shortcutId.isBlank()) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.InvalidInput)
        }
        val defaultServerId = when (val servers = getServers()) {
            is ShortcutRepositoryResult.Success -> servers.data.defaultServerId
            is ShortcutRepositoryResult.Error -> return ShortcutRepositoryResult.Error(servers.error)
        }
        val pinnedShortcuts = loadPinnedShortcutsInternal(currentServerId())
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
        if (!canPinShortcuts) {
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
        val shortcutsByIndex = loadDynamicShortcuts().shortcuts
        val exists = shortcutsByIndex.containsKey(index)
        if (!isEditing && exists) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.SlotsFull)
        }

        return runCatching {
            val normalized = shortcut.copy(id = DynamicShortcutId.build(index))
            val shortcutInfo = shortcutFactory.createShortcutInfo(normalized)
            ShortcutManagerCompat.addDynamicShortcuts(app, listOf(shortcutInfo))
            ShortcutRepositoryResult.Success(DynamicEditorData.Edit(index = index, draftSeed = normalized))
        }.getOrElse { throwable ->
            ShortcutRepositoryResult.Error(ShortcutRepositoryError.Unknown, throwable)
        }
    }

    override suspend fun deleteDynamicShortcut(index: Int): ShortcutRepositoryResult<Unit> {
        if (index !in 0 until maxDynamicShortcuts) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.InvalidIndex)
        }
        return runCatching {
            ShortcutManagerCompat.removeDynamicShortcuts(
                app,
                listOf(DynamicShortcutId.build(index)),
            )
            ShortcutRepositoryResult.Success(Unit)
        }.getOrElse { throwable ->
            ShortcutRepositoryResult.Error(ShortcutRepositoryError.Unknown, throwable)
        }
    }

    override suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): ShortcutRepositoryResult<PinResult> {
        if (!canPinShortcuts) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.PinnedNotSupported)
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
            ShortcutRepositoryResult.Success(result)
        }.getOrElse { throwable ->
            ShortcutRepositoryResult.Error(ShortcutRepositoryError.Unknown, throwable)
        }
    }

    override suspend fun deletePinnedShortcut(shortcutId: String): ShortcutRepositoryResult<Unit> {
        if (!canPinShortcuts) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.PinnedNotSupported)
        }
        if (shortcutId.isBlank()) {
            return ShortcutRepositoryResult.Error(ShortcutRepositoryError.InvalidInput)
        }
        return runCatching {
            ShortcutManagerCompat.disableShortcuts(app, listOf(shortcutId), null)
            ShortcutRepositoryResult.Success(Unit)
        }.getOrElse { throwable ->
            ShortcutRepositoryResult.Error(ShortcutRepositoryError.Unknown, throwable)
        }
    }

    private fun ShortcutInfoCompat.toDraft(defaultServerId: Int, iconIdToName: Map<Int, String>): ShortcutDraft {
        val extras = intent.extras
        val serverId = extras?.getInt(EXTRA_SERVER, defaultServerId) ?: defaultServerId
        val path = extras?.getString(ShortcutIntentKeys.EXTRA_SHORTCUT_PATH) ?: intent.action.orEmpty()

        val selectedIcon = shortcutIntentCodec.parseIcon(extras, iconIdToName)
            ?.let(CommunityMaterial::getIconByMdiName)
        val target = shortcutIntentCodec.parseTarget(extras, path)

        return ShortcutDraft(
            id = id,
            serverId = serverId,
            selectedIcon = selectedIcon,
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
