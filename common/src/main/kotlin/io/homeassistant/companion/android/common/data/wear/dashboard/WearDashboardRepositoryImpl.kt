package io.homeassistant.companion.android.common.data.wear.dashboard

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.model.wearDashboardJson
import io.homeassistant.companion.android.di.qualifiers.NamedWearStorage
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import timber.log.Timber

internal class WearDashboardRepositoryImpl @Inject constructor(
    @NamedWearStorage private val localStorage: LocalStorage,
) : WearDashboardRepository {

    companion object {
        @VisibleForTesting
        const val PREF_WEAR_DASHBOARDS = "wear_dashboards"

        @VisibleForTesting
        const val PREF_WEAR_DASHBOARD_TILES = "wear_dashboard_tiles"

        @VisibleForTesting
        const val UNKNOWN_TILE_ID = -1
    }

    override suspend fun getAllDashboards(): Map<String, WearDashboardConfig> = readDashboards()

    override suspend fun getDashboard(id: String): WearDashboardConfig? = readDashboards()[id]

    override suspend fun setDashboard(config: WearDashboardConfig) {
        val dashboards = readDashboards().toMutableMap()
        dashboards[config.id] = config
        writeDashboards(dashboards)
    }

    override suspend fun setAllDashboards(configs: Map<String, WearDashboardConfig>) {
        writeDashboards(configs)
    }

    override suspend fun removeDashboard(id: String): WearDashboardConfig? {
        val dashboards = readDashboards().toMutableMap()
        val removed = dashboards.remove(id) ?: return null
        writeDashboards(dashboards)
        return removed
    }

    override suspend fun getTileDashboardMapping(): Map<Int, String> = readTileMapping()

    override suspend fun setTileDashboard(tileId: Int, dashboardId: String) {
        val mapping = readTileMapping().toMutableMap()
        mapping[tileId] = dashboardId
        writeTileMapping(mapping)
    }

    override suspend fun removeTileDashboard(tileId: Int) {
        removeDashboardTileAssignment(tileId)
    }

    override suspend fun getDashboardTileAssignmentAndSaveTileId(tileId: Int): String? {
        val mapping = readTileMapping()
        return if (UNKNOWN_TILE_ID in mapping && tileId !in mapping) {
            val dashboardId = mapping[UNKNOWN_TILE_ID] ?: return null
            val updated = mapping.toMutableMap()
            updated.remove(UNKNOWN_TILE_ID)
            updated[tileId] = dashboardId
            writeTileMapping(updated)
            dashboardId
        } else {
            mapping[tileId]
        }
    }

    override suspend fun removeDashboardTileAssignment(tileId: Int) {
        val mapping = readTileMapping().toMutableMap()
        if (mapping.remove(tileId) != null) {
            writeTileMapping(mapping)
        }
    }

    private suspend fun readDashboards(): Map<String, WearDashboardConfig> {
        val json = localStorage.getString(PREF_WEAR_DASHBOARDS) ?: return emptyMap()
        return try {
            wearDashboardJson.decodeFromString<Map<String, WearDashboardConfig>>(json)
        } catch (e: SerializationException) {
            Timber.e(e, "Failed to parse wear dashboards from storage")
            emptyMap()
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Failed to parse wear dashboards from storage")
            emptyMap()
        }
    }

    private suspend fun writeDashboards(configs: Map<String, WearDashboardConfig>) {
        val json = wearDashboardJson.encodeToString(configs)
        localStorage.putString(PREF_WEAR_DASHBOARDS, json)
    }

    private suspend fun readTileMapping(): Map<Int, String> {
        val json = localStorage.getString(PREF_WEAR_DASHBOARD_TILES) ?: return emptyMap()
        return try {
            wearDashboardJson.decodeFromString<Map<String, String>>(json).mapKeys { it.key.toInt() }
        } catch (e: SerializationException) {
            Timber.e(e, "Failed to parse wear dashboard tile mapping from storage")
            emptyMap()
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Failed to parse wear dashboard tile mapping from storage")
            emptyMap()
        }
    }

    private suspend fun writeTileMapping(mapping: Map<Int, String>) {
        val json = wearDashboardJson.encodeToString(mapping.mapKeys { it.key.toString() })
        localStorage.putString(PREF_WEAR_DASHBOARD_TILES, json)
    }
}
