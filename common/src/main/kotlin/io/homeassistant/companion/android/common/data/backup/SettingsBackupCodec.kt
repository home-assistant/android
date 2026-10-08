package io.homeassistant.companion.android.common.data.backup

import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val MAX_SERVERS = 100
private const val MAX_ENTRIES = 10_000
private const val MAX_LABEL_LENGTH = 256
private const val MAX_VALUE_LENGTH = 16_384
private val REFERENCE_PATTERN = Regex("[a-zA-Z0-9_-]{1,64}")
private val ENTITY_PATTERN = Regex("[a-z0-9_]+\\.[a-z0-9_]+")

/** A malformed or incompatible document; messages never contain imported user data. */
class InvalidSettingsBackupException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/** Encodes and validates the public settings format independently of its storage destination. */
class SettingsBackupCodec @Inject constructor() {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Encodes a validated document as readable UTF-8 JSON. */
    fun encode(backup: SettingsBackupData): ByteArray {
        validate(backup)
        return json.encodeToString(backup).encodeToByteArray().also { validateSize(it.size) }
    }

    /** Rejects oversized, malformed, foreign, or unsupported documents before any settings are changed. */
    fun decode(bytes: ByteArray): SettingsBackupData {
        validateSize(bytes.size)
        val backup = try {
            json.decodeFromString<SettingsBackupData>(bytes.decodeToString(throwOnInvalidSequence = true))
        } catch (exception: SerializationException) {
            throw InvalidSettingsBackupException("Invalid settings backup JSON", exception)
        } catch (exception: CharacterCodingException) {
            throw InvalidSettingsBackupException("Settings backup must be UTF-8", exception)
        }
        validate(backup)
        return backup
    }

    private fun validateSize(size: Int) {
        verify(size in 1..SETTINGS_BACKUP_MAX_BYTES, "Settings backup exceeds the size limit")
    }

    private fun validate(backup: SettingsBackupData) {
        verify(backup.format == SETTINGS_BACKUP_FORMAT, "Not a Companion settings backup")
        verify(backup.schemaVersion == SETTINGS_BACKUP_VERSION, "Unsupported settings backup version")
        verify(backup.appVersion.length in 1..MAX_LABEL_LENGTH, "Invalid source app version")
        verify(backup.servers.size <= MAX_SERVERS, "Too many servers")
        val references = backup.servers.map { it.reference }.toSet()
        verify(references.size == backup.servers.size, "Duplicate server references")
        backup.servers.forEach { validateServer(it) }
        backup.androidAutoFavorites?.let { validateReferences(it, references) }
        backup.sensorUpdateFrequency?.let { value ->
            verify(SensorUpdateFrequencySetting.entries.any { it.name == value }, "Invalid update frequency")
        }
        backup.sensorOptions?.let { options ->
            verify(options.size <= MAX_ENTRIES, "Too many sensor options")
            verify(options.map { it.sensor }.distinct().size == options.size, "Duplicate sensor options")
            options.forEach { validateOptions(it, references) }
        }
    }

    private fun validateServer(server: BackupServerData) {
        verify(REFERENCE_PATTERN.matches(server.reference), "Invalid server reference")
        verify(server.name.length in 1..MAX_LABEL_LENGTH, "Invalid server name")
        server.sensors?.let { sensors ->
            verify(sensors.size <= MAX_ENTRIES, "Too many sensors")
            sensors.keys.forEach { verify(REFERENCE_PATTERN.matches(it), "Invalid sensor identifier") }
        }
        server.persistentConnection?.let { value ->
            verify(WebsocketSetting.entries.any { it.name == value }, "Invalid connection preference")
        }
    }

    private fun validateReferences(entries: List<BackupEntityReference>, servers: Set<String>) {
        verify(entries.size <= MAX_ENTRIES, "Too many entity references")
        verify(entries.distinct().size == entries.size, "Duplicate entity references")
        entries.forEach {
            verify(it.server in servers, "Unknown server reference")
            verify(it.entityId.length <= MAX_LABEL_LENGTH && ENTITY_PATTERN.matches(it.entityId), "Invalid entity ID")
        }
    }

    private fun validateOptions(sensor: BackupSensorOptionsData, servers: Set<String>) {
        verify(REFERENCE_PATTERN.matches(sensor.sensor), "Invalid sensor identifier")
        verify(sensor.options.size <= MAX_ENTRIES, "Too many sensor options")
        sensor.options.forEach { (name, option) ->
            verify(REFERENCE_PATTERN.matches(name), "Invalid sensor option name")
            verify((option.value == null) != (option.zones == null), "Sensor option must have one value")
            option.value?.let { verify(it.length <= MAX_VALUE_LENGTH, "Sensor option is too long") }
            option.zones?.let { zones ->
                validateReferences(zones, servers)
                verify(zones.all { it.entityId.startsWith("zone.") }, "Invalid zone reference")
            }
        }
    }

    private fun verify(condition: Boolean, message: String) {
        if (!condition) throw InvalidSettingsBackupException(message)
    }
}
