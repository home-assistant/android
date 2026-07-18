package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SETTING_ALLOW_LIST = "notification_allow_list"
private const val SETTING_DISABLE_ALLOW_LIST = "notification_disable_allow_list"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class NotificationListenerSensorManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sensorRepository: SensorRepository = mockk(relaxed = true)
    private val manager = NotificationListenerSensorManager(
        context,
        sensorRepository,
        mockk<ServerManager>(relaxed = true),
    )

    @After
    fun tearDown() {
        unmockkStatic(NotificationManagerCompat::class)
    }

    @Test
    fun `Given last notification enabled when requesting update then its settings are initialized`() = runTest {
        prepareEnabledNotificationSensor(NotificationListenerSensorManager.lastNotification.id)
        coEvery { sensorRepository.getSettings(any()) } returns emptyList()
        val addedSettings = captureAddedSettings()

        manager.requestSensorUpdate()

        assertEquals(
            defaultSettings(NotificationListenerSensorManager.lastNotification.id),
            addedSettings,
        )
    }

    @Test
    fun `Given last removed notification enabled when requesting update then its settings are initialized`() = runTest {
        prepareEnabledNotificationSensor(NotificationListenerSensorManager.lastRemovedNotification.id)
        coEvery { sensorRepository.getSettings(any()) } returns emptyList()
        val addedSettings = captureAddedSettings()

        manager.requestSensorUpdate()

        assertEquals(
            defaultSettings(NotificationListenerSensorManager.lastRemovedNotification.id),
            addedSettings,
        )
    }

    @Test
    fun `Given custom allow list when requesting update then existing value is preserved`() = runTest {
        val sensorId = NotificationListenerSensorManager.lastNotification.id
        val existingSetting = SensorSetting(
            sensorId = sensorId,
            name = SETTING_ALLOW_LIST,
            value = "com.example.app",
            valueType = SensorSettingType.LIST_APPS,
        )
        prepareEnabledNotificationSensor(sensorId)
        coEvery { sensorRepository.getSettings(sensorId) } returns listOf(existingSetting)
        val addedSettings = captureAddedSettings()

        manager.requestSensorUpdate()

        assertEquals(
            listOf(
                SensorSetting(
                    sensorId = sensorId,
                    name = SETTING_DISABLE_ALLOW_LIST,
                    value = "false",
                    valueType = SensorSettingType.TOGGLE,
                ),
            ),
            addedSettings,
        )
    }

    private fun prepareEnabledNotificationSensor(sensorId: String) {
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.getEnabledListenerPackages(context) } returns setOf(context.packageName)
        coEvery { sensorRepository.get(any()) } returns emptyList()
        coEvery { sensorRepository.get(sensorId) } returns listOf(
            Sensor(
                id = sensorId,
                serverId = 1,
                enabled = true,
                state = "",
            ),
        )
    }

    private fun captureAddedSettings(): MutableList<SensorSetting> {
        val addedSettings = mutableListOf<SensorSetting>()
        coEvery { sensorRepository.add(any<SensorSetting>()) } answers {
            addedSettings += firstArg<SensorSetting>()
        }
        return addedSettings
    }

    private fun defaultSettings(sensorId: String) = listOf(
        SensorSetting(
            sensorId = sensorId,
            name = SETTING_ALLOW_LIST,
            value = "",
            valueType = SensorSettingType.LIST_APPS,
        ),
        SensorSetting(
            sensorId = sensorId,
            name = SETTING_DISABLE_ALLOW_LIST,
            value = "false",
            valueType = SensorSettingType.TOGGLE,
        ),
    )
}
