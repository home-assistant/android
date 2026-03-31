package io.homeassistant.companion.android.util.compose.entity

import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Unit tests for EntityPickerFilter fuzzy search functionality.
 *
 * These tests verify the filtering and sorting behavior matches the frontend implementation.
 * Reference: frontend/test/common/string/sequence_matching.test.ts
 */
class EntityPickerFilterTest {

    // Test helper to create entities
    private fun createTestEntity(
        entityId: String,
        domain: String = entityId.substringBefore("."),
        friendlyName: String,
        areaName: String? = null,
        deviceName: String? = null,
    ) = EntityPickerItem(
        entityId = entityId,
        domain = domain,
        friendlyName = friendlyName,
        icon = CommunityMaterial.Icon2.cmd_lightbulb,
        areaName = areaName,
        deviceName = deviceName,
    )

    // Test helper to create EntityWithSearchFields
    private fun createEntityWithSearchFields(entity: EntityPickerItem): EntityWithSearchFields {
        val sortingKey = entity.friendlyName.lowercase()
        return EntityWithSearchFields(
            entity = entity,
            sortingKey = sortingKey,
            searchableFields = buildList {
                add(SearchField(sortingKey, 8))
                entity.deviceName?.let { add(SearchField(it.lowercase(), 7)) }
                entity.areaName?.let { add(SearchField(it.lowercase(), 6)) }
                add(SearchField(entity.domain.lowercase(), 6))
                add(SearchField(entity.entityId.lowercase(), 3))
            },
        )
    }

    /*
    fuzzyMatch
     */

    @Test
    fun `Given exact match when fuzzy matching then returns 1_0`() {
        val result = fuzzyMatch("automation.ticker", "automation.ticker")
        assertEquals(1.0, result)
    }

    @Test
    fun `Given substring match when fuzzy matching then returns 1_0`() {
        val result = fuzzyMatch("automation.ticker", "ticker")
        assertEquals(1.0, result)
    }

    @Test
    fun `Given prefix match when fuzzy matching then returns 1_0 for substring`() {
        // "automation" is a substring of "automation.ticker", so returns 1.0
        val result = fuzzyMatch("automation.ticker", "automation")
        assertEquals(1.0, result)
    }

    @Test
    fun `Given partial prefix when fuzzy matching then returns 1_0 for substring`() {
        // "auto" is a substring of "automation.ticker", so returns 1.0
        val result = fuzzyMatch("automation.ticker", "auto")
        assertEquals(1.0, result)
    }

    @Test
    fun `Given empty pattern when fuzzy matching then returns 0_0`() {
        val result = fuzzyMatch("automation.ticker", "")
        assertEquals(0.0, result)
    }

    @Test
    fun `Given empty text when fuzzy matching then returns 0_0`() {
        val result = fuzzyMatch("", "pattern")
        assertEquals(0.0, result)
    }

    @Test
    fun `Given fuzzy match with distance when fuzzy matching then returns score`() {
        // "tickr" is 1 edit away from "ticker" and is found as substring
        val result = fuzzyMatch("ticker", "tickr")
        assertTrue(result > 0.0)
        assertTrue(result < 1.0)
    }

    @Test
    fun `Given large distance when fuzzy matching then returns 0_0`() {
        // "xyz" has large distance from "automation"
        val result = fuzzyMatch("automation", "xyz")
        assertEquals(0.0, result)
    }

    @Test
    fun `Given pattern longer than text when fuzzy matching then returns low score`() {
        // Pattern "automation.ticker" is longer than text "tick"
        val result = fuzzyMatch("tick", "automation")
        // Levenshtein distance allows some fuzzy matching even when pattern is longer
        assertTrue(result <= 0.5)
    }

    /*
    calculateFuzzyMatchScoreOptimized
     */

    @Test
    fun `Given exact entity ID match when calculating score then returns high score`() {
        val searchFields = listOf(
            SearchField("automation.ticker", 10),
            SearchField("stocks", 8),
        )
        val terms = listOf("automation.ticker")

        val score = calculateFuzzyMatchScoreOptimized(searchFields, terms)

        assertEquals(10.0, score)
    }

    @Test
    fun `Given friendly name match when calculating score then returns weighted score`() {
        val searchFields = listOf(
            SearchField("ticker", 10),
            SearchField("stocks", 8),
        )
        val terms = listOf("stocks")

        val score = calculateFuzzyMatchScoreOptimized(searchFields, terms)

        assertEquals(8.0, score)
    }

    @Test
    fun `Given multi-term match when calculating score then sums scores order doesn't matter`() {
        val searchFields = listOf(
            SearchField("bedroom", 10),
            SearchField("light", 8),
        )
        val terms = listOf("bedroom", "light")

        val score = calculateFuzzyMatchScoreOptimized(searchFields, terms)

        assertEquals(18.0, score)
        assertEquals(score, calculateFuzzyMatchScoreOptimized(searchFields, terms.reversed()))
    }

    @Test
    fun `Given one term not matching when calculating score then returns 0_0`() {
        val searchFields = listOf(
            SearchField("bedroom", 10),
            SearchField("light", 8),
        )
        val terms = listOf("bedroom", "nonexistent")

        val score = calculateFuzzyMatchScoreOptimized(searchFields, terms)

        assertEquals(0.0, score)
    }

    @Test
    fun `Given higher weighted field match when calculating score then ranks higher`() {
        val highWeightFields = listOf(
            SearchField("kitchen", 10),
            SearchField("other", 3),
        )
        val lowWeightFields = listOf(
            SearchField("other", 10),
            SearchField("kitchen", 3),
        )
        val terms = listOf("kitchen")

        val highScore = calculateFuzzyMatchScoreOptimized(highWeightFields, terms)
        val lowScore = calculateFuzzyMatchScoreOptimized(lowWeightFields, terms)

        assertTrue(highScore > lowScore)
    }

    /*
    filterAndSortEntitiesOptimized
     */

    @Test
    fun `Given empty query when filtering then returns all entities sorted`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "light.living_room",
                    friendlyName = "Living Room",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "light.bedroom",
                    friendlyName = "Bedroom",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "switch.kitchen",
                    friendlyName = "Kitchen",
                ),
            ),
        )

        val result = filterAndSortEntitiesOptimized(entities, "")

        assertEquals(3, result.size)
        assertEquals("light.bedroom", result[0].entityId) // "Bedroom" comes first
        assertEquals("switch.kitchen", result[1].entityId) // "Kitchen" comes second
        assertEquals("light.living_room", result[2].entityId) // "Living Room" comes last
    }

    @Test
    fun `Given blank query when filtering then returns all entities sorted by friendly name`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(createTestEntity("light.test", friendlyName = "Test3")),
            createEntityWithSearchFields(createTestEntity("light.test", friendlyName = "Test2")),
        )

        val result = filterAndSortEntitiesOptimized(entities, "   ")

        assertEquals(2, result.size)
        assertEquals("Test2", result[0].friendlyName)
        assertEquals("Test3", result[1].friendlyName)
    }

    @Test
    fun `Given short term when filtering then returns all entities sorted by friendly name`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "switch.kitchen",
                    friendlyName = "Kitchen",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "light.bedroom",
                    friendlyName = "Bedroom",
                ),
            ),
        )

        // Single character is below MIN_MATCH_CHAR_LENGTH of 2
        val result = filterAndSortEntitiesOptimized(entities, "b")

        assertEquals(2, result.size)
        assertEquals("Bedroom", result[0].friendlyName)
        assertEquals("Kitchen", result[1].friendlyName)
    }

    @Test
    fun `Given minimum length term when filtering then filters entities`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "light.bedroom",
                    friendlyName = "Bedroom",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "switch.kitchen",
                    friendlyName = "Kitchen",
                ),
            ),
        )

        // Two characters meets MIN_MATCH_CHAR_LENGTH
        val result = filterAndSortEntitiesOptimized(entities, "be")

        assertEquals(1, result.size)
        assertEquals("light.bedroom", result[0].entityId)
    }

    @Test
    fun `Given exact entity ID when filtering then returns matching entity`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "automation.ticker",
                    friendlyName = "Stocks",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "light.bedroom",
                    friendlyName = "Bedroom",
                ),
            ),
        )

        val result = filterAndSortEntitiesOptimized(entities, "automation.ticker")

        assertEquals(1, result.size)
        assertEquals("automation.ticker", result[0].entityId)
    }

    @Test
    fun `Given partial match when filtering then returns matching entity`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "automation.ticker",
                    friendlyName = "Stocks",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "light.bedroom",
                    friendlyName = "Bedroom",
                ),
            ),
        )

        val result = filterAndSortEntitiesOptimized(entities, "auto")

        assertEquals(1, result.size)
        assertEquals("automation.ticker", result[0].entityId)
    }

    @Test
    fun `Given friendly name match when filtering then returns matching entity`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "automation.ticker",
                    friendlyName = "Stocks",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "light.bedroom",
                    friendlyName = "Bedroom",
                ),
            ),
        )

        val result = filterAndSortEntitiesOptimized(entities, "stocks")

        assertEquals(1, result.size)
        assertEquals("automation.ticker", result[0].entityId)
    }

    @Test
    fun `Given domain match when filtering then returns entities in domain sorted by friendly name`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "light.bedroom",
                    domain = "light",
                    friendlyName = "Bedroom",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "light.kitchen",
                    domain = "light",
                    friendlyName = "Kitchen",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "switch.bedroom",
                    domain = "switch",
                    friendlyName = "Switch",
                ),
            ),
        )

        val result = filterAndSortEntitiesOptimized(entities, "light")

        assertEquals(2, result.size)
        assertTrue(result.all { it.domain == "light" })
        assertEquals("Bedroom", result[0].friendlyName)
        assertEquals("Kitchen", result[1].friendlyName)
    }

    @Test
    fun `Given area name match when filtering then returns entities in area`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "light.bedroom_main",
                    friendlyName = "Main Light",
                    areaName = "Bedroom",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "light.kitchen",
                    friendlyName = "Kitchen Light",
                    areaName = "Kitchen",
                ),
            ),
        )

        val result = filterAndSortEntitiesOptimized(entities, "bedroom")

        assertEquals(1, result.size)
        assertEquals("Bedroom", result[0].areaName)
    }

    @Test
    fun `Given device name match when filtering then returns entities with device`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "light.bedroom",
                    friendlyName = "Bedroom",
                    deviceName = "Smart Bulb Pro",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "light.kitchen",
                    friendlyName = "Kitchen",
                    deviceName = "Basic Bulb",
                ),
            ),
        )

        val result = filterAndSortEntitiesOptimized(entities, "smart")

        assertEquals(1, result.size)
        assertEquals("Smart Bulb Pro", result[0].deviceName)
    }

    @Test
    fun `Given multi-term search when filtering then matches entities with all terms`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "light.bedroom_main",
                    friendlyName = "Main Light",
                    areaName = "Bedroom",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity("switch.bedroom_fan", friendlyName = "Fan", areaName = "Bedroom"),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "light.kitchen",
                    friendlyName = "Kitchen Light",
                    areaName = "Kitchen",
                ),
            ),
        )

        val result = filterAndSortEntitiesOptimized(entities, "bedroom light")

        assertEquals(1, result.size)
        assertEquals("light.bedroom_main", result[0].entityId)
    }

    @Test
    fun `Given no matching entities when filtering then returns empty list`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "light.bedroom",
                    friendlyName = "Bedroom",
                ),
            ),
        )

        val result = filterAndSortEntitiesOptimized(entities, "nonexistent")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `Given multiple matches when filtering then sorts by score descending`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "automation.ticker",
                    friendlyName = "Stocks",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "sensor.ticker",
                    friendlyName = "Stocks Up",
                ),
            ),
            createEntityWithSearchFields(createTestEntity("ticker", friendlyName = "Just Ticker")),
            createEntityWithSearchFields(
                createTestEntity(
                    "light.bedroom",
                    friendlyName = "Bedroom",
                ),
            ),
        )

        val result = filterAndSortEntitiesOptimized(entities, "ticker")

        assertEquals(3, result.size)
        assertTrue(result.all { it.entityId.contains("ticker") })
    }

    @Test
    fun `Given case insensitive search when filtering then matches regardless of case`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(
                createTestEntity(
                    "light.BEDROOM",
                    friendlyName = "BEDROOM Light",
                ),
            ),
            createEntityWithSearchFields(
                createTestEntity(
                    "light.kitchen",
                    friendlyName = "kitchen light",
                ),
            ),
        )

        val result = filterAndSortEntitiesOptimized(entities, "BeDrOoM")

        assertEquals(1, result.size)
        assertEquals("light.BEDROOM", result[0].entityId)
    }

    @Test
    fun `Given identical scores when filtering then sorts by friendly name`() = runTest {
        val entities = listOf(
            createEntityWithSearchFields(createTestEntity("light.c", friendlyName = "C Light")),
            createEntityWithSearchFields(createTestEntity("light.a", friendlyName = "A Light")),
            createEntityWithSearchFields(createTestEntity("light.b", friendlyName = "B Light")),
        )

        val result = filterAndSortEntitiesOptimized(entities, "light")

        assertEquals(3, result.size)
        // Should be sorted by friendly name after same score
        assertEquals("A Light", result[0].friendlyName)
        assertEquals("B Light", result[1].friendlyName)
        assertEquals("C Light", result[2].friendlyName)
    }
}
