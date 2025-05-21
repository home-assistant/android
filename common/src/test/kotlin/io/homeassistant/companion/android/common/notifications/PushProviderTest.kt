package io.homeassistant.companion.android.common.notifications

import android.content.Context
import io.homeassistant.companion.android.common.ConsoleLogTree
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.database.settings.PushProviderSetting
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

class PushProviderTest {
    private lateinit var mockServerManager: ServerManager
    private lateinit var mockAppDatabase: AppDatabase

    @BeforeEach
    fun setUp() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun TestScope.setupServer(
        isValid: Boolean
    ) {
        mockServerManager = mockk(relaxed = true)

        if (isValid) {
            val testServerId = 1
            val testServer = Server(
                id = testServerId,
                _name = "Test Server",
                connection = ServerConnectionInfo(
                    externalUrl = "https://io.ha",
                ),
                session = ServerSessionInfo(),
                user = ServerUserInfo(),
            )

            every { mockServerManager.getServer(any<Int>()) } returns testServer
            every { mockServerManager.defaultServers } returns listOf(testServer)
        } else {
            every { mockServerManager.getServer(any<Int>()) } returns null
        }
    }

    private fun TestScope.mockDatabase(
        setting: PushProviderSetting = PushProviderSetting.NONE,
        isValid: Boolean
    ) {
        mockAppDatabase = mockk(relaxed = true)
        mockkObject(AppDatabase)

        val mockSettingsDao: SettingsDao = mockk()

        if (isValid) {
            val testSetting = Setting(
                id = 1,
                websocketSetting = WebsocketSetting.NEVER,
                sensorUpdateFrequency = SensorUpdateFrequencySetting.NORMAL,
                pushProvider = setting
            )

            every { mockSettingsDao.get(any<Int>()) } returns testSetting
        } else {
            every { mockSettingsDao.get(any<Int>()) } returns null
        }

        every { AppDatabase.getInstance(any<Context>()) } returns mockAppDatabase
        every { mockAppDatabase.settingsDao() } returns mockSettingsDao
    }

    private fun TestScope.getPushProvider(
        setting: PushProviderSetting = PushProviderSetting.NONE,
        isAvailable: Boolean = false,
        token: String = "",
        onMessage: (Context, Map<String, String>) -> Unit = { _, _ -> }
    ): PushProvider {
        return object : PushProvider {
            override val setting = setting
            override fun isAvailable(context: Context): Boolean = isAvailable
            override suspend fun getToken(): String = token
            override fun onMessage(context: Context, notificationData: Map<String, String>) =
                onMessage(context, notificationData)

            override fun serverManager(context: Context): ServerManager = mockServerManager
        }
    }

    @Test
    fun `Given no server When isEnabled is invoked Then it returns false`() = runTest {
        val context: Context = mockk()
        setupServer(isValid = false)
        mockDatabase(isValid = false)
        val pushProvider = getPushProvider()

        var result = pushProvider.isEnabled(context)
        assertFalse(result)

        result = pushProvider.isEnabled(context, 1)
        assertFalse(result)
    }

    @Test
    fun `Given a valid server When isEnabled is invoked Then it returns true`() = runTest {
        val context: Context = mockk()
        setupServer(isValid = true)
        mockDatabase(PushProviderSetting.FCM, isValid = true)
        val pushProvider = getPushProvider(PushProviderSetting.FCM)

        var result = pushProvider.isEnabled(context)
        assertTrue(result)

        result = pushProvider.isEnabled(context, 1)
        assertTrue(result)

        val servers = pushProvider.getEnabledServers(context)
        assertEquals(servers, setOf(1))
    }

    @Test
    fun `Given a valid disabled server When isEnabled is invoked Then it returns false`() = runTest {
        val context: Context = mockk()
        setupServer(isValid = true)
        mockDatabase(PushProviderSetting.NONE, isValid = true)
        val pushProvider = getPushProvider(PushProviderSetting.FCM)

        var result = pushProvider.isEnabled(context)
        assertFalse(result)

        result = pushProvider.isEnabled(context, 1)
        assertFalse(result)
    }

    @Test
    fun `Given no server When getEnabledServers is invoked Then it returns an empty set`() = runTest {
        val context: Context = mockk()
        setupServer(isValid = false)
        mockDatabase(isValid = false)
        val pushProvider = getPushProvider()

        val result = pushProvider.getEnabledServers(context)
        assertEquals(result, emptySet<Int>())
    }

    @Test
    fun `Given a valid server When getEnabledServers is invoked Then it returns that server`() = runTest {
        val context: Context = mockk()
        setupServer(isValid = true)
        mockDatabase(isValid = true)
        val pushProvider = getPushProvider()

        val result = pushProvider.getEnabledServers(context)
        assertEquals(result, setOf(1))
    }

    @Test
    fun `Given a valid disabled server When getEnabledServers is invoked Then it returns an empty set`() = runTest {
        val context: Context = mockk()
        setupServer(isValid = true)
        mockDatabase(PushProviderSetting.NONE, isValid = true)
        val pushProvider = getPushProvider(PushProviderSetting.FCM)

        val result = pushProvider.getEnabledServers(context)
        assertEquals(result, emptySet<Int>())
    }
}
