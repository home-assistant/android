package io.homeassistant.companion.android.util

import android.content.Context
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.sensors.BatterySensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.TemporaryServer
import kotlinx.coroutines.flow.Flow

/**
 * Builds a real [BatterySensorManager] for `@Preview` composables. The repository and server
 * dependencies are throwing stubs: the battery previews only call `getAvailableSensors`,
 * `requiredPermissions` and `checkPermission`, none of which touch them.
 */
fun batterySensorManager(context: Context): BatterySensorManager =
    BatterySensorManager(context, PreviewSensorRepository, PreviewServerManager)

private object PreviewSensorRepository : SensorRepository {
    override suspend fun get(id: String): List<Sensor> = error("preview-only")
    override suspend fun get(id: String, serverId: Int): Sensor? = error("preview-only")
    override fun getAllFlow(): Flow<List<Sensor>> = error("preview-only")
    override suspend fun getFull(id: String): Map<Sensor, List<Attribute>> = error("preview-only")
    override suspend fun getFull(id: String, serverId: Int): Map<Sensor, List<Attribute>> = error("preview-only")
    override fun getFullFlow(id: String): Flow<Map<Sensor, List<Attribute>>> = error("preview-only")
    override suspend fun getEnabledCount(): Int = error("preview-only")
    override suspend fun update(sensor: Sensor) = error("preview-only")
    override suspend fun setSensorEnabled(sensorId: String, serverIds: List<Int>, enabled: Boolean) =
        error("preview-only")
    override suspend fun setSensorsEnabled(sensorIds: List<String>, serverId: Int, enabled: Boolean) =
        error("preview-only")
    override suspend fun updateLastSentStateAndIcon(sensorId: String, serverId: Int, state: String?, icon: String?) =
        error("preview-only")
    override suspend fun updateLastSentStatesAndIcons(sensorId: String, state: String?, icon: String?) =
        error("preview-only")
    override suspend fun removeServer(serverId: Int) = error("preview-only")
    override suspend fun removeSensorsExceptServers(serverIds: List<Int>) = error("preview-only")
    override suspend fun add(attribute: Attribute) = error("preview-only")
    override suspend fun add(attributes: List<Attribute>) = error("preview-only")
    override suspend fun replaceAllAttributes(sensorId: String, attributes: List<Attribute>) = error("preview-only")
    override suspend fun getSettings(id: String): List<SensorSetting> = error("preview-only")
    override fun getSettingsFlow(id: String): Flow<List<SensorSetting>> = error("preview-only")
    override suspend fun add(sensorSetting: SensorSetting) = error("preview-only")
    override suspend fun updateSettingEnabled(sensorId: String, settingName: String, enabled: Boolean) =
        error("preview-only")
    override suspend fun updateSettingValue(sensorId: String, settingName: String, value: String) =
        error("preview-only")
    override suspend fun removeSetting(sensorId: String, settingName: String) = error("preview-only")
    override suspend fun removeSettings(sensorId: String, settingNames: List<String>) = error("preview-only")
}

private object PreviewServerManager : ServerManager {
    override suspend fun servers(): List<Server> = error("preview-only")
    override suspend fun isRegistered(): Boolean = error("preview-only")
    override suspend fun addServer(server: TemporaryServer): Int = error("preview-only")
    override suspend fun getServer(id: Int): Server? = error("preview-only")
    override suspend fun getServer(webhookId: String): Server? = error("preview-only")
    override suspend fun activateServer(id: Int) = error("preview-only")
    override suspend fun updateServer(server: Server) = error("preview-only")
    override suspend fun removeServer(id: Int) = error("preview-only")
    override suspend fun authenticationRepository(serverId: Int): AuthenticationRepository = error("preview-only")
    override suspend fun integrationRepository(serverId: Int): IntegrationRepository = error("preview-only")
    override suspend fun webSocketRepository(serverId: Int): WebSocketRepository = error("preview-only")
    override suspend fun connectionStateProvider(serverId: Int): ServerConnectionStateProvider = error("preview-only")
    override val serversFlow: Flow<List<Server>> get() = error("preview-only")
}
