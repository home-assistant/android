package io.homeassistant.companion.android.settings.backup

import io.homeassistant.companion.android.common.data.backup.BackupEntityReference
import io.homeassistant.companion.android.common.data.backup.BackupSections
import io.homeassistant.companion.android.common.data.backup.BackupSettingsChanges
import io.homeassistant.companion.android.common.data.backup.BackupSettingsRepository
import io.homeassistant.companion.android.common.data.backup.SettingsBackupCodec
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor.Setting
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorSetting
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

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
        assertEquals(listOf(BackupEntityReference("server-0", "switch.old")), data.androidAutoFavorites)
    }

    @Test
    fun `Given other server favorites when repeatedly restoring then order is retained without duplicates`() = runTest {
        val backup = backupFixture().copy(sensorOptions = null)
        repeat(3) { iteration ->
            if (iteration == 1) favorites = favorites.filterNot { it.serverId == 42 }
            handler.restore(backup, mapOf("home" to 42), handler.prepare(backup, mapOf("home" to 42)))
            assertEquals(listOf(AutoFavorite(99, "light.office"), AutoFavorite(42, "cover.garage"), AutoFavorite(42, "light.driveway")), favorites)
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

    @ParameterizedTest
    @CsvSource("'', -59", "' ', -59", "not-a-number, -59", "2147483648, -59", "-80, -80", "+0007, +0007")
    fun `Given an integer sensor setting when exporting and restoring then the source effective value replaces the destination`(
        storedValue: String,
        expectedValue: String,
    ) = runTest {
        assertNumericOptionRoundTrip(
            sourceDefinition = Setting.Number("measured_power", -59),
            destinationDefinition = Setting.Number("measured_power", -65),
            storedValue = storedValue,
            destinationValue = "-70",
            expectedValue = expectedValue,
        )
    }

    @ParameterizedTest
    @CsvSource("'', 1.05", "' ', 1.05", "not-a-number, 1.05", "1.500, 1.500", "1e-3, 1e-3", "0.0, 0.0")
    fun `Given a decimal sensor setting when exporting and restoring then the source effective value replaces the destination`(
        storedValue: String,
        expectedValue: String,
    ) = runTest {
        assertNumericOptionRoundTrip(
            sourceDefinition = Setting.Decimal("multiplier", 1.05),
            destinationDefinition = Setting.Decimal("multiplier", 2.5),
            storedValue = storedValue,
            destinationValue = "0.75",
            expectedValue = expectedValue,
        )
    }

    private suspend fun assertNumericOptionRoundTrip(
        sourceDefinition: Setting,
        destinationDefinition: Setting,
        storedValue: String,
        destinationValue: String,
        expectedValue: String,
    ) {
        val sensor = SensorManager.BasicSensor("battery", "sensor", settings = listOf(sourceDefinition))
        val sourceSetting = SensorSetting(sensor.id, sourceDefinition.name, storedValue, sourceDefinition.type, enabled = false)
        var currentSetting = sourceSetting
        coEvery { manager.getAvailableSensors() } returns listOf(sensor)
        coEvery { sensors.getSettings(sensor.id) } answers { listOf(currentSetting) }
        coEvery { settings.apply(any()) } answers {
            currentSetting = firstArg<BackupSettingsChanges>().options
                .find { it.sensorId == sensor.id && it.name == sourceDefinition.name } ?: currentSetting
        }
        val codec = SettingsBackupCodec()
        val exported = handler.export(BackupSections(favorites = false, sensors = false, connection = false, frequency = false))
        val backup = codec.decode(codec.encode(exported))

        currentSetting = sourceSetting.copy(value = destinationValue, enabled = true)
        coEvery { manager.getAvailableSensors() } returns listOf(sensor.copy(settings = listOf(destinationDefinition)))
        val plan = handler.prepare(backup, emptyMap())
        assertEquals(emptyList<RestoreIssue>(), plan.issues)

        handler.restore(backup, emptyMap(), plan)

        assertEquals(sourceSetting.copy(value = expectedValue), currentSetting)
        assertEquals(expectedValue, backup.sensorOptions?.single()?.options?.get(sourceDefinition.name)?.value)
    }
}
