package io.homeassistant.companion.android.settings.backup

import io.homeassistant.companion.android.common.data.backup.BackupSections
import io.homeassistant.companion.android.common.data.backup.BackupSettingsRepository
import io.homeassistant.companion.android.common.data.backup.SettingsBackupCodec
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsBackupHandlerTest {
    private val servers = mockk<ServerManager>()
    private val manager = mockk<SensorManager>()
    private val sensors = mockk<SensorRepository>()
    private val settings = mockk<BackupSettingsRepository>()
    private val prefs = mockk<PrefsRepository>()
    private val originalFavorites = listOf(AutoFavorite(99, "light.office"), AutoFavorite(42, "switch.old"))
    private var favorites = originalFavorites
    private val server = Server(
        id = 42,
        _name = "Home",
        connection = ServerConnectionInfo("https://example.invalid", webhookId = "PRIVATE-WEBHOOK", secret = "PRIVATE-SECRET"),
        session = ServerSessionInfo(accessToken = "PRIVATE-TOKEN", installId = "PRIVATE-INSTALL"),
        user = ServerUserInfo(),
    )
    private lateinit var handler: SettingsBackupHandler

    @BeforeEach
    fun setup() {
        coEvery { servers.servers() } returns listOf(server)
        every { manager.hasSensor() } returns true
        coEvery { manager.getAvailableSensors() } returns listOf(SensorManager.BasicSensor("battery", "sensor"))
        coEvery { manager.checkPermission(any()) } returns true
        coEvery { settings.getSettings() } returns emptyList()
        coEvery { settings.apply(any()) } returns Unit
        every { sensors.getAllFlow() } returns flowOf(listOf(Sensor("battery", 42, true, state = "PRIVATE-READING")))
        coEvery { sensors.getSettings(any()) } returns emptyList()
        coEvery { prefs.getAutoFavorites() } answers { favorites }
        coEvery { prefs.setAutoFavorites(any()) } coAnswers { favorites = firstArg() }
        handler = SettingsBackupHandler(servers, BackupSensorManager(setOf(manager), sensors), settings, prefs, FakeClock())
    }

    @Test
    fun `Given registered server credentials when exporting then only portable data is serialized`() = runTest {
        val data = handler.export(BackupSections())
        val json = SettingsBackupCodec().encode(data).decodeToString()
        assertFalse(json.contains("PRIVATE-"))
        assertFalse(json.contains("example.invalid"))
        assertEquals("server-0", data.servers.single().reference)
        assertEquals(mapOf("battery" to true), data.servers.single().sensors)
    }

    @Test
    fun `Given other server favorites when repeatedly restoring then order is retained without duplicates`() = runTest {
        val backup = backupFixture().copy(sensorOptions = null)
        repeat(2) {
            handler.restore(backup, mapOf("home" to 42), handler.prepare(backup, mapOf("home" to 42)))
        }
        assertEquals(listOf(AutoFavorite(99, "light.office"), AutoFavorite(42, "cover.garage"), AutoFavorite(42, "light.driveway")), favorites)
    }

    @Test
    fun `Given a database failure when restoring then previous favorites are recovered`() = runTest {
        val backup = backupFixture()
        val mapping = mapOf("home" to 42)
        val plan = handler.prepare(backup, mapping)
        coEvery { settings.apply(any()) } throws IllegalStateException("Synthetic database failure")
        try {
            handler.restore(backup, mapping, plan)
            throw AssertionError("Restore should fail")
        } catch (expected: IllegalStateException) {
            assertEquals(originalFavorites, favorites)
        }
    }

    @Test
    fun `Given a server removed after preview when restoring then no preferences are written`() = runTest {
        val backup = backupFixture()
        val mapping = mapOf("home" to 42)
        val preview = handler.prepare(backup, mapping)
        coEvery { servers.servers() } returns emptyList()
        try {
            handler.restore(backup, mapping, preview)
            throw AssertionError("Restore should fail")
        } catch (expected: IllegalArgumentException) {
            coVerify(exactly = 0) { prefs.setAutoFavorites(any()) }
            coVerify(exactly = 0) { settings.apply(any()) }
        }
    }
}
