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
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServerData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServersResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.database.IconDialogCompat
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
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

    override val maxDynamicShortcuts: Int = MAX_DYNAMIC_SHORTCUTS

    override val canPinShortcuts: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            ShortcutManagerCompat.isRequestPinShortcutSupported(app)
    }

    private val iconIdToName: Map<Int, String> by lazy { IconDialogCompat(app.assets).loadAllIcons() }

    override suspend fun currentServerId(): Int = serverManager.getServer()?.id ?: 0

    override suspend fun getServers(): ServersResult {
        val servers = serverManager.servers()
        if (servers.isEmpty()) return ServersResult.NoServers
        val currentId = currentServerId()
        val defaultId = servers.firstOrNull { it.id == currentId }?.id ?: servers.first().id
        return ServersResult.Success(servers, defaultId)
    }

    override suspend fun loadServerData(serverId: Int): ServerData = coroutineScope {
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

    override suspend fun loadDynamicShortcuts(): Map<Int, ShortcutDraft> {
        val shortcuts = ShortcutManagerCompat.getShortcuts(
            app,
            ShortcutManagerCompat.FLAG_MATCH_DYNAMIC,
        )

        val defaultServerId = currentServerId()

        return buildMap {
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
    }

    override suspend fun loadPinnedShortcuts(): List<ShortcutDraft> {
        if (!canPinShortcuts) return emptyList()
        val pinnedShortcuts = ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .filter { !it.id.startsWith(ASSIST_SHORTCUT_PREFIX) }
        val defaultServerId = currentServerId()
        return pinnedShortcuts.map { it.toDraft(defaultServerId, iconIdToName) }
    }

    override suspend fun upsertDynamicShortcut(index: Int, shortcut: ShortcutDraft) {
        val normalized = shortcut.copy(id = DynamicShortcutId.build(index))
        val shortcutInfo = shortcutFactory.createShortcutInfo(normalized)
        ShortcutManagerCompat.addDynamicShortcuts(app, listOf(shortcutInfo))
    }

    override suspend fun deleteDynamicShortcut(index: Int) {
        ShortcutManagerCompat.removeDynamicShortcuts(
            app,
            listOf(DynamicShortcutId.build(index)),
        )
    }

    override suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): PinResult {
        if (!canPinShortcuts) return PinResult.NotSupported
        val normalized = if (shortcut.id.isBlank()) {
            shortcut.copy(id = newPinnedId())
        } else {
            shortcut
        }
        val shortcutInfo = shortcutFactory.createShortcutInfo(normalized)
        val pinnedShortcuts = ShortcutManagerCompat.getShortcuts(
            app,
            ShortcutManagerCompat.FLAG_MATCH_PINNED,
        ).filter { !it.id.startsWith(ASSIST_SHORTCUT_PREFIX) }

        val exists = pinnedShortcuts.any { it.id == normalized.id }
        return if (exists) {
            Timber.d("Updating pinned shortcut: ${normalized.id}")
            ShortcutManagerCompat.updateShortcuts(app, listOf(shortcutInfo))
            PinResult.Updated
        } else {
            Timber.d("Requesting pin for shortcut: ${normalized.id}")
            ShortcutManagerCompat.requestPinShortcut(app, shortcutInfo, null)
            PinResult.Requested
        }
    }

    override suspend fun deletePinnedShortcut(shortcutId: String) {
        if (!canPinShortcuts) return
        if (shortcutId.isBlank()) return
        ShortcutManagerCompat.disableShortcuts(app, listOf(shortcutId), null)
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
            isDirty = false,
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
}
