package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase.Companion.ACTION_UPDATE_SENSOR
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase.Companion.EXTRA_SENSOR_ID
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.mdiName
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ActivitySensorManagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private val context = mockk<Context>(relaxed = true)
    private val repository = mockk<SensorRepository>(relaxed = true)
    private val confidence = ActivitySensorManager.sleepConfidence
    private val intent = Intent(ActivitySensorManager.ACTION_SLEEP_ACTIVITY)
    private val broadcasts = mutableListOf<Intent>()
    private val updates = mutableListOf<Sensor>()
    private val attributes = mutableListOf<List<Attribute>>()
    private lateinit var manager: ActivitySensorManager

    @Before
    fun setUp() {
        mockkStatic("kotlinx.coroutines.CoroutineScopeKt")
        every { CoroutineScope(any()) } returns scope
        mockkStatic(SleepClassifyEvent::class, SleepSegmentEvent::class)
        every { SleepClassifyEvent.hasEvents(intent) } returns true
        every { SleepClassifyEvent.extractEvents(intent) } returns listOf(classification())
        every { SleepSegmentEvent.hasEvents(intent) } returns false
        every { context.checkPermission(Manifest.permission.ACTIVITY_RECOGNITION, any(), any()) } returns
            PackageManager.PERMISSION_GRANTED
        coEvery { repository.get(confidence.id) } returns listOf(sensor())
        coEvery { repository.update(capture(updates)) } returns Unit
        coEvery { repository.replaceAllAttributes(confidence.id, capture(attributes)) } returns Unit
        every { context.sendBroadcast(capture(broadcasts)) } answers {
            // Delivery must be requested only after the real onSensorUpdated has persisted both.
            assertEquals(1, updates.size)
            assertEquals(1, attributes.size)
        }
        manager = ActivitySensorManager(context, repository, mockk(), mockk())
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkAll()
    }

    @Test
    fun `Given classifications when received then persists latest event and requests only confidence delivery`() = runTest(dispatcher) {
        every { SleepClassifyEvent.extractEvents(intent) } returns listOf(
            classification(confidence = 12),
            classification(confidence = 87),
        )

        manager.onReceive(intent)
        scope.coroutineContext.job.children.toList().joinAll()

        assertEquals("87", updates.single().state)
        assertEquals("int", updates.single().stateType)
        assertEquals(confidence.statelessIcon.mdiName, updates.single().icon)
        assertEquals(
            mapOf("light" to "3", "motion" to "4", "timestamp" to "123000"),
            attributes.single().associate { it.name to it.value },
        )
        assertEquals(ACTION_UPDATE_SENSOR, broadcasts.single().action)
        assertEquals(SensorReceiver::class.java.name, broadcasts.single().component?.className)
        assertEquals(confidence.id, broadcasts.single().getStringExtra(EXTRA_SENSOR_ID))
    }

    @Test
    fun `Given disabled confidence when classification received then does not persist or send`() = runTest(dispatcher) {
        coEvery { repository.get(confidence.id) } returns listOf(sensor(enabled = false))

        manager.onReceive(intent)
        scope.coroutineContext.job.children.toList().joinAll()

        assertNoUpdate()
    }

    @Test
    fun `Given missing permission when classification received then does not persist or send`() = runTest(dispatcher) {
        every { context.checkPermission(Manifest.permission.ACTIVITY_RECOGNITION, any(), any()) } returns
            PackageManager.PERMISSION_DENIED

        manager.onReceive(intent)
        scope.coroutineContext.job.children.toList().joinAll()

        assertNoUpdate()
    }

    @Test
    fun `Given empty classifications when received then does not persist or send`() = runTest(dispatcher) {
        every { SleepClassifyEvent.extractEvents(intent) } returns emptyList()

        manager.onReceive(intent)
        scope.coroutineContext.job.children.toList().joinAll()

        assertNoUpdate()
    }

    @Test
    fun `Given no sleep events when received then does not persist or send`() = runTest(dispatcher) {
        every { SleepClassifyEvent.hasEvents(intent) } returns false

        manager.onReceive(intent)
        scope.coroutineContext.job.children.toList().joinAll()

        assertNoUpdate()
    }

    @Test
    fun `Given a sleep segment when received then still persists it without requesting confidence delivery`() = runTest(dispatcher) {
        val segment = ActivitySensorManager.sleepSegment
        every { SleepClassifyEvent.hasEvents(intent) } returns false
        every { SleepSegmentEvent.hasEvents(intent) } returns true
        every { SleepSegmentEvent.extractEvents(intent) } returns listOf(
            mockk<SleepSegmentEvent> {
                every { segmentDurationMillis } returns 60000L
                every { startTimeMillis } returns 1000L
                every { endTimeMillis } returns 61000L
                every { status } returns SleepSegmentEvent.STATUS_SUCCESSFUL
            },
        )
        coEvery { repository.get(segment.id) } returns listOf(sensor().copy(id = segment.id))
        coEvery { repository.replaceAllAttributes(segment.id, capture(attributes)) } returns Unit

        manager.onReceive(intent)
        scope.coroutineContext.job.children.toList().joinAll()

        assertEquals(segment.id, updates.single().id)
        assertEquals("60000", updates.single().state)
        assertEquals(
            mapOf("start" to "1000", "end" to "61000", "status" to "successful"),
            attributes.single().associate { it.name to it.value },
        )
        verify(exactly = 0) { context.sendBroadcast(any()) }
    }

    private fun assertNoUpdate() {
        assertEquals(emptyList<Sensor>(), updates)
        assertEquals(emptyList<List<Attribute>>(), attributes)
        verify(exactly = 0) { context.sendBroadcast(any()) }
    }

    private fun sensor(enabled: Boolean = true) = Sensor(
        id = confidence.id,
        serverId = 1,
        enabled = enabled,
        state = "0",
    )

    private fun classification(confidence: Int = 87) = mockk<SleepClassifyEvent> {
        every { getConfidence() } returns confidence
        every { light } returns 3
        every { motion } returns 4
        every { timestampMillis } returns 123000L
    }
}
