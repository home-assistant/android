package io.homeassistant.companion.android.common.data.backup

import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SettingsBackupCodecTest {
    private val codec = SettingsBackupCodec()
    private val backup = SettingsBackupData(
        format = SETTINGS_BACKUP_FORMAT,
        schemaVersion = SETTINGS_BACKUP_VERSION,
        appVersion = "2026.10.2",
        createdAt = Instant.parse("2026-10-07T12:00:00Z"),
        servers = listOf(
            BackupServerData("home", "Home", mapOf("battery_level" to true, "geocoded_location" to false), "ALWAYS"),
            BackupServerData("work", "Work"),
        ),
        androidAutoFavorites = listOf(BackupEntityReference("work", "light.desk"), BackupEntityReference("home", "cover.garage")),
        sensorOptions = listOf(
            BackupSensorOptionsData(
                "location_background",
                mapOf(
                    "high_accuracy_mode_zone" to BackupSensorOptionData(
                        zones = listOf(BackupEntityReference("home", "zone.home")),
                        enabled = true,
                    ),
                ),
            ),
        ),
        sensorUpdateFrequency = "FAST_WHILE_CHARGING",
    )

    @Test
    fun `Given a version one fixture when decoding then portable configuration remains compatible`() {
        val document = javaClass.getResource("/backup/settings-v1.json")!!.readBytes()
        val decoded = codec.decode(document)
        assertEquals("ALWAYS", decoded.servers.single().persistentConnection)
        assertEquals(false, decoded.servers.single().sensors?.get("location_background"))
        assertEquals(listOf("cover.garage", "light.driveway"), decoded.androidAutoFavorites?.map { it.entityId })
        assertEquals(
            listOf(BackupEntityReference("server-0", "zone.home")),
            decoded.sensorOptions?.single()?.options?.get("high_accuracy_mode_zone")?.zones,
        )
        assertEquals(decoded, codec.decode(codec.encode(decoded)))
    }

    @Test
    fun `Given multiple servers and disabled sensors when round tripping then all configuration and order survive`() {
        assertEquals(backup, codec.decode(codec.encode(backup)))
    }

    @Test
    fun `Given an Android Auto favorites only export when encoding then the explicit section name is used`() {
        val selected = backup.select(BackupSections(sensors = false, sensorOptions = false, connection = false, frequency = false))
        val encoded = codec.encode(selected)
        val restored = codec.decode(encoded)
        assertEquals(backup.androidAutoFavorites, restored.androidAutoFavorites)
        assertEquals(
            setOf("format", "schemaVersion", "appVersion", "createdAt", "servers", "androidAutoFavorites"),
            Json.parseToJsonElement(encoded.decodeToString()).jsonObject.keys,
        )
        assertNull(restored.servers.first().sensors)
        assertNull(restored.servers.first().persistentConnection)
        assertNull(restored.sensorUpdateFrequency)
    }

    @Test
    fun `Given an empty favorites list when round tripping then it remains distinct from an absent section`() {
        val empty = backup.copy(androidAutoFavorites = emptyList())
        val absent = backup.copy(androidAutoFavorites = null)
        assertEquals(emptyList<BackupEntityReference>(), codec.decode(codec.encode(empty)).androidAutoFavorites)
        assertNull(codec.decode(codec.encode(absent)).androidAutoFavorites)
    }

    @Test
    fun `Given Android Auto favorites deselected when encoding then they are omitted without removing other settings`() {
        val selected = backup.select(BackupSections(favorites = false))
        val encoded = codec.encode(selected)
        val restored = codec.decode(encoded)
        assertFalse("androidAutoFavorites" in Json.parseToJsonElement(encoded.decodeToString()).jsonObject)
        assertEquals(backup.copy(androidAutoFavorites = null), restored)
    }

    @Test
    fun `Given the generic favorites field when decoding then it is rejected as an unknown section`() {
        val document = javaClass.getResource("/backup/settings-v1.json")!!.readText()
            .replace("\"androidAutoFavorites\"", "\"favorites\"")
        assertThrows(InvalidSettingsBackupException::class.java) { codec.decode(document.encodeToByteArray()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", "[]", "not json", "{\"format\":\"other\"}"])
    fun `Given malformed input when decoding then it is rejected`(input: String) {
        assertThrows(InvalidSettingsBackupException::class.java) { codec.decode(input.encodeToByteArray()) }
    }

    @Test
    fun `Given a newer format when decoding then it is rejected without using default settings`() {
        val json = codec.encode(backup).decodeToString().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        assertThrows(InvalidSettingsBackupException::class.java) { codec.decode(json.encodeToByteArray()) }
    }

    @Test
    fun `Given an oversized or non UTF8 file when decoding then it is rejected`() {
        assertThrows(InvalidSettingsBackupException::class.java) { codec.decode(ByteArray(SETTINGS_BACKUP_MAX_BYTES + 1)) }
        assertThrows(InvalidSettingsBackupException::class.java) { codec.decode(byteArrayOf(0xFF.toByte())) }
    }

    @Test
    fun `Given ambiguous or broken references when encoding then validation fails`() {
        val invalid = listOf(
            backup.copy(servers = backup.servers + backup.servers.first()),
            backup.copy(androidAutoFavorites = listOf(BackupEntityReference("missing", "light.desk"))),
            backup.copy(androidAutoFavorites = backup.androidAutoFavorites!! + backup.androidAutoFavorites.first()),
            backup.copy(sensorUpdateFrequency = "UNRECOGNIZED"),
            backup.copy(servers = listOf(backup.servers.first().copy(persistentConnection = "INVALID"))),
        )
        invalid.forEach { document ->
            assertThrows(InvalidSettingsBackupException::class.java) { codec.encode(document) }
        }
    }
}
