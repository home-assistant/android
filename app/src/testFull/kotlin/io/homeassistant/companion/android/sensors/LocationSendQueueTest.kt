package io.homeassistant.companion.android.sensors

import android.location.Location
import io.homeassistant.companion.android.sensors.LocationSensorManager.LocationUpdateTrigger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SERVER_A = 1
private const val SERVER_B = 2

@OptIn(ExperimentalCoroutinesApi::class)
class LocationSendQueueTest {

    private data class Delivery(val serverId: Int, val queued: QueuedLocation)

    private val sent = mutableListOf<Delivery>()
    private val superseded = mutableListOf<Delivery>()

    /** Sends whose fix time has a gate here suspend until the gate completes, simulating an in-flight HTTP call. */
    private val gates = mutableMapOf<Long, CompletableDeferred<Unit>>()

    private val queue = LocationSendQueue(
        send = { serverId, queued ->
            sent += Delivery(serverId, queued)
            gates[queued.location.time]?.await()
        },
        onSuperseded = { serverId, queued -> superseded += Delivery(serverId, queued) },
    )

    private fun fix(fixTime: Long): Location = mockk { every { time } returns fixTime }

    private fun hold(fixTime: Long): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { gates[fixTime] = it }

    private fun queued(
        fixTime: Long,
        trigger: LocationUpdateTrigger = LocationUpdateTrigger.BACKGROUND_LOCATION,
    ) = QueuedLocation(fix(fixTime), trigger)

    private fun sentTimes() = sent.map { it.queued.location.time }

    private fun supersededTimes() = superseded.map { it.queued.location.time }

    @Test
    fun `Given a send in flight when a newer fix arrives then it is sent after the first completes`() = runTest {
        val firstSend = hold(1)
        launch { queue.submit(SERVER_A, queued(1)) }
        runCurrent()
        launch { queue.submit(SERVER_A, queued(2)) }
        runCurrent()

        assertEquals(listOf(1L), sentTimes())

        firstSend.complete(Unit)
        runCurrent()

        assertEquals(listOf(1L, 2L), sentTimes())
        assertTrue(superseded.isEmpty())
    }

    @Test
    fun `Given fixes queued behind an in-flight send when it completes then only the newest is sent`() = runTest {
        val firstSend = hold(1)
        launch { queue.submit(SERVER_A, queued(1)) }
        runCurrent()
        launch { queue.submit(SERVER_A, queued(2)) }
        launch { queue.submit(SERVER_A, queued(3)) }
        runCurrent()
        firstSend.complete(Unit)
        runCurrent()

        assertEquals(listOf(1L, 3L), sentTimes())
        assertEquals(listOf(2L), supersededTimes())
    }

    @Test
    fun `Given a newer fix pending when an older fix arrives then the older fix is superseded without being sent`() = runTest {
        val firstSend = hold(1)
        launch { queue.submit(SERVER_A, queued(1)) }
        runCurrent()
        launch { queue.submit(SERVER_A, queued(3)) }
        launch { queue.submit(SERVER_A, queued(2)) }
        runCurrent()

        assertEquals(listOf(2L), supersededTimes())

        firstSend.complete(Unit)
        runCurrent()

        assertEquals(listOf(1L, 3L), sentTimes())
    }

    @Test
    fun `Given a geofence fix pending when a newer background fix replaces it then the sent fix bypasses the debounce`() = runTest {
        val firstSend = hold(1)
        launch { queue.submit(SERVER_A, queued(1)) }
        runCurrent()
        launch { queue.submit(SERVER_A, queued(2, LocationUpdateTrigger.GEOFENCE_ENTER)) }
        launch { queue.submit(SERVER_A, queued(3)) }
        runCurrent()
        firstSend.complete(Unit)
        runCurrent()

        assertEquals(listOf(2L), supersededTimes())
        val last = sent.last().queued
        assertEquals(3L, last.location.time)
        assertEquals(LocationUpdateTrigger.BACKGROUND_LOCATION, last.trigger)
        assertTrue(last.bypassDebounce)
    }

    @Test
    fun `Given a background fix pending when a geofence copy with the same time arrives then the pending fix bypasses the debounce`() = runTest {
        val firstSend = hold(1)
        launch { queue.submit(SERVER_A, queued(1)) }
        runCurrent()
        launch { queue.submit(SERVER_A, queued(2)) }
        launch { queue.submit(SERVER_A, queued(2, LocationUpdateTrigger.GEOFENCE_ENTER)) }
        runCurrent()
        firstSend.complete(Unit)
        runCurrent()

        assertEquals(listOf(LocationUpdateTrigger.GEOFENCE_ENTER), superseded.map { it.queued.trigger })
        val last = sent.last().queued
        assertEquals(2L, last.location.time)
        assertEquals(LocationUpdateTrigger.BACKGROUND_LOCATION, last.trigger)
        assertTrue(last.bypassDebounce)
    }

    @Test
    fun `Given background fixes only when one replaces another then the sent fix does not bypass the debounce`() = runTest {
        val firstSend = hold(1)
        launch { queue.submit(SERVER_A, queued(1)) }
        runCurrent()
        launch { queue.submit(SERVER_A, queued(2)) }
        launch { queue.submit(SERVER_A, queued(3)) }
        runCurrent()
        firstSend.complete(Unit)
        runCurrent()

        assertFalse(sent.last().queued.bypassDebounce)
    }

    @Test
    fun `Given a send to one server in flight when a fix arrives for another server then it is sent immediately`() = runTest {
        val serverASend = hold(1)
        launch { queue.submit(SERVER_A, queued(1)) }
        runCurrent()
        launch { queue.submit(SERVER_B, queued(2)) }
        runCurrent()

        assertEquals(listOf(SERVER_A, SERVER_B), sent.map { it.serverId })
        assertEquals(listOf(1L, 2L), sentTimes())

        serverASend.complete(Unit)
    }
}
