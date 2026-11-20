package io.homeassistant.companion.android.common.bluetooth.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import io.homeassistant.companion.android.common.sensors.BluetoothSensorManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.UUID
import org.altbeacon.beacon.BeaconTransmitter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TransmitterManagerTest {

    private lateinit var context: Context
    private lateinit var bluetoothAdapter: BluetoothAdapter

    @BeforeEach
    fun setUp() {
        TransmitterManager.resetForTesting()
        bluetoothAdapter = mockk(relaxed = true) {
            every { isEnabled } returns true
        }
        val bluetoothManager = mockk<BluetoothManager>(relaxed = true) {
            every { adapter } returns bluetoothAdapter
        }
        context = mockk(relaxed = true) {
            every { getSystemService(BluetoothManager::class.java) } returns bluetoothManager
        }
    }

    @AfterEach
    fun tearDown() {
        TransmitterManager.resetForTesting()
        unmockkAll()
    }

    @Test
    fun `Given start succeeds when starting transmitter then state is Transmitting`() {
        val transmitter = mockTransmitter() succeedingWith {}
        TransmitterManager.transmitterFactory = { _, _ -> transmitter }
        val device = device()

        TransmitterManager.startTransmitting(context, device)

        assertTrue(device.transmitting)
        assertEquals("Transmitting", device.state)
        verify(exactly = 1) { transmitter.startAdvertising(any(), any()) }
    }

    @Test
    fun `Given start fails with already started when retry succeeds then state is Transmitting with a fresh transmitter`() {
        val first = mockTransmitter() failingWith AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED
        val second = mockTransmitter() succeedingWith {}
        val created = ArrayDeque(listOf(first, second))
        TransmitterManager.transmitterFactory = { _, _ -> created.removeFirst() }
        val device = device()

        TransmitterManager.startTransmitting(context, device)

        assertTrue(device.transmitting)
        assertEquals("Transmitting", device.state)
        assertTrue(created.isEmpty(), "Expected exactly one retry with a fresh transmitter")
        verify(exactly = 1) { first.startAdvertising(any(), any()) }
        verify(exactly = 1) { second.startAdvertising(any(), any()) }
    }

    @Test
    fun `Given start fails with already started when retry also fails then state is Unable to transmit and identifiers are kept`() {
        val first = mockTransmitter() failingWith AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED
        val second = mockTransmitter() failingWith AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED
        val created = ArrayDeque(listOf(first, second))
        TransmitterManager.transmitterFactory = { _, _ -> created.removeFirst() }
        val device = device()
        val uuid = device.uuid

        TransmitterManager.startTransmitting(context, device)

        assertFalse(device.transmitting)
        assertEquals("Unable to transmit", device.state)
        assertEquals(uuid, device.uuid)
        assertEquals("123", device.major)
        assertEquals("456", device.minor)
        assertTrue(created.isEmpty(), "Expected exactly one retry with a fresh transmitter")
    }

    @Test
    fun `Given start fails with another error when starting transmitter then state is Unable to transmit and identifiers are cleared`() {
        val transmitter = mockTransmitter() failingWith AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE
        TransmitterManager.transmitterFactory = { _, _ -> transmitter }
        val device = device()

        TransmitterManager.startTransmitting(context, device)

        assertFalse(device.transmitting)
        assertEquals("Unable to transmit", device.state)
        assertEquals("", device.uuid)
        assertEquals("", device.major)
        assertEquals("", device.minor)
        verify(exactly = 1) { transmitter.startAdvertising(any(), any()) }
    }

    @Test
    fun `Given restart is required when starting transmitter then the current advertisement is stopped before starting`() {
        var started = true
        val transmitter = mockk<BeaconTransmitter>(relaxed = true) {
            every { isStarted } answers { started }
            every { stopAdvertising() } answers { started = false }
        }
        every { transmitter.startAdvertising(any(), any()) } answers {
            val callback = secondArg<AdvertiseCallback>()
            callback.onStartSuccess(mockk<AdvertiseSettings>())
        }
        TransmitterManager.transmitterFactory = { _, _ -> transmitter }
        val device = device(transmitting = true, restartRequired = true)

        TransmitterManager.startTransmitting(context, device)

        verify(exactly = 1) { transmitter.stopAdvertising() }
        verify(exactly = 1) { transmitter.startAdvertising(any(), any()) }
        assertTrue(device.transmitting)
        assertEquals("Transmitting", device.state)
    }

    @Test
    fun `Given transmitter is already started when starting without restart then start is not attempted`() {
        var started = false
        val transmitter = mockk<BeaconTransmitter>(relaxed = true) {
            every { isStarted } answers { started }
        }
        every { transmitter.startAdvertising(any(), any()) } answers {
            started = true
            val callback = secondArg<AdvertiseCallback>()
            callback.onStartSuccess(mockk<AdvertiseSettings>())
        }
        TransmitterManager.transmitterFactory = { _, _ -> transmitter }
        val device = device()

        TransmitterManager.startTransmitting(context, device)
        TransmitterManager.startTransmitting(context, device)

        verify(exactly = 1) { transmitter.startAdvertising(any(), any()) }
        assertTrue(device.transmitting)
    }

    @Test
    fun `Given a start is pending when starting again then the second start is coalesced`() {
        var capturedCallback: AdvertiseCallback? = null
        val transmitter = mockk<BeaconTransmitter>(relaxed = true) {
            every { isStarted } returns false
        }
        every { transmitter.startAdvertising(any(), any()) } answers {
            capturedCallback = secondArg<AdvertiseCallback>()
        }
        TransmitterManager.transmitterFactory = { _, _ -> transmitter }
        val device = device()

        TransmitterManager.startTransmitting(context, device)
        TransmitterManager.startTransmitting(context, device)

        verify(exactly = 1) { transmitter.startAdvertising(any(), any()) }
        assertFalse(device.transmitting)

        capturedCallback?.onStartSuccess(mockk<AdvertiseSettings>())

        assertTrue(device.transmitting)
        assertEquals("Transmitting", device.state)
    }

    @Test
    fun `Given start fails with already started when retrying then the original transmitter is stopped before the fresh one starts`() {
        val first = mockTransmitter() failingWith AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED
        val second = mockTransmitter() succeedingWith {}
        val created = ArrayDeque(listOf(first, second))
        TransmitterManager.transmitterFactory = { _, _ -> created.removeFirst() }
        val device = device()

        TransmitterManager.startTransmitting(context, device)

        verifyOrder {
            first.stopAdvertising()
            second.startAdvertising(any(), any())
        }
        assertTrue(device.transmitting)
        assertEquals("Transmitting", device.state)
    }

    private fun device(
        transmitting: Boolean = false,
        restartRequired: Boolean = false,
    ) = IBeaconTransmitter(
        uuid = UUID.randomUUID().toString(),
        major = "123",
        minor = "456",
        transmitting = transmitting,
        transmitRequested = true,
        state = "",
        transmitPowerSetting = BluetoothSensorManager.BLE_TRANSMIT_LOW,
        measuredPowerSetting = -59,
        advertiseModeSetting = BluetoothSensorManager.BLE_ADVERTISE_LOW_POWER,
        restartRequired = restartRequired,
    )

    private infix fun BeaconTransmitter.succeedingWith(block: () -> Unit): BeaconTransmitter {
        every { startAdvertising(any(), any()) } answers {
            val callback = secondArg<AdvertiseCallback>()
            callback.onStartSuccess(mockk<AdvertiseSettings>())
            block()
        }
        return this
    }

    private infix fun BeaconTransmitter.failingWith(errorCode: Int): BeaconTransmitter {
        every { startAdvertising(any(), any()) } answers {
            val callback = secondArg<AdvertiseCallback>()
            callback.onStartFailure(errorCode)
        }
        return this
    }

    private fun mockTransmitter(): BeaconTransmitter = mockk(relaxed = true)
}
