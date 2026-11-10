package io.homeassistant.companion.android.sensors

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor
import io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor.Setting
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

private const val SETTING_ALLOW_LIST = "notification_allow_list"
private const val SETTING_DISABLE_ALLOW_LIST = "notification_disable_allow_list"
private const val SETTING_INCLUDE_CONTENTS_AS_ATTRS = "active_notification_count_content_attrs"
private const val APP_PACKAGE = "io.homeassistant.companion.android"
private const val ALLOWED_PACKAGE = "com.example.allowed"
private const val OTHER_PACKAGE = "com.example.other"
private const val SERVER_ID = 1
private val HANDLER_TIMEOUT = 5.seconds

@ExtendWith(MainDispatcherJUnit5Extension::class)
class NotificationListenerSensorManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val sensorRepository: SensorRepository = mockk(relaxed = true)
    private val manager = NotificationListenerSensorManager(
        context,
        sensorRepository,
        mockk<ServerManager>(relaxed = true),
    )

    @BeforeEach
    fun setUp() {
        SdkVersion.sdkInt = Build.VERSION_CODES.O
        every { context.packageName } returns APP_PACKAGE
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.getEnabledListenerPackages(context) } returns setOf(APP_PACKAGE)
        mockkObject(SensorReceiver.Companion)
        every { SensorReceiver.updateSensor(any(), any()) } just runs
        every { SensorReceiver.updateAllSensors(any()) } just runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
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
    fun `Given active notification count sensor when inspected then content setting defaults to off`() {
        assertEquals(
            listOf(
                Setting.Toggle(name = "active_notification_count_content_attrs", default = false),
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

    @Test
    fun `Given an allowed notification when posted then only the last notification sensor is updated`() = runTest {
        enableNotificationSensor(NotificationListenerSensorManager.lastNotification)

        manager.onNotificationPosted(notification(ALLOWED_PACKAGE), emptyArray())
        awaitSensorUpdate(NotificationListenerSensorManager.lastNotification.id)

        verify(exactly = 1) { SensorReceiver.updateSensor(context, NotificationListenerSensorManager.lastNotification.id) }
        verify(exactly = 0) { SensorReceiver.updateAllSensors(any()) }
    }

    @Test
    fun `Given an allowed notification when removed then only the last removed notification sensor is updated`() = runTest {
        enableNotificationSensor(NotificationListenerSensorManager.lastRemovedNotification)

        manager.onNotificationRemoved(notification(ALLOWED_PACKAGE), emptyArray())
        awaitSensorUpdate(NotificationListenerSensorManager.lastRemovedNotification.id)

        verify(exactly = 1) {
            SensorReceiver.updateSensor(context, NotificationListenerSensorManager.lastRemovedNotification.id)
        }
        verify(exactly = 0) { SensorReceiver.updateAllSensors(any()) }
    }

    @Test
    fun `Given a notification from a package not in the allow list when posted then no sensor is updated`() = runTest {
        enableNotificationSensor(NotificationListenerSensorManager.lastNotification)

        manager.onNotificationPosted(notification(OTHER_PACKAGE), emptyArray())
        advanceUntilIdle()

        verify(exactly = 0) { SensorReceiver.updateSensor(any(), any()) }
        verify(exactly = 0) { SensorReceiver.updateAllSensors(any()) }
    }

    @Test
    fun `Given the active notification count enabled when a notification is posted then the count sensor is updated`() = runTest {
        val countSensor = NotificationListenerSensorManager.activeNotificationCount
        coEvery { sensorRepository.get(countSensor.id) } returns listOf(enabledSensor(countSensor.id))
        coEvery { sensorRepository.getSettings(countSensor.id) } returns listOf(
            SensorSetting(countSensor.id, SETTING_INCLUDE_CONTENTS_AS_ATTRS, "false", SensorSettingType.TOGGLE),
        )
        manager.onListenerConnectionChanged(true)

        manager.onNotificationPosted(notification(OTHER_PACKAGE), arrayOf(notification(OTHER_PACKAGE)))
        awaitSensorUpdate(countSensor.id)

        verify(exactly = 1) { SensorReceiver.updateSensor(context, countSensor.id) }
        verify(exactly = 0) { SensorReceiver.updateAllSensors(any()) }
    }

    private fun enableNotificationSensor(sensor: BasicSensor) {
        coEvery { sensorRepository.get(sensor.id) } returns listOf(enabledSensor(sensor.id))
        coEvery { sensorRepository.getSettings(sensor.id) } returns listOf(
            SensorSetting(sensor.id, SETTING_ALLOW_LIST, ALLOWED_PACKAGE, SensorSettingType.LIST_APPS),
            SensorSetting(sensor.id, SETTING_DISABLE_ALLOW_LIST, "false", SensorSettingType.TOGGLE),
        )
    }

    private fun enabledSensor(id: String) = Sensor(id = id, serverId = SERVER_ID, enabled = true, state = "")

    private fun notification(sourcePackage: String): StatusBarNotification {
        // extras is a field, so it is assigned rather than stubbed
        val notification = mockk<Notification>(relaxed = true)
        notification.extras = mockk<Bundle> { every { keySet() } returns emptySet() }
        return mockk(relaxed = true) {
            every { packageName } returns sourcePackage
            every { this@mockk.notification } returns notification
        }
    }

    /**
     * Runs the handler launched on Main until it broadcasts the update of [sensorId]. The handler
     * hops to Dispatchers.Default to read the notification and store the state, then back to Main,
     * so the Main dispatcher has to be advanced again after each hop.
     */
    private suspend fun TestScope.awaitSensorUpdate(sensorId: String) = withContext(Dispatchers.Default) {
        withTimeout(HANDLER_TIMEOUT) {
            while (!isSensorUpdateRequested(sensorId)) {
                testScheduler.advanceUntilIdle()
                yield()
            }
        }
    }

    private fun isSensorUpdateRequested(sensorId: String): Boolean = runCatching {
        verify(exactly = 1) { SensorReceiver.updateSensor(context, sensorId) }
    }.isSuccess

    companion object {
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
