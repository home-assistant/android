package io.homeassistant.companion.android.common.data.integration

import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedEntityRemoved
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedEntityState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateDiff
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class EntityTest {

    private val baseDateTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0)

    private val newDateTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0)

    private val newDateTimeEpoch = newDateTime.toEpochSecond(ZoneOffset.UTC).toDouble()

    private fun createEntity(
        entityId: String = "light.living_room",
        state: String = "on",
        attributes: Map<String, Any?> = mapOf("friendly_name" to "Living Room Light"),
    ) = Entity(
        entityId = entityId,
        state = state,
        attributes = attributes,
        lastChanged = baseDateTime,
        lastUpdated = baseDateTime,
    )

    @Nested
    inner class DomainProperty {
        @ParameterizedTest(name = "entityId={0} -> domain={1}")
        @CsvSource(
            "light.living_room, light",
            "device_tracker.phone.location, device_tracker",
            "invalid_entity_id, invalid_entity_id",
            "'', ''",
            ".light, ''",
        )
        fun `Given entityId when accessing domain then returns expected value`(
            entityId: String,
            expectedDomain: String,
        ) {
            val entity = createEntity(entityId = entityId)
            assertEquals(expectedDomain, entity.domain)
        }
    }

    @Nested
    inner class FriendlyNameProperty {
        @Test
        fun `Given friendly_name attribute when accessing friendlyName then returns it`() {
            val entity = createEntity(attributes = mapOf("friendly_name" to "Living Room Light"))
            assertEquals("Living Room Light", entity.friendlyName)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "   "])
        fun `Given blank friendly_name attribute when accessing friendlyName then returns entityId`(name: String) {
            val entity = createEntity(attributes = mapOf("friendly_name" to name))
            assertEquals("light.living_room", entity.friendlyName)
        }

        @Test
        fun `Given no friendly_name attribute when accessing friendlyName then returns entityId`() {
            val entity = createEntity(attributes = emptyMap())
            assertEquals("light.living_room", entity.friendlyName)
        }

        @Test
        fun `Given null friendly_name attribute when accessing friendlyName then returns entityId`() {
            val entity = createEntity(attributes = mapOf("friendly_name" to null))
            assertEquals("light.living_room", entity.friendlyName)
        }

        @Test
        fun `Given non-string friendly_name attribute when accessing friendlyName then returns its string value`() {
            val entity = createEntity(attributes = mapOf("friendly_name" to 42))
            assertEquals("42", entity.friendlyName)
        }
    }

    @Nested
    inner class Deserialization {
        private fun entityJson(stateValue: String) = """
            {
                "entity_id": "sensor.temperature",
                "state": $stateValue,
                "attributes": {"friendly_name": "Temperature"},
                "last_changed": "2024-01-01T12:00:00",
                "last_updated": "2024-01-01T12:00:00"
            }
        """.trimIndent()

        @Test
        fun `Given string state when deserializing then returns state value`() {
            val entity = kotlinJsonMapper.decodeFromString<Entity>(entityJson("\"on\""))
            assertEquals("on", entity.state)
        }

        @Test
        fun `Given empty string state when deserializing then returns empty state`() {
            val entity = kotlinJsonMapper.decodeFromString<Entity>(entityJson("\"\""))
            assertEquals("", entity.state)
        }

        @Test
        fun `Given numeric state when deserializing then returns empty state`() {
            val entity = kotlinJsonMapper.decodeFromString<Entity>(entityJson("42"))
            assertEquals("", entity.state)
            assertEquals("sensor.temperature", entity.entityId)
        }

        @Test
        fun `Given boolean state when deserializing then returns empty state`() {
            val entity = kotlinJsonMapper.decodeFromString<Entity>(entityJson("true"))
            assertEquals("", entity.state)
        }

        @Test
        fun `Given null state when deserializing then returns empty state`() {
            val entity = kotlinJsonMapper.decodeFromString<Entity>(entityJson("null"))
            assertEquals("", entity.state)
        }

        @Test
        fun `Given string state when serializing round-trip then preserves state`() {
            val entity = createEntity(state = "unavailable")
            val json = kotlinJsonMapper.encodeToString(entity)
            val deserialized = kotlinJsonMapper.decodeFromString<Entity>(json)
            assertEquals("unavailable", deserialized.state)
        }
    }

    @Nested
    inner class GetIcon {
        @Test
        fun `Given blank state and non-string state attribute when getting icon then does not throw`() {
            val entity = createEntity(
                entityId = "sensor.test",
                state = "",
                attributes = mapOf("state" to 42),
            )
            assertDoesNotThrow { entity.getIcon() }
        }

        @ParameterizedTest
        @ValueSource(strings = ["mdi:", "mdi:abcdefgh"])
        fun `Given invalid mdi icon attribute when getting icon then returns fallback`(iconAttr: String) {
            val entity = createEntity(
                entityId = "sensor.test",
                state = "42",
                attributes = mapOf("icon" to iconAttr),
            )
            val icon = entity.getIcon()
            assertEquals(Icon.cmd_bookmark, icon)
        }

        @ParameterizedTest
        @ValueSource(strings = ["mdicustom:abcdefgh", "hue:bulb-filament"])
        fun `Given custom non-mdi icon attribute when getting icon then returns domain default`(iconAttr: String) {
            val entity = createEntity(
                entityId = "sensor.test",
                state = "42",
                attributes = mapOf("icon" to iconAttr),
            )
            val icon = entity.getIcon()
            assertEquals(Icon.cmd_eye, icon)
        }
    }

    @Nested
    inner class ApplyCompressedStateDiff {
        @Test
        fun `Given empty diff when applying then returns entity with same values`() {
            val entity = createEntity()
            val diff = CompressedStateDiff(plus = null, minus = null)

            val result = entity.applyCompressedStateDiff(diff)

            assertEquals(entity.entityId, result.entityId)
            assertEquals(entity.state, result.state)
            assertSame(entity.attributes, result.attributes)
        }

        @Test
        fun `Given diff with state change when applying then updates state`() {
            val entity = createEntity(state = "on")
            val diff = CompressedStateDiff(plus = CompressedEntityState(state = JsonPrimitive("off")))

            val result = entity.applyCompressedStateDiff(diff)

            assertEquals("off", result.state)
        }

        @Test
        fun `Given diff with attribute changes when applying then merges correctly`() {
            val entity = createEntity(
                attributes = mapOf("friendly_name" to "Light", "old_attr" to "value"),
            )
            val diff = CompressedStateDiff(
                plus = CompressedEntityState(attributes = mapOf("new_attr" to "new_value")),
                minus = CompressedEntityRemoved(attributes = listOf("old_attr")),
            )

            val result = entity.applyCompressedStateDiff(diff)

            assertEquals(
                mapOf<String, Any?>("friendly_name" to "Light", "new_attr" to "new_value"),
                result.attributes,
            )
        }

        @Test
        fun `Given diff with lastChanged when applying then updates both timestamps`() {
            val entity = createEntity()
            val diff = CompressedStateDiff(
                plus = CompressedEntityState(lastChanged = newDateTimeEpoch),
            )

            val result = entity.applyCompressedStateDiff(diff)

            // Verify timestamps actually changed from original
            assertNotEquals(baseDateTime, result.lastChanged)
            assertEquals(newDateTime, result.lastUpdated)
            assertEquals(result.lastChanged, result.lastUpdated)
        }

        @Test
        fun `Given diff with only lastUpdated when applying then preserves lastChanged`() {
            val entity = createEntity()
            val diff = CompressedStateDiff(
                plus = CompressedEntityState(lastUpdated = newDateTimeEpoch),
            )

            val result = entity.applyCompressedStateDiff(diff)

            assertEquals(baseDateTime, result.lastChanged)
            assertEquals(newDateTime, result.lastUpdated)
        }
    }

    @Nested
    inner class SupportsFeature {

        @Test
        fun `Given a feature in the bitmask when checking support then only its flags are supported`() {
            val entity = createEntity(attributes = mapOf("supported_features" to 5))

            assertTrue(entity.supportsFeature(1))
            assertTrue(entity.supportsFeature(4))
            assertFalse(entity.supportsFeature(2))
        }

        @Test
        fun `Given one of the requested features in the bitmask when checking support then it is supported`() {
            val entity = createEntity(attributes = mapOf("supported_features" to 4))

            assertTrue(entity.supportsFeature(1 or 4))
        }

        @Test
        fun `Given a bitmask serialized as another number type when checking support then it is supported`() {
            assertTrue(createEntity(attributes = mapOf("supported_features" to 4L)).supportsFeature(4))
            assertTrue(createEntity(attributes = mapOf("supported_features" to 4.0)).supportsFeature(4))
        }

        @Test
        fun `Given no or non numeric supported_features when checking support then it is not supported`() {
            assertFalse(createEntity(attributes = emptyMap()).supportsFeature(1))
            assertFalse(createEntity(attributes = mapOf("supported_features" to "4")).supportsFeature(4))
        }
    }

    @Nested
    inner class ControlGroups {

        @ParameterizedTest
        @ValueSource(strings = ["number", "input_number"])
        fun `Given a number entity when getting number controls then range and step are resolved`(domain: String) {
            val entity = createEntity(
                entityId = "$domain.threshold",
                state = "7.5",
                attributes = mapOf("min" to 5, "max" to 30, "step" to 0.5),
            )

            val controls = checkNotNull(entity.getNumberControls())
            assertEquals(EntityPosition(value = 7.5f, min = 5f, max = 30f), controls.range)
            assertEquals(0.5f, controls.step)
        }

        @Test
        fun `Given not a number entity when getting number controls then they are null`() {
            assertNull(createEntity(entityId = "sensor.value", state = "7.5").getNumberControls())
        }

        @Test
        fun `Given a media player supporting volume when getting media player controls then volume is resolved`() {
            val entity = createEntity(
                entityId = "media_player.tv",
                attributes = mapOf("supported_features" to 4, "volume_level" to 0.5, "volume_step" to 0.05),
            )

            val controls = checkNotNull(entity.getMediaPlayerControls())
            assertEquals(50f, controls.volume?.value)
            assertEquals(0.05f, controls.volumeStep)
        }

        @Test
        fun `Given a media player without volume support when getting media player controls then volume is null`() {
            val entity = createEntity(entityId = "media_player.tv", attributes = mapOf("supported_features" to 0))

            val controls = checkNotNull(entity.getMediaPlayerControls())
            assertNull(controls.volume)
        }

        @Test
        fun `Given not a media player when getting media player controls then they are null`() {
            assertNull(createEntity().getMediaPlayerControls())
        }

        @Test
        fun `Given a cover supporting set position when getting cover controls then position is resolved`() {
            val entity = createEntity(
                entityId = "cover.blinds",
                state = "open",
                attributes = mapOf("supported_features" to 4, "current_position" to 40),
            )

            val controls = checkNotNull(entity.getCoverControls())
            assertEquals(true, controls.supportsSetPosition)
            assertEquals(40f, controls.position?.value)
        }

        @Test
        fun `Given a cover without set position support when getting cover controls then it is not supported`() {
            val entity = createEntity(
                entityId = "cover.blinds",
                state = "open",
                attributes = mapOf("supported_features" to 0),
            )

            assertEquals(false, checkNotNull(entity.getCoverControls()).supportsSetPosition)
        }

        @Test
        fun `Given a vacuum when getting vacuum controls then turn on support is resolved`() {
            val supported = createEntity(entityId = "vacuum.roomba", attributes = mapOf("supported_features" to 1))
            val unsupported = createEntity(entityId = "vacuum.roomba", attributes = mapOf("supported_features" to 2))

            assertEquals(true, checkNotNull(supported.getVacuumControls()).supportsTurnOn)
            assertEquals(false, checkNotNull(unsupported.getVacuumControls()).supportsTurnOn)
            assertNull(createEntity().getVacuumControls())
        }

        @Test
        fun `Given a climate entity when getting climate controls then range unit and modes are resolved`() {
            val entity = createEntity(
                entityId = "climate.thermostat",
                state = "heat",
                attributes = mapOf(
                    "min_temp" to 7,
                    "max_temp" to 35,
                    "temperature_unit" to "°C",
                    "hvac_modes" to listOf("heat", "off"),
                    "supported_features" to 1,
                ),
            )

            val controls = checkNotNull(entity.getClimateControls())
            assertEquals(7f, controls.minTemperature)
            assertEquals(35f, controls.maxTemperature)
            assertEquals("°C", controls.temperatureUnit)
            assertEquals(listOf("heat", "off"), controls.hvacModes)
            assertEquals(true, controls.supportsTargetTemperature)
        }

        @Test
        fun `Given a climate entity without target temperature support when getting climate controls then it is not supported`() {
            val entity = createEntity(
                entityId = "climate.thermostat",
                state = "heat",
                attributes = mapOf("supported_features" to 128),
            )

            assertEquals(false, checkNotNull(entity.getClimateControls()).supportsTargetTemperature)
        }
    }

    @Nested
    inner class DisplayAttributes {

        @Test
        fun `Given device_class and entity_picture attributes when accessing them then they are returned`() {
            val entity = createEntity(
                entityId = "cover.garage",
                attributes = mapOf("device_class" to "garage", "entity_picture" to "/api/camera_proxy/camera.door"),
            )

            assertEquals("garage", entity.deviceClass())
            assertEquals("/api/camera_proxy/camera.door", entity.entityPicturePath())
        }

        @Test
        fun `Given no device_class and a blank entity_picture when accessing them then they are null`() {
            val entity = createEntity(attributes = mapOf("entity_picture" to " "))

            assertNull(entity.deviceClass())
            assertNull(entity.entityPicturePath())
        }
    }
}
