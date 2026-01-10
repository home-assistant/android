package io.homeassistant.companion.android.common.data.integration

import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedEntityRemoved
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedEntityState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateDiff
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

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
            val diff = CompressedStateDiff(plus = CompressedEntityState(state = "off"))

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
}
