package io.homeassistant.companion.android.settings.backup

import io.homeassistant.companion.android.common.data.backup.BackupEntityReference
import io.homeassistant.companion.android.common.data.backup.BackupSensorOptionData
import io.homeassistant.companion.android.common.data.backup.BackupSensorOptionsData
import io.homeassistant.companion.android.common.data.backup.BackupServerData
import io.homeassistant.companion.android.common.data.backup.SETTINGS_BACKUP_FORMAT
import io.homeassistant.companion.android.common.data.backup.SETTINGS_BACKUP_VERSION
import io.homeassistant.companion.android.common.data.backup.SettingsBackupData
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor.Setting
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal fun backupFixture() = SettingsBackupData(
    SETTINGS_BACKUP_FORMAT,
    SETTINGS_BACKUP_VERSION,
    "test",
    Instant.parse("2026-10-07T12:00:00Z"),
    listOf(BackupServerData("home", "Home", mapOf("battery" to false, "location" to true), "ALWAYS")),
    androidAutoFavorites = listOf(BackupEntityReference("home", "cover.garage"), BackupEntityReference("home", "light.driveway")),
    sensorOptions = listOf(
        BackupSensorOptionsData(
            "location",
            mapOf("zones" to BackupSensorOptionData(zones = listOf(BackupEntityReference("home", "zone.home")), enabled = true)),
        ),
    ),
)

class SettingsRestorePlanTest {
    private val destinations = listOf(BackupDestination(42, "New phone home"), BackupDestination(99, "Other"))
    private val location = SensorManager.BasicSensor("location", "sensor", settings = listOf(Setting.Zones("zones")))
    private val capabilities = mapOf(
        "battery" to BackupSensorCapability(SensorManager.BasicSensor("battery", "sensor"), true),
        "location" to BackupSensorCapability(location, true),
    )

    @Test
    fun `Given changed local server IDs when preparing then favorites sensors and zones all use the destination`() {
        val plan = prepareSettingsRestore(backupFixture(), mapOf("home" to 42), destinations, capabilities)
        assertEquals(listOf(AutoFavorite(42, "cover.garage"), AutoFavorite(42, "light.driveway")), plan.favorites)
        assertTrue(plan.changes.sensors.all { it.serverId == 42 })
        assertFalse(plan.changes.sensors.first { it.sensorId == "battery" }.enabled)
        assertEquals("42_zone.home", plan.changes.options.single().value)
        assertEquals(setOf(42), plan.changes.connections.keys)
    }

    @Test
    fun `Given missing permissions and hardware when preparing then unsupported changes are reported and skipped`() {
        val plan = prepareSettingsRestore(
            backupFixture(),
            mapOf("home" to 42),
            destinations,
            mapOf("location" to BackupSensorCapability(location, false)),
        )
        assertTrue(plan.changes.sensors.isEmpty())
        assertTrue(plan.issues.contains(RestoreIssue.UnsupportedSensor("battery")))
        assertTrue(plan.issues.contains(RestoreIssue.PermissionRequired("location")))
    }

    @Test
    fun `Given an unmapped server when preparing then its favorites selections and zone options are left alone`() {
        val plan = prepareSettingsRestore(backupFixture(), emptyMap(), destinations, capabilities)
        assertNull(plan.favorites)
        assertTrue(plan.changes.sensors.isEmpty())
        assertTrue(plan.changes.options.isEmpty())
        assertTrue(plan.changes.connections.isEmpty())
        assertEquals(0, plan.changeCount)
    }

    @Test
    fun `Given an ambiguous or stale mapping when preparing then it is rejected`() {
        val backup = backupFixture().copy(servers = backupFixture().servers + BackupServerData("work", "Work"))
        listOf(mapOf("home" to 42, "work" to 42), mapOf("home" to 100), mapOf("unknown" to 42)).forEach { mapping ->
            assertThrows(IllegalArgumentException::class.java) { prepareSettingsRestore(backup, mapping, destinations, capabilities) }
        }
    }

    @Test
    fun `Given empty favorites versus an omitted section when preparing then only explicit empty favorites are cleared`() {
        val empty = prepareSettingsRestore(backupFixture().copy(androidAutoFavorites = emptyList()), mapOf("home" to 42), destinations, capabilities)
        val absent = prepareSettingsRestore(backupFixture().copy(androidAutoFavorites = null), mapOf("home" to 42), destinations, capabilities)
        assertEquals(emptyList<AutoFavorite>(), empty.favorites)
        assertNull(absent.favorites)
    }

    @Test
    fun `Given local identity settings and malformed options when restoring then they are never written`() {
        val identity = Setting.Text("ble_id1")
        assertNull(restoreSensorOption("ble_emitter", identity, BackupSensorOptionData("old-phone", enabled = true), emptyMap()))
        assertNull(exportSensorOption(identity, SensorSetting("ble_emitter", "ble_id1", "old-phone", SensorSettingType.STRING), emptyMap()))
        assertNull(restoreSensorOption("location", Setting.Number("interval", 5), BackupSensorOptionData("NaN", enabled = true), emptyMap()))
        assertNull(restoreSensorOption("location", Setting.Decimal("multiplier", 1.0), BackupSensorOptionData("Infinity", enabled = true), emptyMap()))
        assertNull(restoreSensorOption("location", Setting.Toggle("enabled", true), BackupSensorOptionData("yes", enabled = true), emptyMap()))
        assertNull(restoreSensorOption("location", Setting.Options("mode", "one", listOf("one")), BackupSensorOptionData("two", enabled = true), emptyMap()))
    }

    @Test
    fun `Given a zone with an underscored entity ID when exporting then its portable reference round trips`() {
        val definition = Setting.Zones("zones")
        val value = SensorSetting("location", "zones", "12_zone.holiday_home", SensorSettingType.LIST_ZONES)
        val exported = exportSensorOption(definition, value, mapOf(12 to "home"))!!
        assertEquals(listOf(BackupEntityReference("home", "zone.holiday_home")), exported.zones)
        assertEquals("42_zone.holiday_home", restoreSensorOption("location", definition, exported, mapOf("home" to 42))?.value)
    }
}
