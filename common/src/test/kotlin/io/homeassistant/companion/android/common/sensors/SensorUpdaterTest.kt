package io.homeassistant.companion.android.common.sensors

import android.content.Context
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.server.Server
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SensorUpdaterTest {

    private val context = mockk<Context>(relaxed = true)
    private val sensorRepository = mockk<SensorRepository>(relaxed = true)
    private val appVersion = AppVersion.from("1.0", 1)
    private val appVersionProvider = AppVersionProvider { appVersion }
    private val settingsIntentProvider = SensorSettingsIntentProvider { _, _, _, _ -> null }

    private val haVersion = "2022.6.0"
    private val serverId = 0
    private val basicSensor = SensorManager.BasicSensor(
        id = "test_sensor",
        type = "sensor",
        statelessIcon = "mdi:test",
    )

    private fun updater(serverManager: ServerManager, managers: Set<SensorManager>) = SensorUpdater(context, serverManager, sensorRepository, appVersionProvider, managers, settingsIntentProvider)

    @Test
    fun `Given device not registered when updateSensors then no manager is asked to update`() = runTest {
        val serverManager = mockk<ServerManager> {
            coEvery { isRegistered() } returns false
        }
        val manager = mockk<SensorManager>(relaxed = true)

        updater(serverManager, setOf(manager)).updateSensors()

        coVerify(exactly = 0) { manager.requestSensorUpdate(any()) }
    }

    @Test
    fun `Given device registered and no servers when updateSensors then each available manager is asked to update`() = runTest {
        val serverManager = mockk<ServerManager> {
            coEvery { isRegistered() } returns true
            coEvery { servers() } returns emptyList()
        }
        val manager = mockk<SensorManager>(relaxed = true) {
            every { hasSensor() } returns true
        }

        updater(serverManager, setOf(manager)).updateSensors()

        coVerify(exactly = 1) { manager.requestSensorUpdate(null) }
    }

    @Test
    fun `Given a sensor enabled and registered when its runtime permission is revoked then it is disabled and unregistered on the server`() = runTest {
        val sensor = enabledRegisteredSensor()
        val integrationRepository = stubbedIntegrationRepository()
        val serverManager = registeredServerManager(integrationRepository)
        val manager = sensorManager(hasPermission = false)
        coEvery { sensorRepository.getFull(basicSensor.id, serverId) } returns mapOf(sensor to emptyList())

        updater(serverManager, setOf(manager)).updateSensors()

        // The missing permission is persisted as disabled.
        assertFalse(sensor.enabled)
        coVerify { sensorRepository.update(sensor) }

        // The server is told the sensor is now disabled.
        val registration = slot<SensorRegistration<Any>>()
        coVerify(exactly = 1) { integrationRepository.registerSensor(capture(registration)) }
        assertTrue(registration.captured.disabled)
    }

    @Test
    fun `Given a sensor enabled and registered when its runtime permission is granted then it stays enabled and is not unregistered`() = runTest {
        val sensor = enabledRegisteredSensor()
        val integrationRepository = stubbedIntegrationRepository()
        val serverManager = registeredServerManager(integrationRepository)
        val manager = sensorManager(hasPermission = true)
        coEvery { sensorRepository.getFull(basicSensor.id, serverId) } returns mapOf(sensor to emptyList())

        updater(serverManager, setOf(manager)).updateSensors()

        assertTrue(sensor.enabled)
        coVerify(exactly = 0) { sensorRepository.update(any()) }
        coVerify(exactly = 0) { integrationRepository.registerSensor(any()) }
    }

    private fun registeredServerManager(integrationRepository: IntegrationRepository) = mockk<ServerManager> {
        coEvery { isRegistered() } returns true
        coEvery { servers() } returns listOf(mockk<Server>(relaxed = true) { every { id } returns serverId })
        coEvery { integrationRepository(serverId) } returns integrationRepository
    }

    // Core >= 2022.6 (supports disabled sensors) and trusted, with no per-entity disabled state
    // reported, so the core-driven reconciliation branch stays out of the way.
    private fun stubbedIntegrationRepository() = mockk<IntegrationRepository>(relaxed = true) {
        coEvery { getConfig() } returns config()
        coEvery { getHomeAssistantVersion() } returns haVersion
        coEvery { isHomeAssistantVersionAtLeast(any(), any(), any()) } returns true
        coEvery { isTrusted() } returns true
    }

    private fun sensorManager(hasPermission: Boolean) = mockk<SensorManager>(relaxed = true) {
        every { name } returns commonR.string.sensor
        every { hasSensor() } returns true
        coEvery { getAvailableSensors() } returns listOf(basicSensor)
        coEvery { checkPermission(basicSensor.id) } returns hasPermission
    }

    // Already registered and up to date, so only a permission/enabled change drives reconciliation.
    private fun enabledRegisteredSensor() = Sensor(
        id = basicSensor.id,
        serverId = serverId,
        enabled = true,
        registered = true,
        state = "",
        lastSentState = "",
        lastSentIcon = "",
        appRegistration = appVersion.value,
        coreRegistration = haVersion,
    )

    private fun config() = GetConfigResponse(
        latitude = 0.0,
        longitude = 0.0,
        elevation = 0.0,
        unitSystem = emptyMap(),
        locationName = "",
        timeZone = "",
        components = emptyList(),
        version = haVersion,
        entities = null,
    )
}
