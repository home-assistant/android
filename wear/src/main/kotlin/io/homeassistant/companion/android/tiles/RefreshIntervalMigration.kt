package io.homeassistant.companion.android.tiles

import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.database.wear.CameraTileDao
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Startup migration for devices where refreshing a tile when it becomes visible is not supported
 * anymore ([isRefreshOnViewedSupported]): stored "when viewed" intervals are rewritten to never,
 * so every consumer reads what actually happens.
 */
class RefreshIntervalMigration @Inject constructor(
    private val cameraTileDao: CameraTileDao,
    private val thermostatTileDao: ThermostatTileDao,
    private val wearPrefsRepository: WearPrefsRepository,
) {
    suspend fun migrate() {
        if (isRefreshOnViewedSupported()) return

        cameraTileDao.getAllFlow().first()
            .filter { it.refreshInterval == REFRESH_INTERVAL_ON_VIEWED.toLong() }
            .forEach { cameraTileDao.add(it.copy(refreshInterval = 0)) }

        thermostatTileDao.getAllFlow().first()
            .filter { it.refreshInterval == REFRESH_INTERVAL_ON_VIEWED.toLong() }
            .forEach { thermostatTileDao.add(it.copy(refreshInterval = 0)) }

        wearPrefsRepository.getAllTemplateTiles()
            .filterValues { it.refreshInterval == REFRESH_INTERVAL_ON_VIEWED }
            .forEach { (tileId, config) ->
                wearPrefsRepository.setTemplateTile(tileId, config.template, refreshInterval = 0)
            }
    }
}
