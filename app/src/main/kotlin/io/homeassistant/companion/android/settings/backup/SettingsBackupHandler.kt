package io.homeassistant.companion.android.settings.backup

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.backup.BackupEntityReference
import io.homeassistant.companion.android.common.data.backup.BackupSections
import io.homeassistant.companion.android.common.data.backup.BackupServerData
import io.homeassistant.companion.android.common.data.backup.BackupSettingsRepository
import io.homeassistant.companion.android.common.data.backup.SETTINGS_BACKUP_FORMAT
import io.homeassistant.companion.android.common.data.backup.SETTINGS_BACKUP_VERSION
import io.homeassistant.companion.android.common.data.backup.SettingsBackupData
import io.homeassistant.companion.android.common.data.backup.select
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Coordinates portable configuration with the phone's current servers and sensor capabilities. */
@Singleton
internal class SettingsBackupHandler @VisibleForTesting constructor(
    private val serverManager: ServerManager,
    private val sensorManager: BackupSensorManager,
    private val settingsRepository: BackupSettingsRepository,
    private val prefsRepository: PrefsRepository,
    private val clock: Clock,
    private val backgroundDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        serverManager: ServerManager,
        sensorManager: BackupSensorManager,
        settingsRepository: BackupSettingsRepository,
        prefsRepository: PrefsRepository,
        clock: Clock,
    ) : this(serverManager, sensorManager, settingsRepository, prefsRepository, clock, Dispatchers.Default)

    private val restoreMutex = Mutex()

    suspend fun destinations(): List<BackupDestination> = serverManager.servers().map {
        BackupDestination(it.id, it.friendlyName)
    }

    suspend fun export(sections: BackupSections): SettingsBackupData = withContext(backgroundDispatcher) {
        val servers = serverManager.servers()
        val references = servers.mapIndexed { index, server -> server.id to "server-$index" }.toMap()
        val capabilities = sensorManager.capabilities()
        val sensors = sensorManager.selections()
        val settings = settingsRepository.getSettings().associateBy { it.id }
        SettingsBackupData(
            format = SETTINGS_BACKUP_FORMAT,
            schemaVersion = SETTINGS_BACKUP_VERSION,
            appVersion = BuildConfig.VERSION_NAME,
            createdAt = clock.now(),
            servers = servers.map { server ->
                BackupServerData(
                    reference = references.getValue(server.id),
                    name = server.nameOverride ?: server._name.ifBlank { "Home Assistant" },
                    sensors = sensors[server.id].orEmpty().filter { it.id in capabilities }
                        .associate { it.id to it.enabled },
                    persistentConnection = (
                        settings[server.id]?.websocketSetting
                            ?: if (BuildConfig.FLAVOR == "full") WebsocketSetting.NEVER else WebsocketSetting.ALWAYS
                        ).name,
                )
            },
            favorites = prefsRepository.getAutoFavorites().mapNotNull { favorite ->
                references[favorite.serverId]?.let { BackupEntityReference(it, favorite.entityId) }
            },
            sensorOptions = sensorManager.options(capabilities, references),
            sensorUpdateFrequency = (
                settings[APP_SETTINGS_ID]?.sensorUpdateFrequency
                    ?: SensorUpdateFrequencySetting.NORMAL
                ).name,
        ).select(sections)
    }

    suspend fun prepare(backup: SettingsBackupData, mapping: Map<String, Int>): SettingsRestorePlan =
        withContext(backgroundDispatcher) {
            prepareSettingsRestore(backup, mapping, destinations(), sensorManager.capabilities())
        }

    suspend fun restore(backup: SettingsBackupData, mapping: Map<String, Int>, preview: SettingsRestorePlan) {
        restoreMutex.withLock {
            val current = prepare(backup, mapping)
            check(current == preview) { "Destination settings capabilities changed; review the restore again" }
            val oldFavorites = prefsRepository.getAutoFavorites()
            val favorites = current.favorites?.let { restored ->
                oldFavorites.filterNot { it.serverId in current.changes.serverIds } + restored
            }
            // Room rolls back database changes on failure. Compensate the separate preferences write
            // as well; lifecycle cancellation must not interrupt this short commit/rollback sequence.
            withContext(NonCancellable) {
                var committed = false
                try {
                    favorites?.let { prefsRepository.setAutoFavorites(it) }
                    settingsRepository.apply(current.changes)
                    committed = true
                } finally {
                    if (!committed && favorites != null) prefsRepository.setAutoFavorites(oldFavorites)
                }
            }
        }
    }
}

private const val APP_SETTINGS_ID = 0
