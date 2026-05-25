package io.homeassistant.companion.android.tiles

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.data.SimplifiedEntity
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShortcutsTileStateTest {

    private val epoch = LocalDateTime.of(2026, 1, 1, 0, 0)

    private fun entity(id: String, state: String): Entity = Entity(
        entityId = id,
        state = state,
        attributes = emptyMap(),
        lastChanged = epoch,
        lastUpdated = epoch,
    )

    @Test
    fun `resourceIdForEntity omits separator when state is null`() {
        assertEquals("switch.plug_a", resourceIdForEntity("switch.plug_a", null))
    }

    @Test
    fun `resourceIdForEntity omits separator when state is empty`() {
        assertEquals("switch.plug_a", resourceIdForEntity("switch.plug_a", ""))
    }

    @Test
    fun `resourceIdForEntity includes state suffix for distinct states`() {
        val on = resourceIdForEntity("switch.plug_a", "on")
        val off = resourceIdForEntity("switch.plug_a", "off")
        assertEquals("switch.plug_a@on", on)
        assertEquals("switch.plug_a@off", off)
        assertNotEquals(on, off)
    }

    @Test
    fun `snapshot includes every requested entity id`() {
        val cache = mapOf(
            "switch.plug_a" to entity("switch.plug_a", "on"),
            "lock.front" to entity("lock.front", "locked"),
        )
        val snapshot = TileSnapshot.from(cache, listOf("switch.plug_a", "lock.front", "light.missing"))

        assertEquals("on", snapshot.stateOf("switch.plug_a"))
        assertEquals("locked", snapshot.stateOf("lock.front"))
        assertNull(snapshot.stateOf("light.missing"))
    }

    @Test
    fun `snapshot is decoupled from later cache mutations`() {
        val cache = mutableMapOf("switch.plug_a" to entity("switch.plug_a", "on"))
        val snapshot = TileSnapshot.from(cache, listOf("switch.plug_a"))

        cache["switch.plug_a"] = entity("switch.plug_a", "off")

        assertEquals("on", snapshot.stateOf("switch.plug_a"))
    }

    @Test
    fun `requiredResourceIds matches resource ids produced by iconLayout inputs`() {
        val entities = listOf(
            SimplifiedEntity(entityId = "switch.plug_a"),
            SimplifiedEntity(entityId = "lock.front"),
        )
        val snapshot = TileSnapshot.from(
            mapOf(
                "switch.plug_a" to entity("switch.plug_a", "on"),
                "lock.front" to entity("lock.front", "locked"),
            ),
            entities.map { it.entityId },
        )

        val ids = requiredResourceIds(entities, snapshot)

        assertTrue("switch.plug_a@on" in ids)
        assertTrue("lock.front@locked" in ids)
        assertEquals(2, ids.size)
    }

    @Test
    fun `requiredResourceIds differs when only one entity state changes between snapshots`() {
        val entities = listOf(SimplifiedEntity(entityId = "switch.plug_a"), SimplifiedEntity(entityId = "lock.front"))

        val s1 = TileSnapshot.from(
            mapOf(
                "switch.plug_a" to entity("switch.plug_a", "on"),
                "lock.front" to entity("lock.front", "locked"),
            ),
            entities.map { it.entityId },
        )
        val s2 = TileSnapshot.from(
            mapOf(
                "switch.plug_a" to entity("switch.plug_a", "off"),
                "lock.front" to entity("lock.front", "locked"),
            ),
            entities.map { it.entityId },
        )

        assertNotEquals(requiredResourceIds(entities, s1), requiredResourceIds(entities, s2))
    }

    @Test
    fun `snapshot stash returns the exact snapshot that was stored`() {
        val stash = SnapshotStash()
        val snapshot = TileSnapshot.from(mapOf("a" to entity("a", "1")), listOf("a"))
        stash.put("v1", snapshot)

        assertSame(snapshot, stash.get("v1"))
    }

    @Test
    fun `snapshot stash is resistant to live cache mutation between put and get`() {
        val stash = SnapshotStash()
        val cache = mutableMapOf("a" to entity("a", "1"))
        val snapshot = TileSnapshot.from(cache, listOf("a"))
        stash.put("v1", snapshot)

        cache["a"] = entity("a", "2")
        val retrieved = stash.get("v1")

        assertEquals("1", retrieved?.stateOf("a"))
    }

    @Test
    fun `snapshot stash evicts oldest entries past the bound`() {
        val stash = SnapshotStash(maxEntries = 2)
        stash.put("v1", TileSnapshot.from(emptyMap(), emptyList()))
        stash.put("v2", TileSnapshot.from(emptyMap(), emptyList()))
        stash.put("v3", TileSnapshot.from(emptyMap(), emptyList()))

        assertNull(stash.get("v1"))
        assertEquals(2, stash.size())
    }

    @Test
    fun `snapshot stash clear removes all entries`() {
        val stash = SnapshotStash()
        stash.put("v1", TileSnapshot.from(emptyMap(), emptyList()))
        stash.put("v2", TileSnapshot.from(emptyMap(), emptyList()))
        stash.clear()

        assertNull(stash.get("v1"))
        assertNull(stash.get("v2"))
    }

    @Test
    fun `put with existing version replaces snapshot and keeps size bounded`() {
        val stash = SnapshotStash(maxEntries = 2)
        val first = TileSnapshot.from(mapOf("a" to entity("a", "1")), listOf("a"))
        val second = TileSnapshot.from(mapOf("a" to entity("a", "2")), listOf("a"))

        stash.put("v1", first)
        stash.put("v1", second)

        assertSame(second, stash.get("v1"))
        assertEquals(1, stash.size())
    }

    /**
     * Mimics the runtime race: tile request builds layout from snapshot S1, WebSocket update
     * mutates cache to S2 before resources request arrives. Invariant: the layout's required
     * resource IDs must all be satisfiable from the stashed snapshot, regardless of current cache.
     */
    @Test
    fun `mutation between tile request and resources request does not break invariant`() {
        val entities = listOf(SimplifiedEntity(entityId = "switch.plug_a"))
        val liveCache = mutableMapOf("switch.plug_a" to entity("switch.plug_a", "on"))
        val stash = SnapshotStash()

        val versionA = "v-1"
        val snapshotA = TileSnapshot.from(liveCache, entities.map { it.entityId })
        stash.put(versionA, snapshotA)
        val layoutIds = requiredResourceIds(entities, snapshotA)

        liveCache["switch.plug_a"] = entity("switch.plug_a", "off")

        val retrievedSnapshot = stash.get(versionA)!!
        val resourceIds = entities.map { it.resourceIdIn(retrievedSnapshot) }.toSet()

        assertEquals(layoutIds, resourceIds)
        assertTrue("switch.plug_a@on" in resourceIds)
    }

    @Test
    fun `predictedStateAfterClick flips switch between on and off`() {
        assertEquals("off", predictedStateAfterClick(entity("switch.plug_a", "on")))
        assertEquals("on", predictedStateAfterClick(entity("switch.plug_a", "off")))
    }

    @Test
    fun `predictedStateAfterClick flips light between on and off`() {
        assertEquals("off", predictedStateAfterClick(entity("light.living_room", "on")))
        assertEquals("on", predictedStateAfterClick(entity("light.living_room", "off")))
    }

    @Test
    fun `predictedStateAfterClick flips lock between locked and unlocked`() {
        assertEquals("unlocked", predictedStateAfterClick(entity("lock.front", "locked")))
        assertEquals("locked", predictedStateAfterClick(entity("lock.front", "unlocked")))
    }

    @Test
    fun `predictedStateAfterClick flips cover between open and closed`() {
        assertEquals("closed", predictedStateAfterClick(entity("cover.garage", "open")))
        assertEquals("open", predictedStateAfterClick(entity("cover.garage", "closed")))
    }

    @Test
    fun `predictedStateAfterClick flips input_boolean and fan`() {
        assertEquals("off", predictedStateAfterClick(entity("input_boolean.flag", "on")))
        assertEquals("on", predictedStateAfterClick(entity("fan.bedroom", "off")))
    }

    @Test
    fun `predictedStateAfterClick returns null for unknown domain`() {
        assertNull(predictedStateAfterClick(entity("sensor.temperature", "on")))
        assertNull(predictedStateAfterClick(entity("climate.hvac", "heat")))
    }

    @Test
    fun `predictedStateAfterClick returns null for unrecognized state`() {
        assertNull(predictedStateAfterClick(entity("lock.front", "jammed")))
        assertNull(predictedStateAfterClick(entity("switch.plug_a", "unavailable")))
    }

    @Test
    fun `applyOptimisticClick returns new entity with flipped state for toggles`() {
        val before = entity("switch.plug_a", "on")
        val after = applyOptimisticClick(before)
        assertEquals("off", after.state)
        assertNotEquals(before.state, after.state)
    }

    @Test
    fun `applyOptimisticClick returns input unchanged when state is not predictable`() {
        val before = entity("sensor.temperature", "22.5")
        val after = applyOptimisticClick(before)
        assertSame(before, after)
    }

    @Test
    fun `applyOptimisticClick returns input unchanged for unrecognized state of known domain`() {
        val before = entity("lock.front", "jammed")
        val after = applyOptimisticClick(before)
        assertSame(before, after)
    }

    /**
     * End-to-end contract: after a click, a snapshot built from the optimistically-updated
     * cache must reference the predicted resource ID — that's what fixes the "display lags
     * reality by one tap" symptom.
     */
    @Test
    fun `snapshot after optimistic click points to predicted resource id`() {
        val cache = mutableMapOf("switch.plug_a" to entity("switch.plug_a", "on"))
        cache["switch.plug_a"] = applyOptimisticClick(cache["switch.plug_a"]!!)

        val snapshot = TileSnapshot.from(cache, listOf("switch.plug_a"))
        val entities = listOf(SimplifiedEntity(entityId = "switch.plug_a"))

        assertTrue("switch.plug_a@off" in requiredResourceIds(entities, snapshot))
    }
}
