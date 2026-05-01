package io.homeassistant.companion.android.sensors.healthconnect.command

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectDataType
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Parsed, validated form of an FCM `command_health_connect_write` payload.
 *
 * The wire format is a flat `Map<String, String>` (FCM data fields are always strings),
 * so all coercion happens in [parse]. Numeric / temporal fields are reported as
 * [InvalidPayloadException] rather than swallowed — the handler turns those into a
 * user-visible notification so that broken automations are debuggable.
 *
 * Sub-payloads for sample-based records (heart rate, sleep) are encoded as JSON arrays
 * inside their own field (`samples` / `stages`) — this keeps the rest of the payload
 * comfortably flat for HA template authors.
 */
sealed class HealthConnectWriteCommandPayload {
    abstract val dataType: HealthConnectDataType
    abstract val clientRecordId: String?

    data class Instantaneous(
        override val dataType: HealthConnectDataType,
        override val clientRecordId: String?,
        val time: Instant,
        val value: Double,
    ) : HealthConnectWriteCommandPayload()

    data class Interval(
        override val dataType: HealthConnectDataType,
        override val clientRecordId: String?,
        val startTime: Instant,
        val endTime: Instant,
        val value: Double,
    ) : HealthConnectWriteCommandPayload()

    data class BloodPressure(
        override val dataType: HealthConnectDataType,
        override val clientRecordId: String?,
        val time: Instant,
        val systolic: Double,
        val diastolic: Double,
    ) : HealthConnectWriteCommandPayload()

    data class HeartRate(
        override val dataType: HealthConnectDataType,
        override val clientRecordId: String?,
        val startTime: Instant,
        val endTime: Instant,
        val samples: List<HeartRateRecord.Sample>,
    ) : HealthConnectWriteCommandPayload()

    data class Sleep(
        override val dataType: HealthConnectDataType,
        override val clientRecordId: String?,
        val startTime: Instant,
        val endTime: Instant,
        val title: String?,
        val notes: String?,
        val stages: List<SleepSessionRecord.Stage>,
    ) : HealthConnectWriteCommandPayload()

    class InvalidPayloadException(message: String) : IllegalArgumentException(message)

    companion object {
        const val FIELD_DATA_TYPE = "data_type"
        const val FIELD_VALUE = "value"
        const val FIELD_TIME = "time"
        const val FIELD_START_TIME = "start_time"
        const val FIELD_END_TIME = "end_time"
        const val FIELD_SYSTOLIC = "systolic"
        const val FIELD_DIASTOLIC = "diastolic"
        const val FIELD_CLIENT_RECORD_ID = "client_record_id"
        const val FIELD_SAMPLES = "samples"
        const val FIELD_STAGES = "stages"
        const val FIELD_TITLE = "title"
        const val FIELD_NOTES = "notes"

        /**
         * Sleep-stage string → HC integer constant. Mirrors the (`@RestrictTo`) map that
         * `androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_STRING_TO_INT_MAP`
         * exposes for library-internal use only. Kept as a local copy because the SDK
         * doesn't expose a public alternative.
         */
        private val SLEEP_STAGE_NAME_TO_INT: Map<String, Int> = mapOf(
            "awake" to SleepSessionRecord.STAGE_TYPE_AWAKE,
            "sleeping" to SleepSessionRecord.STAGE_TYPE_SLEEPING,
            "out_of_bed" to SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
            "light" to SleepSessionRecord.STAGE_TYPE_LIGHT,
            "deep" to SleepSessionRecord.STAGE_TYPE_DEEP,
            "rem" to SleepSessionRecord.STAGE_TYPE_REM,
            "awake_in_bed" to SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            "unknown" to SleepSessionRecord.STAGE_TYPE_UNKNOWN,
        )

        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            ignoreUnknownKeys = true
            namingStrategy = JsonNamingStrategy.SnakeCase
        }

        /**
         * Parse an FCM data map into a typed payload, or throw [InvalidPayloadException]
         * with a human-readable reason. The exception is caught one level up so the
         * handler can surface it as a notification instead of crashing the worker.
         *
         * @param now Clock-supplied "now" used when the payload omits an end time. Pulled
         *   from a parameter rather than [Instant.now] so tests are deterministic.
         */
        fun parse(data: Map<String, String>, now: Instant): HealthConnectWriteCommandPayload {
            val dataTypeKey = data[FIELD_DATA_TYPE]?.takeIf { it.isNotBlank() }
                ?: throw InvalidPayloadException("Missing required field: $FIELD_DATA_TYPE")
            val dataType = HealthConnectDataType.fromKey(dataTypeKey)
                ?: throw InvalidPayloadException("Unknown data_type: $dataTypeKey")
            val clientRecordId = data[FIELD_CLIENT_RECORD_ID]?.takeIf { it.isNotBlank() }

            return when (dataType) {
                HealthConnectDataType.BloodPressure -> BloodPressure(
                    dataType = dataType,
                    clientRecordId = clientRecordId,
                    time = parseInstant(data, FIELD_TIME, default = now),
                    systolic = parseDouble(data, FIELD_SYSTOLIC),
                    diastolic = parseDouble(data, FIELD_DIASTOLIC),
                )
                HealthConnectDataType.HeartRate -> {
                    val end = parseInstant(data, FIELD_END_TIME, default = now)
                    HeartRate(
                        dataType = dataType,
                        clientRecordId = clientRecordId,
                        startTime = parseInstant(data, FIELD_START_TIME, default = end),
                        endTime = end,
                        samples = parseHeartRateSamples(data),
                    )
                }
                HealthConnectDataType.Sleep -> {
                    val end = parseInstant(data, FIELD_END_TIME, default = now)
                    Sleep(
                        dataType = dataType,
                        clientRecordId = clientRecordId,
                        startTime = parseInstant(data, FIELD_START_TIME, default = end),
                        endTime = end,
                        title = data[FIELD_TITLE]?.takeIf { it.isNotBlank() },
                        notes = data[FIELD_NOTES]?.takeIf { it.isNotBlank() },
                        stages = parseSleepStages(data),
                    )
                }
                else -> if (dataType in INTERVAL_TYPES) {
                    val end = parseInstant(data, FIELD_END_TIME, default = now)
                    Interval(
                        dataType = dataType,
                        clientRecordId = clientRecordId,
                        startTime = parseInstant(data, FIELD_START_TIME, default = end),
                        endTime = end,
                        value = parseDouble(data, FIELD_VALUE),
                    )
                } else {
                    Instantaneous(
                        dataType = dataType,
                        clientRecordId = clientRecordId,
                        time = parseInstant(data, FIELD_TIME, default = now),
                        value = parseDouble(data, FIELD_VALUE),
                    )
                }
            }
        }

        private val INTERVAL_TYPES = setOf(
            HealthConnectDataType.ActiveCaloriesBurned,
            HealthConnectDataType.Distance,
            HealthConnectDataType.ElevationGained,
            HealthConnectDataType.FloorsClimbed,
            HealthConnectDataType.Hydration,
            HealthConnectDataType.Steps,
            HealthConnectDataType.TotalCaloriesBurned,
        )

        private fun parseDouble(data: Map<String, String>, field: String): Double {
            val raw = data[field] ?: throw InvalidPayloadException("Missing required field: $field")
            return raw.toDoubleOrNull()
                ?: throw InvalidPayloadException("Field $field must be a number, got: $raw")
        }

        private fun parseInstant(data: Map<String, String>, field: String, default: Instant): Instant {
            val raw = data[field] ?: return default
            return try {
                Instant.parse(raw)
            } catch (e: DateTimeParseException) {
                throw InvalidPayloadException("Field $field must be ISO-8601 instant, got: $raw")
            }
        }

        private fun parseHeartRateSamples(data: Map<String, String>): List<HeartRateRecord.Sample> {
            val raw = data[FIELD_SAMPLES]
                ?: throw InvalidPayloadException("Missing required field: $FIELD_SAMPLES")
            val parsed = try {
                json.decodeFromString<List<HeartRateSampleDto>>(raw)
            } catch (e: Exception) {
                throw InvalidPayloadException(
                    "Field $FIELD_SAMPLES must be a JSON array of {time, beats_per_minute}: ${e.message}",
                )
            }
            if (parsed.isEmpty()) {
                throw InvalidPayloadException("Field $FIELD_SAMPLES must contain at least one sample")
            }
            return parsed.map { dto ->
                val time = try {
                    Instant.parse(dto.time)
                } catch (e: DateTimeParseException) {
                    throw InvalidPayloadException("Heart rate sample time must be ISO-8601: ${dto.time}")
                }
                HeartRateRecord.Sample(time = time, beatsPerMinute = dto.beatsPerMinute)
            }
        }

        private fun parseSleepStages(data: Map<String, String>): List<SleepSessionRecord.Stage> {
            val raw = data[FIELD_STAGES] ?: return emptyList()
            val parsed = try {
                json.decodeFromString<List<SleepStageDto>>(raw)
            } catch (e: Exception) {
                throw InvalidPayloadException(
                    "Field $FIELD_STAGES must be a JSON array of {start_time, end_time, stage}: ${e.message}",
                )
            }
            return parsed.map { dto ->
                val start = try {
                    Instant.parse(dto.startTime)
                } catch (e: DateTimeParseException) {
                    throw InvalidPayloadException("Sleep stage start_time must be ISO-8601: ${dto.startTime}")
                }
                val end = try {
                    Instant.parse(dto.endTime)
                } catch (e: DateTimeParseException) {
                    throw InvalidPayloadException("Sleep stage end_time must be ISO-8601: ${dto.endTime}")
                }
                val stageInt = SLEEP_STAGE_NAME_TO_INT[dto.stage.lowercase()]
                    ?: throw InvalidPayloadException(
                        "Unknown sleep stage '${dto.stage}'. Expected one of: ${SLEEP_STAGE_NAME_TO_INT.keys}",
                    )
                SleepSessionRecord.Stage(startTime = start, endTime = end, stage = stageInt)
            }
        }
    }

    @Serializable
    private data class HeartRateSampleDto(val time: String, val beatsPerMinute: Long)

    @Serializable
    private data class SleepStageDto(val startTime: String, val endTime: String, val stage: String)
}
