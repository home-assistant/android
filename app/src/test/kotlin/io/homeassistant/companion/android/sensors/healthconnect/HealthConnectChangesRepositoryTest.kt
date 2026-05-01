package io.homeassistant.companion.android.sensors.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.units.Mass
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Covers the three behaviors that drive correctness of the changes-API loop:
 *  1. First-poll baseline — mint a fresh token, emit no changes.
 *  2. Steady state — surface upsertions/deletions and persist the new cursor.
 *  3. Token expiry — clear stale token, re-mint, and force a sensor refresh.
 */
@ExtendWith(ConsoleLogExtension::class)
class HealthConnectChangesRepositoryTest {

    private lateinit var client: HealthConnectClient
    private lateinit var permissionController: PermissionController
    private lateinit var tokenStore: HealthConnectChangesTokenStore
    private lateinit var repository: HealthConnectChangesRepository

    @BeforeEach
    fun setUp() {
        permissionController = mockk(relaxed = true)
        client = mockk {
            every { permissionController } returns this@HealthConnectChangesRepositoryTest.permissionController
        }
        coEvery { permissionController.getGrantedPermissions() } returns
            setOf(HealthPermission.getReadPermission(WeightRecord::class))
        tokenStore = mockk(relaxed = true)
        repository = HealthConnectChangesRepository(Provider { client }, tokenStore)
    }

    @Test
    fun `pollChanges returns null when client is unavailable`() = runTest {
        val nullRepo = HealthConnectChangesRepository(Provider { null }, tokenStore)

        assertNull(nullRepo.pollChanges(listOf(HealthConnectDataType.Weight)))
    }

    @Test
    fun `data types without READ permission are skipped`() = runTest {
        coEvery { permissionController.getGrantedPermissions() } returns emptySet()

        val result = repository.pollChanges(listOf(HealthConnectDataType.Weight))

        assertEquals(emptySet<HealthConnectDataType>(), result)
        coVerify(exactly = 0) { client.getChangesToken(any()) }
    }

    @Test
    fun `first poll mints a token and reports no changes`() = runTest {
        coEvery { tokenStore.get(HealthConnectDataType.Weight) } returns null
        coEvery { client.getChangesToken(any()) } returns "tok-1"

        val result = repository.pollChanges(listOf(HealthConnectDataType.Weight))

        assertEquals(emptySet<HealthConnectDataType>(), result)
        coVerify { tokenStore.put(HealthConnectDataType.Weight, "tok-1") }
        coVerify(exactly = 0) { client.getChanges(any()) }
    }

    @Test
    fun `upsertion change marks the data type as changed and rotates the token`() = runTest {
        coEvery { tokenStore.get(HealthConnectDataType.Weight) } returns "tok-1"
        coEvery { client.getChanges("tok-1") } returns ChangesResponse(
            changes = listOf(UpsertionChange(weightRecord())),
            nextChangesToken = "tok-2",
            hasMore = false,
            changesTokenExpired = false,
        )

        val result = repository.pollChanges(listOf(HealthConnectDataType.Weight))

        assertEquals(setOf(HealthConnectDataType.Weight), result)
        coVerify { tokenStore.put(HealthConnectDataType.Weight, "tok-2") }
    }

    @Test
    fun `deletion change marks the data type as changed`() = runTest {
        coEvery { tokenStore.get(HealthConnectDataType.Weight) } returns "tok-1"
        coEvery { client.getChanges("tok-1") } returns ChangesResponse(
            changes = listOf(DeletionChange("rec-id")),
            nextChangesToken = "tok-2",
            hasMore = false,
            changesTokenExpired = false,
        )

        val result = repository.pollChanges(listOf(HealthConnectDataType.Weight))

        assertEquals(setOf(HealthConnectDataType.Weight), result)
    }

    @Test
    fun `expired token is cleared, refreshed, and reported as changed`() = runTest {
        coEvery { tokenStore.get(HealthConnectDataType.Weight) } returns "tok-old"
        coEvery { client.getChanges("tok-old") } returns ChangesResponse(
            changes = emptyList(),
            nextChangesToken = "",
            hasMore = false,
            changesTokenExpired = true,
        )
        coEvery { client.getChangesToken(any()) } returns "tok-new"

        val result = repository.pollChanges(listOf(HealthConnectDataType.Weight))

        assertEquals(setOf(HealthConnectDataType.Weight), result)
        coVerify { tokenStore.clear(HealthConnectDataType.Weight) }
        coVerify { tokenStore.put(HealthConnectDataType.Weight, "tok-new") }
    }

    @Test
    fun `paginated changes drain hasMore until exhausted`() = runTest {
        coEvery { tokenStore.get(HealthConnectDataType.Weight) } returns "tok-1"
        coEvery { client.getChanges("tok-1") } returns ChangesResponse(
            changes = listOf(UpsertionChange(weightRecord())),
            nextChangesToken = "tok-2",
            hasMore = true,
            changesTokenExpired = false,
        )
        coEvery { client.getChanges("tok-2") } returns ChangesResponse(
            changes = emptyList(),
            nextChangesToken = "tok-3",
            hasMore = false,
            changesTokenExpired = false,
        )

        val result = repository.pollChanges(listOf(HealthConnectDataType.Weight))

        assertTrue(HealthConnectDataType.Weight in result.orEmpty())
        coVerify { client.getChanges("tok-1") }
        coVerify { client.getChanges("tok-2") }
        coVerify { tokenStore.put(HealthConnectDataType.Weight, "tok-3") }
    }

    @Test
    fun `getChangesToken request includes the record class`() = runTest {
        coEvery { tokenStore.get(any()) } returns null
        val request = slot<ChangesTokenRequest>()
        coEvery { client.getChangesToken(capture(request)) } returns "tok"

        repository.pollChanges(listOf(HealthConnectDataType.Weight))

        assertTrue(WeightRecord::class in request.captured.recordTypes)
    }

    private fun weightRecord(): WeightRecord = WeightRecord(
        time = Instant.parse("2026-05-01T10:00:00Z"),
        zoneOffset = null,
        weight = Mass.kilograms(80.0),
        metadata = Metadata.manualEntry(),
    )
}
