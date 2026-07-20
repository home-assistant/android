package io.homeassistant.companion.android.common.data.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryOptions
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistrySensorOptions
import io.homeassistant.companion.android.common.util.SdkVersion
import java.time.LocalDateTime
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK_VERSION_O = 26

private const val SDK_VERSION_BEFORE_O = 25

/** 2024-01-01T12:00:00Z, the instant of [TIMESTAMP_STATE]. */
private const val TIMESTAMP_EPOCH_MILLIS = 1704110400000L

private const val TIMESTAMP_STATE = "2024-01-01T12:00:00+00:00"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class FriendlyStateTest {

    private lateinit var context: Context
    private lateinit var initialLocale: Locale

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        initialLocale = Locale.getDefault()
        // Precision formatting and title casing use the default locale, pin it so assertions are stable
        Locale.setDefault(Locale.US)
        SdkVersion.sdkInt = SDK_VERSION_O
    }

    @After
    fun tearDown() {
        Locale.setDefault(initialLocale)
        SdkVersion.resetSdkInt()
    }

    private fun createEntity(
        entityId: String = "light.living_room",
        state: String = "on",
        attributes: Map<String, Any?> = mapOf("friendly_name" to "Living Room Light"),
    ) = Entity(
        entityId = entityId,
        state = state,
        attributes = attributes,
        lastChanged = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
        lastUpdated = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
    )

    private fun binarySensor(deviceClass: String, state: String) = createEntity(
        entityId = "binary_sensor.test",
        state = state,
        attributes = mapOf("device_class" to deviceClass),
    )

    private fun assertResource(expectedResId: Int, actual: FriendlyState) {
        assertEquals(FriendlyState.Resource(expectedResId), actual)
    }

    // region State to resource mapping

    @Test
    fun `Given a binary sensor with a device class when getting friendly state then maps to the device class resource`() {
        assertEquals(
            FriendlyState.Resource(commonR.string.state_open),
            binarySensor(deviceClass = "door", state = "on").friendlyState(displayPrecision = null),
        )
        assertEquals(
            FriendlyState.Resource(commonR.string.state_closed),
            binarySensor(deviceClass = "door", state = "off").friendlyState(displayPrecision = null),
        )
        assertEquals(
            FriendlyState.Resource(commonR.string.state_low),
            binarySensor(deviceClass = "battery", state = "on").friendlyState(displayPrecision = null),
        )
    }

    @Test
    fun `Given a binary sensor with an unknown device class when getting friendly state then falls back to on and off`() {
        assertEquals(
            FriendlyState.Resource(commonR.string.state_on),
            binarySensor(deviceClass = "not_a_device_class", state = "on").friendlyState(displayPrecision = null),
        )
        assertEquals(
            FriendlyState.Resource(commonR.string.state_off),
            binarySensor(deviceClass = "not_a_device_class", state = "off").friendlyState(displayPrecision = null),
        )
    }

    @Test
    fun `Given a non binary sensor with a known state when getting friendly state then maps to the state resource`() {
        val entity = createEntity(entityId = "lock.front_door", state = "locked")

        assertResource(commonR.string.state_locked, entity.friendlyState(displayPrecision = null))
    }

    @Test
    fun `Given a device class only meaningful for binary sensors when getting friendly state then it is ignored`() {
        // The device class branch must not apply outside of the binary_sensor domain: a sensor
        // reporting "on" maps through the generic state table, not to "Open".
        val entity = createEntity(
            entityId = "sensor.door_state",
            state = "on",
            attributes = mapOf("device_class" to "door"),
        )

        assertResource(commonR.string.state_on, entity.friendlyState(displayPrecision = null))
    }

    @Test
    fun `Given an unknown state when getting friendly state then returns a title cased literal`() {
        val entity = createEntity(entityId = "vacuum.robot", state = "deep_cleaning_floor")

        assertEquals(
            FriendlyState.Literal("Deep Cleaning Floor"),
            entity.friendlyState(displayPrecision = null),
        )
    }

    // endregion

    // region Timestamps

    @Test
    fun `Given an ISO timestamp state when getting friendly state then returns the instant as relative time`() {
        val entity = createEntity(entityId = "sensor.last_boot", state = TIMESTAMP_STATE)

        assertEquals(
            FriendlyState.RelativeTime(TIMESTAMP_EPOCH_MILLIS),
            entity.friendlyState(displayPrecision = null),
        )
    }

    @Test
    fun `Given an ISO timestamp state below Android O when getting friendly state then falls back to a literal`() {
        SdkVersion.sdkInt = SDK_VERSION_BEFORE_O
        val entity = createEntity(entityId = "sensor.last_boot", state = TIMESTAMP_STATE)

        assertEquals(
            FriendlyState.Literal(TIMESTAMP_STATE),
            entity.friendlyState(displayPrecision = null),
        )
    }

    @Test
    fun `Given a timestamp state with a display precision when getting friendly state then relative time wins`() {
        // A timestamp is never numeric, so precision must not be attempted on it
        val entity = createEntity(entityId = "sensor.last_boot", state = TIMESTAMP_STATE)

        assertInstanceOf(
            FriendlyState.RelativeTime::class.java,
            entity.friendlyState(displayPrecision = 2),
        )
    }

    // endregion

    // region Precision

    @Test
    fun `Given a numeric sensor state with a display precision when getting friendly state then the state is rounded`() {
        val entity = createEntity(entityId = "sensor.temperature", state = "20.126456")

        assertEquals(FriendlyState.Literal("20.13"), entity.friendlyState(displayPrecision = 2))
        assertEquals(FriendlyState.Literal("20"), entity.friendlyState(displayPrecision = 0))
    }

    @Test
    fun `Given no display precision when getting friendly state then the numeric state is unchanged`() {
        val entity = createEntity(entityId = "sensor.temperature", state = "20.126456")

        assertEquals(FriendlyState.Literal("20.126456"), entity.friendlyState(displayPrecision = null))
    }

    @Test
    fun `Given a non numeric sensor state with a display precision when getting friendly state then precision is skipped`() {
        val entity = createEntity(entityId = "sensor.status", state = "custom_state")

        assertEquals(FriendlyState.Literal("Custom State"), entity.friendlyState(displayPrecision = 2))
    }

    @Test
    fun `Given a numeric non sensor state with a display precision when getting friendly state then precision is not applied`() {
        val entity = createEntity(entityId = "input_number.slider", state = "20.126456")

        assertEquals(FriendlyState.Literal("20.126456"), entity.friendlyState(displayPrecision = 2))
    }

    // endregion

    // region Unit of measurement

    @Test
    fun `Given a unit of measurement when appending it then the state is wrapped with the unit`() {
        val entity = createEntity(
            entityId = "sensor.temperature",
            state = "20.126456",
            attributes = mapOf("unit_of_measurement" to "°C"),
        )

        assertEquals(
            FriendlyState.WithUnit(FriendlyState.Literal("20.13"), "°C"),
            entity.friendlyState(displayPrecision = 2, appendUnitOfMeasurement = true),
        )
    }

    @Test
    fun `Given a unit of measurement when not appending it then the state is not wrapped`() {
        val entity = createEntity(
            entityId = "sensor.temperature",
            state = "20.126456",
            attributes = mapOf("unit_of_measurement" to "°C"),
        )

        assertEquals(
            FriendlyState.Literal("20.126456"),
            entity.friendlyState(displayPrecision = null, appendUnitOfMeasurement = false),
        )
    }

    @Test
    fun `Given a blank unit of measurement when appending it then the state is not wrapped`() {
        val entity = createEntity(
            entityId = "sensor.temperature",
            state = "20.126456",
            attributes = mapOf("unit_of_measurement" to " "),
        )

        assertEquals(
            FriendlyState.Literal("20.126456"),
            entity.friendlyState(displayPrecision = null, appendUnitOfMeasurement = true),
        )
    }

    @Test
    fun `Given a translated state with a unit of measurement when appending it then the resource keeps the unit`() {
        val entity = createEntity(
            entityId = "sensor.status",
            state = "on",
            attributes = mapOf("unit_of_measurement" to "W"),
        )

        assertEquals(
            FriendlyState.WithUnit(FriendlyState.Resource(commonR.string.state_on), "W"),
            entity.friendlyState(displayPrecision = null, appendUnitOfMeasurement = true),
        )
    }

    // endregion

    // region Resolving

    @Test
    fun `Given a resource state when resolving then returns the localized string`() {
        assertEquals("Open", FriendlyState.Resource(commonR.string.state_open).resolve(context))
    }

    @Test
    fun `Given a literal state when resolving then returns the value unchanged`() {
        assertEquals("20.13", FriendlyState.Literal("20.13").resolve(context))
    }

    @Test
    fun `Given a state with a unit when resolving then the unit is separated by a space`() {
        val state = FriendlyState.WithUnit(FriendlyState.Resource(commonR.string.state_on), "W")

        assertEquals("On W", state.resolve(context))
    }

    @Test
    fun `Given a relative time state when resolving then returns a relative description of now`() {
        val twoHoursAgo = System.currentTimeMillis() - 2.hours.inWholeMilliseconds

        assertEquals("2 hr. ago", FriendlyState.RelativeTime(twoHoursAgo).resolve(context))
    }

    // endregion

    // region Public context resolving overloads

    private fun assertPrecisionFromOptions(displayPrecision: Int?, suggestedDisplayPrecision: Int?, expected: String) {
        val entity = createEntity(entityId = "sensor.temperature", state = "20.126456")
        val options = EntityRegistryOptions(
            sensor = EntityRegistrySensorOptions(
                displayPrecision = displayPrecision,
                suggestedDisplayPrecision = suggestedDisplayPrecision,
            ),
        )

        assertEquals(expected, entity.friendlyState(context, options))
    }

    @Test
    fun `Given registry options with a display precision when getting friendly state then it is applied`() {
        assertPrecisionFromOptions(displayPrecision = 2, suggestedDisplayPrecision = null, expected = "20.13")
    }

    @Test
    fun `Given registry options with only a suggested precision when getting friendly state then it is applied`() {
        assertPrecisionFromOptions(displayPrecision = null, suggestedDisplayPrecision = 1, expected = "20.1")
    }

    @Test
    fun `Given registry options with both precisions when getting friendly state then display precision takes priority`() {
        assertPrecisionFromOptions(displayPrecision = 3, suggestedDisplayPrecision = 1, expected = "20.126")
    }

    @Test
    fun `Given no registry options when getting friendly state then the numeric state is unchanged`() {
        val entity = createEntity(entityId = "sensor.temperature", state = "20.126456")

        assertEquals("20.126456", entity.friendlyState(context))
    }

    @Test
    fun `Given registry options and a unit when appending the unit then the rounded state includes it`() {
        val entity = createEntity(
            entityId = "sensor.temperature",
            state = "20.126456",
            attributes = mapOf("unit_of_measurement" to "°C"),
        )
        val options = EntityRegistryOptions(sensor = EntityRegistrySensorOptions(displayPrecision = 2))

        assertEquals("20.13 °C", entity.friendlyState(context, options, appendUnitOfMeasurement = true))
    }

    // endregion
}
