package io.homeassistant.companion.android.sensors

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor.Setting
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

private const val SETTING_ALLOW_LIST = "notification_allow_list"
private const val SETTING_DISABLE_ALLOW_LIST = "notification_disable_allow_list"

// Flags Android sets on the summaries it creates when auto-grouping an app's notifications
private const val SYSTEM_AUTOGROUP_SUMMARY_FLAGS =
    FLAG_AUTOGROUP_SUMMARY or Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_LOCAL_ONLY

private const val APP_PACKAGE = "io.homeassistant.companion.android"
private const val SOURCE_PACKAGE = "com.example"
private const val NOTIFICATION_TEXT = "Your order has arrived"
private const val SERVER_ID = 1

class NotificationListenerSensorManagerTest {

    private val context = mockk<Context>(relaxed = true) {
        every { packageName } returns APP_PACKAGE
    }
    private val sensorRepository: SensorRepository = mockk(relaxed = true) {
        coEvery { this@mockk.get(any<String>()) } returns emptyList()
    }

    @BeforeEach
    fun setUp() {
        SdkVersion.sdkInt = Build.VERSION_CODES.O
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.getEnabledListenerPackages(context) } returns setOf(APP_PACKAGE)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(NotificationManagerCompat::class)
        SdkVersion.resetSdkInt()
    }

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
        createManager(this).requestSensorUpdate()

        coVerify(exactly = 0) {
            sensorRepository.updateSettingValue(any(), any(), any())
            sensorRepository.addDynamicSetting(any())
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("groupSummaryCases")
    fun `Given allowed notification when posted then only non system summaries update last notification`(
        group: String?,
        flags: Int,
        reported: Boolean,
    ) = runTest {
        enableNotificationSensor(NotificationListenerSensorManager.lastNotification)

        createManager(this).onNotificationPosted(statusBarNotification(group, flags), emptyArray())
        awaitLaunchedWork()

        verifyNotificationReported(NotificationListenerSensorManager.lastNotification, reported)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("groupSummaryCases")
    fun `Given allowed notification when removed then only non system summaries update last removed notification`(
        group: String?,
        flags: Int,
        reported: Boolean,
    ) = runTest {
        enableNotificationSensor(NotificationListenerSensorManager.lastRemovedNotification)

        createManager(this).onNotificationRemoved(statusBarNotification(group, flags), emptyArray())
        awaitLaunchedWork()

        verifyNotificationReported(NotificationListenerSensorManager.lastRemovedNotification, reported)
    }

    companion object {
        @JvmStatic
        fun groupSummaryCases() = listOf(
            Arguments.of(named("Plain app notification", null), Notification.FLAG_AUTO_CANCEL, true),
            Arguments.of(named("App defined group summary", "app_group"), Notification.FLAG_GROUP_SUMMARY, true),
            Arguments.of(named("Child of an app defined group", "app_group"), Notification.FLAG_AUTO_CANCEL, true),
            Arguments.of(
                named("App defined group summary named ranker_group", "ranker_group"),
                Notification.FLAG_GROUP_SUMMARY,
                true,
            ),
            Arguments.of(named("ranker_group auto-group summary", "ranker_group"), SYSTEM_AUTOGROUP_SUMMARY_FLAGS, false),
            Arguments.of(
                named(
                    "Sectioned Aggregate auto-group summary (Android 16+)",
                    "0|$SOURCE_PACKAGE|g:Aggregate_AlertingSection",
                ),
                SYSTEM_AUTOGROUP_SUMMARY_FLAGS or Notification.FLAG_AUTO_CANCEL,
                false,
            ),
        )

        @JvmStatic
        fun sensorIds() = listOf(
            NotificationListenerSensorManager.lastNotification.id,
            NotificationListenerSensorManager.lastRemovedNotification.id,
        )
    }

    private fun createManager(sensorWorkerScope: CoroutineScope) = NotificationListenerSensorManager(
        context,
        sensorRepository,
        mockk<ServerManager>(relaxed = true),
        sensorWorkerScope,
    )

    /**
     * Waits for the work the notification callbacks launch in the test scope. Part of it switches to
     * `Dispatchers.Default` (`mappedBundle` and `SensorManager.onSensorUpdated`), which the test scheduler can't
     * advance, so the launched jobs are joined instead.
     */
    private suspend fun TestScope.awaitLaunchedWork() {
        this.coroutineContext.job.children.toList().joinAll()
    }

    private fun enableNotificationSensor(sensor: SensorManager.BasicSensor) {
        coEvery { sensorRepository.get(sensor.id) } returns listOf(
            Sensor(id = sensor.id, serverId = SERVER_ID, enabled = true, state = ""),
        )
        coEvery { sensorRepository.getSettings(sensor.id) } returns listOf(
            SensorSetting(sensor.id, SETTING_ALLOW_LIST, SOURCE_PACKAGE, SensorSettingType.LIST_APPS),
            SensorSetting(sensor.id, SETTING_DISABLE_ALLOW_LIST, false.toString(), SensorSettingType.TOGGLE),
        )
    }

    private fun statusBarNotification(group: String?, flags: Int): StatusBarNotification {
        val extras = mockk<Bundle> {
            every { keySet() } returns setOf(Notification.EXTRA_TEXT)
            @Suppress("DEPRECATION")
            every { this@mockk.get(Notification.EXTRA_TEXT) } returns NOTIFICATION_TEXT
        }
        val notification = mockk<Notification>(relaxed = true) {
            every { this@mockk.group } returns group
        }.apply {
            this.flags = flags
            this.extras = extras
        }
        return mockk(relaxed = true) {
            every { packageName } returns SOURCE_PACKAGE
            every { this@mockk.notification } returns notification
        }
    }

    private fun verifyNotificationReported(sensor: SensorManager.BasicSensor, reported: Boolean) {
        coVerify(exactly = if (reported) 1 else 0) {
            sensorRepository.update(match { it.id == sensor.id && it.state == NOTIFICATION_TEXT })
        }
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
