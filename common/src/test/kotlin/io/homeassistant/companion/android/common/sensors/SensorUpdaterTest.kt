package io.homeassistant.companion.android.common.sensors

import android.app.NotificationManager
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

    private fun updater(
        serverManager: ServerManager,
        managers: Set<SensorManager>,
        notificationManager: NotificationManager = mockk(relaxed = true),
    ) = SensorUpdater(
        context,
        serverManager,
        sensorRepository,
        appVersionProvider,
        managers,
        settingsIntentProvider,
        notificationManager,
    )

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
        val sensorUpdateCaptured = mutableListOf<Sensor>()
        coVerify { sensorRepository.update(capture(sensorUpdateCaptured)) }
        assertTrue(sensorUpdateCaptured.any { !it.enabled })

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

    @Test
    fun `Given a new enabled sensor with a changed state when updateSensors runs then its state is sent to the server in the same pass`() = runTest {
        val sensor = newEnabledSensor()
        val integrationRepository = stubbedIntegrationRepository()
        val serverManager = registeredServerManager(integrationRepository)
        val manager = sensorManager(hasPermission = true)
        coEvery { sensorRepository.getFull(basicSensor.id, serverId) } returns mapOf(sensor to emptyList())

        updater(serverManager, setOf(manager)).updateSensors()

        // The sensor is registered with core...
        coVerify(exactly = 1) { integrationRepository.registerSensor(any()) }
        // ...and its state is sent in the same pass, because the just-registered state is visible downstream.
        coVerify(exactly = 1) { integrationRepository.updateSensors(any()) }
    }

    @Test
    fun `Given a disabled sensor enabled on core with permission when updateSensors then it is enabled and registered locally`() = runTest {
        val sensor = disabledUnregisteredSensor()
        val integrationRepository = stubbedIntegrationRepository(config(entities = coreEntities(disabled = false)))
        val serverManager = registeredServerManager(integrationRepository)
        val manager = sensorManager(hasPermission = true)
        coEvery { sensorRepository.getFull(basicSensor.id, serverId) } returns mapOf(sensor to emptyList())

        updater(serverManager, setOf(manager)).updateSensors()

        val sensorUpdateCaptured = mutableListOf<Sensor>()
        coVerify { sensorRepository.update(capture(sensorUpdateCaptured)) }
        assertTrue(sensorUpdateCaptured.any { it.enabled && it.registered == true })
        // App state now matches core directly, so there is no need to re-register/override on the server.
        coVerify(exactly = 0) { integrationRepository.registerSensor(any()) }
    }

    @Test
    fun `Given an enabled sensor disabled on core when updateSensors then it is disabled locally`() = runTest {
        val sensor = enabledRegisteredSensor()
        val integrationRepository = stubbedIntegrationRepository(config(entities = coreEntities(disabled = true)))
        val serverManager = registeredServerManager(integrationRepository)
        val manager = sensorManager(hasPermission = true)
        coEvery { sensorRepository.getFull(basicSensor.id, serverId) } returns mapOf(sensor to emptyList())

        updater(serverManager, setOf(manager)).updateSensors()

        val sensorUpdateCaptured = mutableListOf<Sensor>()
        coVerify { sensorRepository.update(capture(sensorUpdateCaptured)) }
        assertTrue(sensorUpdateCaptured.any { !it.enabled && it.registered == false })
        coVerify(exactly = 0) { integrationRepository.registerSensor(any()) }
    }

    @Test
    fun `Given core enables a sensor but the server is untrusted when updateSensors then the app state is kept and core is overridden`() = runTest {
        val sensor = disabledUnregisteredSensor()
        val integrationRepository = stubbedIntegrationRepository(config(entities = coreEntities(disabled = false)), trusted = false)
        val serverManager = registeredServerManager(integrationRepository)
        val manager = sensorManager(hasPermission = true)
        // Untrusted servers only reach the reconciliation branch when at least one sensor is enabled.
        coEvery { sensorRepository.getEnabledCount() } returns 1
        coEvery { sensorRepository.getFull(basicSensor.id, serverId) } returns mapOf(sensor to emptyList())

        updater(serverManager, setOf(manager)).updateSensors()

        // The app doesn't trust the server to flip the state, so it re-registers to override core...
        coVerify(exactly = 1) { integrationRepository.registerSensor(any()) }
        // ...and keeps the sensor disabled locally instead of enabling it to match core.
        val sensorUpdateCaptured = mutableListOf<Sensor>()
        coVerify { sensorRepository.update(capture(sensorUpdateCaptured)) }
        assertTrue(sensorUpdateCaptured.any { !it.enabled && it.registered == false })
    }

    @Test
    fun `Given core disables a sensor but the server is untrusted when updateSensors then the app state is kept and core is overridden`() = runTest {
        val sensor = enabledRegisteredSensor()
        val integrationRepository = stubbedIntegrationRepository(config(entities = coreEntities(disabled = true)), trusted = false)
        val serverManager = registeredServerManager(integrationRepository)
        val manager = sensorManager(hasPermission = true)
        coEvery { sensorRepository.getEnabledCount() } returns 1
        coEvery { sensorRepository.getFull(basicSensor.id, serverId) } returns mapOf(sensor to emptyList())

        updater(serverManager, setOf(manager)).updateSensors()

        // The app doesn't trust the server to flip the state, so it re-registers to override core...
        coVerify(exactly = 1) { integrationRepository.registerSensor(any()) }
        // ...and keeps the sensor enabled locally instead of disabling it to match core.
        val sensorUpdateCaptured = mutableListOf<Sensor>()
        coVerify { sensorRepository.update(capture(sensorUpdateCaptured)) }
        assertTrue(sensorUpdateCaptured.any { it.enabled && it.registered == true })
    }

    private fun registeredServerManager(integrationRepository: IntegrationRepository) = mockk<ServerManager> {
        coEvery { isRegistered() } returns true
        coEvery { servers() } returns listOf(mockk<Server>(relaxed = true) { every { id } returns serverId })
        coEvery { integrationRepository(serverId) } returns integrationRepository
    }

    // Core >= 2022.6 (supports disabled sensors) and trusted by default. No per-entity disabled state is
    // reported unless a [config] with entities is passed; set [trusted] to false to simulate a server that
    // isn't allowed to remotely control the app.
    private fun stubbedIntegrationRepository(
        config: GetConfigResponse = config(),
        trusted: Boolean = true,
    ) = mockk<IntegrationRepository>(relaxed = true) {
        coEvery { getConfig() } returns config
        coEvery { getHomeAssistantVersion() } returns haVersion
        coEvery { isHomeAssistantVersionAtLeast(any(), any(), any()) } returns true
        coEvery { isTrusted() } returns trusted
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

    // Newly discovered sensor, enabled by the user but not yet registered, with a state that hasn't been sent.
    private fun newEnabledSensor() = Sensor(
        id = basicSensor.id,
        serverId = serverId,
        enabled = true,
        registered = null,
        state = "on",
        lastSentState = null,
        lastSentIcon = null,
        appRegistration = appVersion.value,
        coreRegistration = haVersion,
    )

    private fun config(entities: Map<String, Map<String, Any>>? = null) = GetConfigResponse(
        latitude = 0.0,
        longitude = 0.0,
        elevation = 0.0,
        unitSystem = emptyMap(),
        locationName = "",
        timeZone = "",
        components = emptyList(),
        version = haVersion,
        entities = entities,
    )

    // Mimics core reporting a per-entity disabled flag, which drives the core-state reconciliation branch.
    private fun coreEntities(disabled: Boolean) = mapOf(basicSensor.id to mapOf<String, Any>("disabled" to disabled))

    // Known to the app but disabled and not registered, so a core-driven enable can reconcile it.
    private fun disabledUnregisteredSensor() = Sensor(
        id = basicSensor.id,
        serverId = serverId,
        enabled = false,
        registered = false,
        state = "",
        lastSentState = "",
        lastSentIcon = "",
        appRegistration = appVersion.value,
        coreRegistration = haVersion,
    )
}
