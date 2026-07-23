package io.homeassistant.companion.android.common.sensors

import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorSetting
import kotlinx.coroutines.flow.Flow

/**
 * Single access point to sensors, their state, settings and attributes.
 *
 * A known sensor always has a state: reads fall back to its default when nothing has been set yet,
 * so they never come back empty for a real sensor. There is no separate create step — [update] sets
 * a sensor's state on demand.
 */
interface SensorRepository {

    /** State of sensor [id] on every server the state set for it. */
    suspend fun get(id: String): List<Sensor>

    /**
     * State of sensor [id] on [serverId]: the state set for it. `null` only when [id] isn't a
     * real sensor.
     */
    suspend fun get(id: String, serverId: Int): Sensor?

    /**
     * Reactive state of every known sensor on every server the state set for it.
     * Re-emits when any sensor's state or the set of servers changes.
     */
    fun getAllFlow(): Flow<List<Sensor>>

    /** [get] together with each sensor's attributes. */
    suspend fun getFull(id: String): Map<Sensor, List<Attribute>>

    /** [get] together with attributes for one server. */
    suspend fun getFull(id: String, serverId: Int): Map<Sensor, List<Attribute>>

    /** Reactive form of [getFull], re-emitting when the sensor's state or the set of servers changes. */
    fun getFullFlow(id: String): Flow<Map<Sensor, List<Attribute>>>

    /** Number of enabled (sensor, server) pairs, counting defaults. */
    suspend fun getEnabledCount(): Int

    /** Update [sensor]'s state. */
    suspend fun update(sensor: Sensor)

    /** Enables or disables sensor [sensorId] on each of [serverIds]. */
    suspend fun setSensorEnabled(sensorId: String, serverIds: List<Int>, enabled: Boolean)

    /** Enables or disables each of [sensorIds] on [serverId]. */
    suspend fun setSensorsEnabled(sensorIds: List<String>, serverId: Int, enabled: Boolean)

    /** Records the last state and icon sent to [serverId] for sensor [sensorId]. */
    suspend fun updateLastSentStateAndIcon(sensorId: String, serverId: Int, state: String?, icon: String?)

    /** Records the last sent state and icon for sensor [sensorId] across all servers. */
    suspend fun updateLastSentStatesAndIcons(sensorId: String, state: String?, icon: String?)

    /** Forgets all data for the sensors of [serverId]. */
    suspend fun removeServer(serverId: Int)

    /** Remove the persisted state of sensors for any server not in [serverIds] */
    suspend fun removeSensorsExceptServers(serverIds: List<Int>)

    /** Adds a single sensor [attribute], replacing any existing one with the same name. */
    suspend fun add(attribute: Attribute)

    /** Adds the given sensor [attributes], replacing any existing ones with the same name. */
    suspend fun add(attributes: List<Attribute>)

    /** Replaces all attributes of sensor [sensorId] with [attributes]. */
    suspend fun replaceAllAttributes(sensorId: String, attributes: List<Attribute>)

    /** Settings of sensor [id]. */
    suspend fun getSettings(id: String): List<SensorSetting>

    /** Reactive settings of sensor [id]. */
    fun getSettingsFlow(id: String): Flow<List<SensorSetting>>

    /** Adds a sensor [sensorSetting], replacing any existing one with the same name. */
    suspend fun add(sensorSetting: SensorSetting)

    /** Enables or disables setting [settingName] of sensor [sensorId]. */
    suspend fun updateSettingEnabled(sensorId: String, settingName: String, enabled: Boolean)

    /** Sets the value of setting [settingName] of sensor [sensorId]. */
    suspend fun updateSettingValue(sensorId: String, settingName: String, value: String)

    /** Removes setting [settingName] from sensor [sensorId]. */
    suspend fun removeSetting(sensorId: String, settingName: String)

    /** Removes settings [settingNames] from sensor [sensorId]. */
    suspend fun removeSettings(sensorId: String, settingNames: List<String>)
}
