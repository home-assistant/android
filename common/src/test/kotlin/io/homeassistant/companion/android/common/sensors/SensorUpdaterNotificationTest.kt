package io.homeassistant.companion.android.common.sensors

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
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
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the missing-permission notification branch of [SensorUpdater]. It lives in its own Robolectric
 * test because building the [androidx.core.app.NotificationCompat] notification needs a real Android
 * runtime, which the plain JVM [SensorUpdaterTest] doesn't provide.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SensorUpdaterNotificationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
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

    @Test
    fun `Given a sensor enabled on core but missing permission when updateSensors then core is overridden and the user is notified`() = runTest {
        val sensor = disabledUnregisteredSensor()
        val integrationRepository = stubbedIntegrationRepository(coreEntities(disabled = false))
        val serverManager = registeredServerManager(integrationRepository)
        val manager = sensorManager(hasPermission = false)
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        coEvery { sensorRepository.getFull(basicSensor.id, serverId) } returns mapOf(sensor to emptyList())

        SensorUpdater(
            context,
            serverManager,
            sensorRepository,
            appVersionProvider,
            setOf(manager),
            settingsIntentProvider,
            notificationManager,
        ).updateSensors()

        // Core wants it enabled but the runtime permission is missing: re-register to override core...
        coVerify(exactly = 1) { integrationRepository.registerSensor(any()) }
        // ...and notify the user so they can grant the permission.
        verify { notificationManager.notify(any<Int>(), any()) }
    }

    private fun registeredServerManager(integrationRepository: IntegrationRepository) = mockk<ServerManager> {
        coEvery { isRegistered() } returns true
        coEvery { servers() } returns listOf(mockk<Server>(relaxed = true) { every { id } returns serverId })
        coEvery { integrationRepository(serverId) } returns integrationRepository
    }

    private fun stubbedIntegrationRepository(entities: Map<String, Map<String, Any>>) = mockk<IntegrationRepository>(relaxed = true) {
        coEvery { getConfig() } returns config(entities)
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

    private fun coreEntities(disabled: Boolean) = mapOf(basicSensor.id to mapOf<String, Any>("disabled" to disabled))

    // Known to the app but disabled and not registered, so a core-driven enable reaches the branch.
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

    private fun config(entities: Map<String, Map<String, Any>>) = GetConfigResponse(
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
}
