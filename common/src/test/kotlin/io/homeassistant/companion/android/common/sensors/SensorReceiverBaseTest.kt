package io.homeassistant.companion.android.common.sensors

import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.server.Server
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SensorReceiverBaseTest {

    private val basicSensor = SensorManager.BasicSensor(
        id = "test_sensor",
        type = "sensor",
        statelessIcon = "mdi:test",
    )

    private val appVersion = "1.0"
    private val haVersion = "2022.6.0"
    private val serverId = 0

    private lateinit var context: Context
    private lateinit var serverManager: ServerManager
    private lateinit var integrationRepository: IntegrationRepository
    private lateinit var sensorRepository: SensorRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        integrationRepository = mockk(relaxed = true)
        sensorRepository = mockk(relaxed = true)
        serverManager = mockk()

        val server = mockk<Server>()
        every { server.id } returns serverId

        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.servers() } returns listOf(server)
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository

        // Core >= 2022.6 (supports disabled sensors) and trusted, with no per-entity disabled state
        // reported, so the core-driven reconciliation branch stays out of the way.
        coEvery { integrationRepository.getConfig() } returns config()
        coEvery { integrationRepository.getHomeAssistantVersion() } returns haVersion
        coEvery { integrationRepository.isHomeAssistantVersionAtLeast(any(), any(), any()) } returns true
        coEvery { integrationRepository.isTrusted() } returns true
        coEvery { sensorRepository.getEnabledCount() } returns 1
    }

    @Test
    fun `Given a sensor enabled and registered when its runtime permission is revoked then it is disabled in repository and on the server`() = runTest {
        val sensor = enabledRegisteredSensor()
        coEvery { sensorRepository.getFull(basicSensor.id, serverId) } returns mapOf(sensor to emptyList())

        receiver(hasPermission = false).updateSensors(context, serverManager, sensorRepository, intent = null)

        // The missing permission is persisted as disabled.
        assertFalse(sensor.enabled)
        coVerify { sensorRepository.update(sensor) }

        // The server is told the sensor is now disabled.
        val registration = slot<SensorRegistration<Any>>()
        coVerify(exactly = 1) { integrationRepository.registerSensor(capture(registration)) }
        assertTrue(registration.captured.disabled)
    }

    @Test
    fun `Given a sensor enabled and registered when its runtime permission is granted then it stays enabled and is not re-registered`() = runTest {
        val sensor = enabledRegisteredSensor()
        coEvery { sensorRepository.getFull(basicSensor.id, serverId) } returns mapOf(sensor to emptyList())

        receiver(hasPermission = true).updateSensors(context, serverManager, sensorRepository, intent = null)

        assertTrue(sensor.enabled)
        coVerify(exactly = 0) { sensorRepository.update(any()) }
        coVerify(exactly = 0) { integrationRepository.registerSensor(any()) }
    }

    private fun receiver(hasPermission: Boolean) = TestSensorReceiver(
        managers = listOf(FakeSensorManager(listOf(basicSensor), hasPermission)),
        currentAppVersion = appVersion,
    ).apply {
        serverManager = this@SensorReceiverBaseTest.serverManager
        sensorRepository = this@SensorReceiverBaseTest.sensorRepository
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
        appRegistration = appVersion,
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

private class TestSensorReceiver(
    override val managers: List<SensorManager>,
    override val currentAppVersion: String,
) : SensorReceiverBase() {
    override val skippableActions: Map<String, List<String>> = emptyMap()

    override fun getSensorSettingsIntent(
        context: Context,
        sensorId: String,
        sensorManagerId: String,
        notificationId: Int,
    ): PendingIntent? = null
}

private class FakeSensorManager(
    private val sensors: List<SensorManager.BasicSensor>,
    private val hasPermission: Boolean,
) : SensorManager {
    override val name: Int = commonR.string.sensor

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> = emptyArray()

    override suspend fun checkPermission(context: Context, sensorId: String): Boolean = hasPermission

    override suspend fun requestSensorUpdate(context: Context) {}

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> = sensors
}
