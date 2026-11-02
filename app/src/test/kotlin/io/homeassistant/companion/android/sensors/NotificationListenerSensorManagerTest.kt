package io.homeassistant.companion.android.sensors

import android.app.Notification
import android.content.Context
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor.Setting
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

private const val SETTING_ALLOW_LIST = "notification_allow_list"
private const val SETTING_DISABLE_ALLOW_LIST = "notification_disable_allow_list"

// Hidden Notification.FLAG_AUTOGROUP_SUMMARY system API
private const val FLAG_AUTOGROUP_SUMMARY = 0x00000400

// Flags Android sets on the summaries it creates when auto-grouping an app's notifications
private const val SYSTEM_AUTOGROUP_SUMMARY_FLAGS =
    FLAG_AUTOGROUP_SUMMARY or Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_LOCAL_ONLY

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
                Setting.Toggle(name = "active_notification_count_content_attrs", default = true),
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("groupSummaryCases")
    fun `Given notification when checking for system generated group summary then only Android auto-group summaries match`(
        description: String,
        group: String?,
        flags: Int,
        expected: Boolean,
    ) {
        val notification = mockk<Notification> {
            every { this@mockk.group } returns group
        }.apply { this.flags = flags }

        assertEquals(expected, notification.isSystemGeneratedGroupSummary(), description)
    }

    companion object {
        @JvmStatic
        fun groupSummaryCases() = listOf(
            Arguments.of("Plain app notification", null, Notification.FLAG_AUTO_CANCEL, false),
            Arguments.of("App defined group summary", "app_group", Notification.FLAG_GROUP_SUMMARY, false),
            Arguments.of("Child of an app defined group", "app_group", Notification.FLAG_AUTO_CANCEL, false),
            Arguments.of("ranker_group auto-group summary", "ranker_group", SYSTEM_AUTOGROUP_SUMMARY_FLAGS, true),
            Arguments.of("ranker_group key without auto-group flag", "ranker_group", 0, true),
            Arguments.of(
                "Sectioned Aggregate auto-group summary (Android 16+)",
                "0|com.example|g:Aggregate_AlertingSection",
                SYSTEM_AUTOGROUP_SUMMARY_FLAGS or Notification.FLAG_AUTO_CANCEL,
                true,
            ),
        )

        @JvmStatic
        fun sensorIds() = listOf(
            NotificationListenerSensorManager.lastNotification.id,
            NotificationListenerSensorManager.lastRemovedNotification.id,
        )
    }

    private fun defaultSettings() = listOf(
        Setting.Apps(name = SETTING_ALLOW_LIST),
        Setting.Toggle(name = SETTING_DISABLE_ALLOW_LIST, default = false),
    )

    private fun notificationSensor(sensorId: String) = when (sensorId) {
        NotificationListenerSensorManager.lastNotification.id -> NotificationListenerSensorManager.lastNotification
        NotificationListenerSensorManager.lastRemovedNotification.id ->
            NotificationListenerSensorManager.lastRemovedNotification
        else -> error("Unknown notification sensor: $sensorId")
    }
}
