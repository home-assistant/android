package io.homeassistant.companion.android.common.sensors

import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.server.ServerDao
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import timber.log.Timber

/**
 * [SensorRepository] backed by [SensorDao], with [ServerDao] providing the configured servers and
 * [basicSensors] the sensor definitions used to fill in defaults. See [SensorRepository] for the behavior.
 */
internal class SensorRepositoryImpl @Inject constructor(
    private val dao: SensorDao,
    private val serverDao: ServerDao,
    basicSensors: Set<@JvmSuppressWildcards SensorManager.BasicSensor>,
) : SensorRepository {

    // Sensor enabled-by-default per sensor id, used to synthesize a Sensor when no row exists.
    private val enabledByDefaultById: Map<String, Boolean> = basicSensors.associate { it.id to it.enabledByDefault }

    // This could in theory return orphan sensors for removed servers where the DB was not cleared properly
    override suspend fun get(id: String): List<Sensor> = sensorsByServer(id, dao.get(id), configuredServerIds())

    override suspend fun get(id: String, serverId: Int): Sensor? = dao.get(id, serverId) ?: defaultSensor(id, serverId)

    override fun getAllFlow(): Flow<List<Sensor>> = combine(dao.getAllFlow(), serverDao.getAllFlow()) { rows, servers ->
        withDefaults(rows, servers.map { it.id })
    }

    override suspend fun getFull(id: String): Map<Sensor, List<Attribute>> =
        fullByServer(id, dao.getFull(id), configuredServerIds())

    override suspend fun getFull(id: String, serverId: Int): Map<Sensor, List<Attribute>> {
        val existing = dao.getFull(id, serverId)
        if (existing.isNotEmpty()) return existing
        val default = defaultSensor(id, serverId) ?: return emptyMap()
        return mapOf(default to emptyList())
    }

    override fun getFullFlow(id: String): Flow<Map<Sensor, List<Attribute>>> =
        combine(dao.getFullFlow(id), serverDao.getAllFlow()) { existing, servers ->
            fullByServer(id, existing, servers.map { it.id })
        }

    // A default-enabled sensor counts even before it has a stored row, so this is the
    // count of effective-enabled (sensor, server) pairs, not just persisted enabled rows. Rather than
    // synthesizing a full per-server list, start from the default-enabled count for every configured
    // server and treat each stored row as an override that adjusts its sensor's default contribution.
    override suspend fun getEnabledCount(): Int {
        val serverIds = configuredServerIds().toSet()
        val defaultEnabledCount = enabledByDefaultById.count { (_, enabledByDefault) -> enabledByDefault }
        var count = serverIds.size * defaultEnabledCount
        for (row in dao.getAll()) {
            // Skip rows that don't contribute to the effective count: sensors that aren't annotated
            // don't exist, and rows for removed (non-configured) servers must not be counted.
            val enabledByDefault = enabledByDefaultById[row.id] ?: continue
            if (row.serverId !in serverIds) continue
            if (row.enabled != enabledByDefault) {
                count += if (row.enabled) 1 else -1
            }
        }
        return count
    }

    override suspend fun update(sensor: Sensor) = dao.upsert(sensor)

    override suspend fun setSensorEnabled(sensorId: String, serverIds: List<Int>, enabled: Boolean) =
        dao.setSensorEnabled(sensorId, serverIds, enabled)

    override suspend fun setSensorsEnabled(sensorIds: List<String>, serverId: Int, enabled: Boolean) =
        dao.setSensorsEnabled(sensorIds, serverId, enabled)

    override suspend fun updateLastSentStateAndIcon(sensorId: String, serverId: Int, state: String?, icon: String?) =
        dao.updateLastSentStateAndIcon(sensorId, serverId, state, icon)

    override suspend fun updateLastSentStatesAndIcons(sensorId: String, state: String?, icon: String?) =
        dao.updateLastSentStatesAndIcons(sensorId, state, icon)

    override suspend fun removeServer(serverId: Int) = dao.removeServer(serverId)

    override suspend fun removeSensorsExceptServers(serverIds: List<Int>) {
        val toRemove = dao.getAllExceptServer(serverIds)
        if (toRemove.isEmpty()) return
        Timber.i("Cleaning up ${toRemove.size} sensor entries")
        toRemove.forEach { dao.removeSensor(it.id, it.serverId) }
    }

    override suspend fun add(attribute: Attribute) = dao.add(attribute)
    override suspend fun add(attributes: List<Attribute>) = dao.add(attributes)
    override suspend fun replaceAllAttributes(sensorId: String, attributes: List<Attribute>) =
        dao.replaceAllAttributes(sensorId, attributes)

    override suspend fun getSettings(id: String) = dao.getSettings(id)
    override fun getSettingsFlow(id: String) = dao.getSettingsFlow(id)
    override suspend fun add(sensorSetting: SensorSetting) = dao.add(sensorSetting)
    override suspend fun updateSettingEnabled(sensorId: String, settingName: String, enabled: Boolean) =
        dao.updateSettingEnabled(sensorId, settingName, enabled)
    override suspend fun updateSettingValue(sensorId: String, settingName: String, value: String) =
        dao.updateSettingValue(sensorId, settingName, value)
    override suspend fun removeSetting(sensorId: String, settingName: String) = dao.removeSetting(sensorId, settingName)
    override suspend fun removeSettings(sensorId: String, settingNames: List<String>) =
        dao.removeSettings(sensorId, settingNames)

    private suspend fun configuredServerIds(): List<Int> = serverDao.getAll().map { it.id }

    /**
     * In-memory default state for ([id], [serverId]), or `null` when [id] has no backing
     * `BasicSensor` - meaning the sensor does not exist. That should never happen for
     * a sensor the app actually queries, so it is also surfaced via [FailFast].
     */
    private fun defaultSensor(id: String, serverId: Int): Sensor? {
        val enabledByDefault = enabledByDefaultById[id]
        if (enabledByDefault == null) {
            FailFast.fail { "No BasicSensor defined for sensor id=$id" }
            return null
        }
        return Sensor(id = id, serverId = serverId, enabled = enabledByDefault, state = "")
    }

    /**
     * Row-or-default state for [id], one entry per server in [serverIds] plus any server that
     * already has a stored row, using [rows] where present and the sensor definition otherwise. Empty
     * when [id] isn't a known sensor (no rows and no defined sensor).
     */
    private fun sensorsByServer(id: String, rows: List<Sensor>, serverIds: Collection<Int>): List<Sensor> {
        val byServer = rows.associateBy { it.serverId }
        return (serverIds + byServer.keys).toSet()
            .mapNotNull { serverId -> byServer[serverId] ?: defaultSensor(id, serverId) }
    }

    /** [sensorsByServer] paired with each sensor's attributes (empty for a defaulted sensor). */
    private fun fullByServer(
        id: String,
        existing: Map<Sensor, List<Attribute>>,
        serverIds: Collection<Int>,
    ): Map<Sensor, List<Attribute>> = sensorsByServer(id, existing.keys.toList(), serverIds)
        .associateWith { sensor -> existing[sensor] ?: emptyList() }

    /**
     * Every defined sensor across [serverIds], as its stored row in [rows] when present or a the
     * default otherwise. Stored rows for undefined sensors are **not** surfaced - only defined
     * sensors exist.
     */
    private fun withDefaults(rows: List<Sensor>, serverIds: Collection<Int>): List<Sensor> {
        val byKey = rows.associateBy { it.id to it.serverId }
        return serverIds.flatMap { serverId ->
            enabledByDefaultById.keys.mapNotNull { id -> byKey[id to serverId] ?: defaultSensor(id, serverId) }
        }
    }
}
