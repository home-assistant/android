package io.homeassistant.companion.android.frontend.permissions

import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ConsoleLogExtension::class)
class PermissionManagerTest {

    private val serverManager: ServerManager = mockk(relaxed = true)
    private val settingsDao: SettingsDao = mockk(relaxed = true)
    private val integrationRepository: IntegrationRepository = mockk(relaxed = true)
    private val notificationStatusProvider: NotificationStatusProvider = mockk()

    private val serverId = 1

    @BeforeEach
    fun setUp() {
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
    }

    private fun createManager(hasFcmPushSupport: Boolean): PermissionManager {
        return PermissionManager(
            serverManager = serverManager,
            settingsDao = settingsDao,
            hasFcmPushSupport = hasFcmPushSupport,
            notificationStatusProvider = notificationStatusProvider,
        )
    }

    @Nested
    inner class ShouldAskNotificationPermission {

        // hasFcm | notifEnabled | storedPref | expected
        @ParameterizedTest(name = "hasFcm={0}, notifEnabled={1}, storedPref={2} -> shouldAsk={3}")
        @CsvSource(
            "true,true,,false", // FCM + granted → auto-dismiss, no need to prompt
            "true,false,,true",  // FCM + not granted + fresh install → prompt
            "true,false,false,false", // FCM + not granted + user already answered → skip
            "false,true,,true",  // No FCM + granted + fresh install → prompt to configure websocket
            "false,false,,true",  // No FCM + not granted + fresh install → prompt
            "false,false,false,false", // No FCM + not granted + user already answered → skip
            "false,true,false,false", // No FCM + granted + user already answered → skip
        )
        fun `Given inputs then returns expected result`(
            hasFcm: Boolean,
            notifEnabled: Boolean,
            storedPref: String?,
            expected: Boolean,
        ) = runTest {
            every { notificationStatusProvider.areNotificationsEnabled() } returns notifEnabled
            coEvery { integrationRepository.shouldAskNotificationPermission() } returns storedPref?.toBooleanStrictOrNull()

            val manager = createManager(hasFcmPushSupport = hasFcm)
            val result = manager.shouldAskNotificationPermission(serverId)

            assertEquals(expected, result)
        }

        @Test
        fun `Given hasFcmPushSupport and notifications already granted then persists false`() = runTest {
            every { notificationStatusProvider.areNotificationsEnabled() } returns true
            coEvery { integrationRepository.shouldAskNotificationPermission() } returns null

            val manager = createManager(hasFcmPushSupport = true)
            manager.shouldAskNotificationPermission(serverId)

            coVerify { integrationRepository.setAskNotificationPermission(false) }
        }
    }

    @Nested
    inner class OnNotificationPermissionResult {

        @Test
        fun `Given permission granted when doesn't have FcmPushSupport then inserts websocket settings`() = runTest {
            val manager = createManager(hasFcmPushSupport = false)
            manager.onNotificationPermissionResult(serverId = serverId, granted = true)

            coVerify {
                settingsDao.insert(
                    Setting(
                        id = serverId,
                        websocketSetting = WebsocketSetting.ALWAYS,
                        sensorUpdateFrequency = SensorUpdateFrequencySetting.NORMAL,
                    ),
                )
            }
        }

        // Websocket settings should NOT be inserted when FCM is available or permission denied
        @ParameterizedTest(name = "hasFcm={0}, granted={1} -> no websocket insert")
        @CsvSource(
            "true,  true",
            "true,  false",
            "false, false",
        )
        fun `Given inputs where websocket should not be configured then does not insert settings`(
            hasFcm: Boolean,
            granted: Boolean,
        ) = runTest {
            val manager = createManager(hasFcmPushSupport = hasFcm)
            manager.onNotificationPermissionResult(serverId = serverId, granted = granted)

            coVerify(exactly = 0) { settingsDao.insert(any()) }
        }

        @ParameterizedTest(name = "granted={0} -> persists do not ask again")
        @ValueSource(booleans = [true, false])
        fun `Given any result then persists do not ask again`(granted: Boolean) = runTest {
            val manager = createManager(hasFcmPushSupport = false)
            manager.onNotificationPermissionResult(serverId = serverId, granted = granted)

            coVerify { integrationRepository.setAskNotificationPermission(false) }
        }
    }
}
