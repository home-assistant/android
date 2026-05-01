package io.homeassistant.companion.android.sensors.healthconnect.command

import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectWriteRepository
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectWriteResult
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlin.time.ExperimentalTime
import kotlin.time.Instant as KInstant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Tests for the FCM payload → repository call translation.
 *
 * These tests focus on the dispatching logic — picking the right repository method for each
 * data type — and on payload validation. The actual record construction is the
 * repository's job and is covered separately in `HealthConnectWriteRepositoryImplTest`.
 */
@ExtendWith(ConsoleLogExtension::class)
@OptIn(ExperimentalTime::class)
class HealthConnectWriteCommandHandlerTest {

    private val fixedNow: KInstant = KInstant.parse("2026-05-01T12:00:00Z")
    private val fixedNowJava: Instant = Instant.parse("2026-05-01T12:00:00Z")
    private val clock = object : kotlin.time.Clock {
        override fun now(): KInstant = fixedNow
    }

    private val repository = mockk<HealthConnectWriteRepository>(relaxed = true) {
        coEvery { writeWeight(any(), any(), any()) } returns
            HealthConnectWriteResult.Success(listOf("id-w"))
        coEvery { writeBloodPressure(any(), any(), any(), any()) } returns
            HealthConnectWriteResult.Success(listOf("id-bp"))
        coEvery { writeSteps(any(), any(), any(), any()) } returns
            HealthConnectWriteResult.Success(listOf("id-s"))
    }
    private val handler = HealthConnectWriteCommandHandler(repository, clock)

    @Test
    fun `weight payload dispatches to writeWeight`() = runTest {
        val result = handler.handle(
            mapOf(
                "data_type" to "weight",
                "value" to "75.2",
                "time" to "2026-05-01T08:30:00Z",
                "client_record_id" to "scale-1",
            ),
        )
        assertTrue(result is HealthConnectWriteResult.Success)
        coVerify {
            repository.writeWeight(
                time = Instant.parse("2026-05-01T08:30:00Z"),
                kilograms = 75.2,
                clientRecordId = "scale-1",
            )
        }
    }

    @Test
    fun `weight payload defaults missing time to now`() = runTest {
        handler.handle(mapOf("data_type" to "weight", "value" to "70.0"))
        coVerify { repository.writeWeight(time = fixedNowJava, kilograms = 70.0, clientRecordId = null) }
    }

    @Test
    fun `blood_pressure payload dispatches to writeBloodPressure`() = runTest {
        handler.handle(
            mapOf(
                "data_type" to "blood_pressure",
                "systolic" to "118",
                "diastolic" to "76",
            ),
        )
        coVerify {
            repository.writeBloodPressure(
                time = fixedNowJava,
                systolicMmHg = 118.0,
                diastolicMmHg = 76.0,
                clientRecordId = null,
            )
        }
    }

    @Test
    fun `steps payload uses Long count and defaults end_time to now`() = runTest {
        handler.handle(
            mapOf(
                "data_type" to "steps",
                "value" to "1234",
                "start_time" to "2026-05-01T11:00:00Z",
            ),
        )
        coVerify {
            repository.writeSteps(
                startTime = Instant.parse("2026-05-01T11:00:00Z"),
                endTime = fixedNowJava,
                count = 1234L,
                clientRecordId = null,
            )
        }
    }

    @Test
    fun `unknown data_type returns InvalidPayload`() = runTest {
        val result = handler.handle(mapOf("data_type" to "telepathy", "value" to "1"))
        assertTrue(result is HealthConnectWriteResult.InvalidPayload)
    }

    @Test
    fun `missing data_type returns InvalidPayload`() = runTest {
        val result = handler.handle(mapOf("value" to "1"))
        assertTrue(result is HealthConnectWriteResult.InvalidPayload)
    }

    @Test
    fun `non-numeric value returns InvalidPayload`() = runTest {
        val result = handler.handle(mapOf("data_type" to "weight", "value" to "heavy"))
        assertTrue(result is HealthConnectWriteResult.InvalidPayload)
        assertEquals(
            "Field value must be a number, got: heavy",
            (result as HealthConnectWriteResult.InvalidPayload).reason,
        )
    }

    @Test
    fun `bad ISO timestamp returns InvalidPayload`() = runTest {
        val result = handler.handle(
            mapOf("data_type" to "weight", "value" to "70", "time" to "yesterday"),
        )
        assertTrue(result is HealthConnectWriteResult.InvalidPayload)
    }

    @Test
    fun `heart_rate parses samples JSON array`() = runTest {
        coEvery { repository.writeHeartRate(any(), any(), any(), any()) } returns
            HealthConnectWriteResult.Success(listOf("id-hr"))

        handler.handle(
            mapOf(
                "data_type" to "heart_rate",
                "start_time" to "2026-05-01T11:55:00Z",
                "end_time" to "2026-05-01T12:00:00Z",
                "samples" to """[{"time":"2026-05-01T11:55:00Z","beats_per_minute":72}]""",
            ),
        )

        coVerify {
            repository.writeHeartRate(
                startTime = Instant.parse("2026-05-01T11:55:00Z"),
                endTime = Instant.parse("2026-05-01T12:00:00Z"),
                samples = match { it.size == 1 && it.single().beatsPerMinute == 72L },
                clientRecordId = null,
            )
        }
    }

    @Test
    fun `permission denial from repository propagates to caller`() = runTest {
        coEvery { repository.writeWeight(any(), any(), any()) } returns
            HealthConnectWriteResult.MissingPermission("perm.WRITE_WEIGHT")
        val result = handler.handle(mapOf("data_type" to "weight", "value" to "70"))
        assertTrue(result is HealthConnectWriteResult.MissingPermission)
    }
}
