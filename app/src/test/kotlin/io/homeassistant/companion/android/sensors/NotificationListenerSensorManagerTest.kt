package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

private const val SETTING_ALLOW_LIST = "notification_allow_list"
private const val SETTING_DISABLE_ALLOW_LIST = "notification_disable_allow_list"

class NotificationListenerSensorManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val sensorRepository: SensorRepository = mockk(relaxed = true)
    private val manager = NotificationListenerSensorManager(
        context,
        sensorRepository,
        mockk<ServerManager>(relaxed = true),
    )

    @ParameterizedTest
    @MethodSource("sensorIds")
    fun `Given notification sensor when inspected then its settings are declared`(
        sensorId: String,
    ) {
        assertEquals(
            defaultSettings(),
            notificationSensor(sensorId).settings,
        )
    }

    @Test
    fun `Given active notification count sensor when inspected then content setting is declared`() {
        assertEquals(
            listOf(
                SensorManager.BasicSensor.Setting(
                    name = "active_notification_count_content_attrs",
                    type = SensorSettingType.TOGGLE,
                    defaultValue = "true",
                ),
            ),
            NotificationListenerSensorManager.activeNotificationCount.settings,
        )
    }

    @Test
    fun `Given notification refresh when requested then settings are not persisted`() = runTest {
        manager.requestSensorUpdate()

        coVerify(exactly = 0) {
            sensorRepository.updateSettingValue(any(), any(), any())
            sensorRepository.addDynamicSetting(any())
        }
    }

    companion object {
        @JvmStatic
        fun sensorIds() = listOf(
            NotificationListenerSensorManager.lastNotification.id,
            NotificationListenerSensorManager.lastRemovedNotification.id,
        )
    }

    private fun defaultSettings() = listOf(
        SensorManager.BasicSensor.Setting(
            name = SETTING_ALLOW_LIST,
            type = SensorSettingType.LIST_APPS,
            defaultValue = "",
        ),
        SensorManager.BasicSensor.Setting(
            name = SETTING_DISABLE_ALLOW_LIST,
            type = SensorSettingType.TOGGLE,
            defaultValue = "false",
        ),
    )

    private fun notificationSensor(sensorId: String) = when (sensorId) {
        NotificationListenerSensorManager.lastNotification.id -> NotificationListenerSensorManager.lastNotification
        NotificationListenerSensorManager.lastRemovedNotification.id ->
            NotificationListenerSensorManager.lastRemovedNotification
        else -> error("Unknown notification sensor: $sensorId")
    }
}
