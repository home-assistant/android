package io.homeassistant.companion.android.database.settings

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.backup.BackupSettingsChanges
import io.homeassistant.companion.android.common.data.backup.RestoredSensorSelection
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepositoryImpl
import io.homeassistant.companion.android.common.sensors.SensorSettingsIntentProvider
import io.homeassistant.companion.android.common.sensors.SensorUpdater
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SettingsBackupDaoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = Server(
        id = 42,
        _name = "Home",
        connection = ServerConnectionInfo("https://example.invalid"),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )
    private val appVersion = AppVersion("test", 1)
    private val coreVersion = "2026.10.0"
    private val basicSensor = SensorManager.BasicSensor(
        id = "battery",
        type = "sensor",
        name = R.string.sensor,
        statelessIcon = "mdi:battery",
    )
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        context.deleteDatabase("settings-backup-test")
        database = Room.databaseBuilder(context, AppDatabase::class.java, "settings-backup-test").build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `Given an existing sensor when restoring then it awaits synchronization while readings and metadata are retained`() = runTest {
        database.serverDao().add(server)
        val sensor = Sensor(
            id = basicSensor.id,
            serverId = server.id,
            enabled = true,
            registered = true,
            state = "85",
            lastSentState = "85",
            lastSentIcon = basicSensor.statelessIcon,
            coreRegistration = coreVersion,
            appRegistration = appVersion.toString(),
        )
        val untouched = sensor.copy(id = "other")
        database.sensorDao().upsert(sensor)
        database.sensorDao().upsert(untouched)
        database.settingsBackupDao().apply(
            BackupSettingsChanges(setOf(42), listOf(RestoredSensorSelection("battery", 42, false)), emptyList(), mapOf(42 to WebsocketSetting.ALWAYS), SensorUpdateFrequencySetting.FAST_ALWAYS),
        )
        val restored = database.sensorDao().get("battery", 42)!!
        assertFalse(restored.enabled)
        assertNull(restored.registered)
        assertEquals(sensor.state, restored.state)
        assertEquals(sensor.coreRegistration, restored.coreRegistration)
        assertEquals(sensor.appRegistration, restored.appRegistration)
        assertNull(restored.lastSentState)
        assertNull(restored.lastSentIcon)
        assertEquals(untouched, database.sensorDao().get(untouched.id, untouched.serverId))
        assertEquals(WebsocketSetting.ALWAYS, database.settingsDao().get(42)?.websocketSetting)
        assertEquals(SensorUpdateFrequencySetting.FAST_ALWAYS, database.settingsDao().get(0)?.sensorUpdateFrequency)
    }

    @Test
    fun `Given a removed destination when restoring then no database changes are applied`() = runTest {
        val original = Setting(0, WebsocketSetting.NEVER, SensorUpdateFrequencySetting.NORMAL)
        database.settingsDao().insert(original)
        try {
            database.settingsBackupDao().apply(BackupSettingsChanges(setOf(42), emptyList(), emptyList(), emptyMap(), SensorUpdateFrequencySetting.FAST_ALWAYS))
            throw AssertionError("Restore should fail")
        } catch (expected: IllegalStateException) {
            assertEquals(original, database.settingsDao().get(0))
        }
    }

    @Test
    fun `Given a sensor disabled on the server when restoring its cached enabled choice then synchronization keeps it enabled`() = runTest {
        assertRestoredSelectionWins(enabled = true)
    }

    @Test
    fun `Given a sensor enabled on the server when restoring its cached disabled choice then synchronization keeps it disabled`() = runTest {
        assertRestoredSelectionWins(enabled = false)
    }

    private suspend fun assertRestoredSelectionWins(enabled: Boolean) {
        database.serverDao().add(server)
        val sensor = Sensor(
            id = basicSensor.id,
            serverId = server.id,
            enabled = enabled,
            registered = enabled,
            state = "85",
            stateType = "int",
            icon = basicSensor.statelessIcon,
            lastSentState = "85",
            lastSentIcon = basicSensor.statelessIcon,
            coreRegistration = coreVersion,
            appRegistration = appVersion.toString(),
        )
        database.sensorDao().upsert(sensor)
        var serverEnabled = !enabled
        val integrationRepository = mockk<IntegrationRepository>(relaxed = true) {
            coEvery { getConfig() } answers { serverConfig(enabled = serverEnabled) }
            coEvery { getHomeAssistantVersion() } returns coreVersion
            coEvery { isHomeAssistantVersionAtLeast(2022, 6, 0) } returns true
            coEvery { isTrusted() } returns true
            coEvery { registerSensor(any()) } answers {
                serverEnabled = !firstArg<SensorRegistration<Any>>().disabled
            }
            coEvery { updateSensors(any()) } returns true
        }
        database.settingsBackupDao().apply(
            BackupSettingsChanges(setOf(server.id), listOf(RestoredSensorSelection(basicSensor.id, server.id, enabled)), emptyList(), emptyMap(), null),
        )

        val updater = sensorUpdater(integrationRepository)
        repeat(2) { updater.updateSensors() }

        val syncedSensor = database.sensorDao().get(basicSensor.id, server.id)!!
        assertEquals(enabled, syncedSensor.enabled)
        assertEquals(enabled, syncedSensor.registered)
        assertEquals(enabled, serverEnabled)
        assertEquals(sensor.state, syncedSensor.state)
        coVerify(exactly = 1) {
            integrationRepository.registerSensor(match { it.uniqueId == basicSensor.id && it.serverId == server.id && it.disabled == !enabled })
        }
    }

    private fun sensorUpdater(integrationRepository: IntegrationRepository): SensorUpdater {
        val serverManager = mockk<ServerManager> {
            coEvery { isRegistered() } returns true
            coEvery { servers() } returns listOf(server)
            coEvery { integrationRepository(server.id) } returns integrationRepository
        }
        val manager = mockk<SensorManager>(relaxed = true) {
            every { hasSensor() } returns true
            coEvery { getAvailableSensors() } returns listOf(basicSensor)
            coEvery { checkPermission(basicSensor.id) } returns true
        }
        return SensorUpdater(
            context = context,
            serverManager = serverManager,
            sensorRepository = SensorRepositoryImpl(database.sensorDao(), database.serverDao(), setOf(basicSensor)),
            appVersion = appVersion,
            managers = setOf(manager),
            sensorSettingsIntentProvider = SensorSettingsIntentProvider { _, _, _, _ -> null },
            notificationManager = null,
        )
    }

    private fun serverConfig(enabled: Boolean) = GetConfigResponse(
        latitude = 0.0,
        longitude = 0.0,
        elevation = 0.0,
        unitSystem = emptyMap(),
        locationName = "",
        timeZone = "",
        components = emptyList(),
        version = coreVersion,
        entities = mapOf(basicSensor.id to mapOf("disabled" to !enabled)),
    )
}
