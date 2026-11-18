package io.homeassistant.companion.android.tiles

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

private const val TOLERANCE = 0.01f
private const val MIN_CIRCLE_SIZE = 40f
private const val FIRST_PACKED_COUNT = 8

class ShortcutsTileLayoutTest {

    @Test
    fun `Given 7 shortcuts When computing the layout Then it matches the original rows`() {
        val layout = shortcutLayout(7)

        assertEquals(CIRCLE_SIZE, layout.circleSize)
        val expected = listOf(
            ShortcutCenter(-32f, -64f),
            ShortcutCenter(32f, -64f),
            ShortcutCenter(-64f, 0f),
            ShortcutCenter(0f, 0f),
            ShortcutCenter(64f, 0f),
            ShortcutCenter(-32f, 64f),
            ShortcutCenter(32f, 64f),
        )
        assertEquals(expected, layout.centers)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7])
    fun `Given up to 7 shortcuts When computing the layout Then the circles have the normal size and spacing`(
        count: Int,
    ) {
        val layout = shortcutLayout(count)

        assertEquals(count, layout.centers.size)
        assertEquals(CIRCLE_SIZE, layout.circleSize)
        if (count > 1) {
            val closest = layout.centers.flatMapIndexed { i, a ->
                layout.centers.drop(i + 1).map { b -> hypot(a.x - b.x, a.y - b.y) }
            }.min()
            assertEquals(CIRCLE_SIZE + SPACING, closest, TOLERANCE)
        }
    }

    @ParameterizedTest
    @ValueSource(floats = [192f, 200f, 227f, 240f])
    fun `Given more than 7 shortcuts When computing the layout Then circles fit on screen without overlapping`(
        screenDiameterDp: Float,
    ) {
        for (count in FIRST_PACKED_COUNT..MAX_SHORTCUTS_PER_TILE) {
            val layout = shortcutLayout(count, screenDiameterDp)
            val message = "count=$count diameter=$screenDiameterDp"

            assertEquals(count, layout.centers.size, message)
            assertTrue(layout.circleSize >= MIN_CIRCLE_SIZE, "$message circleSize=${layout.circleSize}")
            layout.centers.forEach { center ->
                assertTrue(hypot(center.x, center.y) + layout.circleSize / 2 <= screenDiameterDp / 2, message)
            }
            layout.centers.forEachIndexed { i, a ->
                layout.centers.drop(i + 1).forEach { b ->
                    assertTrue(hypot(a.x - b.x, a.y - b.y) >= layout.circleSize - TOLERANCE, message)
                }
            }
        }
    }

    @Test
    fun `Given a large screen When computing the layout Then circles are not larger than the normal size`() {
        for (count in FIRST_PACKED_COUNT..MAX_SHORTCUTS_PER_TILE) {
            val layout = shortcutLayout(count, screenDiameterDp = 260f)

            assertTrue(layout.circleSize <= CIRCLE_SIZE, "count=$count circleSize=${layout.circleSize}")
        }
    }

    @Test
    fun `Given any number of shortcuts When computing the layout Then it is mirrored left to right`() {
        for (count in 1..MAX_SHORTCUTS_PER_TILE) {
            val centers = shortcutLayout(count).centers

            centers.forEach { center ->
                assertTrue(
                    centers.any { abs(it.x + center.x) < TOLERANCE && abs(it.y - center.y) < TOLERANCE },
                    "count=$count has no mirror of $center",
                )
            }
        }
    }

    @Test
    fun `Given any number of shortcuts When computing the layout Then they are in reading order`() {
        for (count in 1..MAX_SHORTCUTS_PER_TILE) {
            val centers = shortcutLayout(count).centers

            centers.zipWithNext().forEach { (a, b) ->
                assertTrue(a.y < b.y + TOLERANCE, "count=$count $a is below $b")
                if (abs(a.y - b.y) < TOLERANCE) {
                    assertTrue(a.x < b.x, "count=$count $a is not left of $b")
                }
            }
        }
    }

    @Test
    fun `Given more shortcuts than the maximum When computing the layout Then the count is capped`() {
        val layout = shortcutLayout(MAX_SHORTCUTS_PER_TILE + 5)

        assertEquals(MAX_SHORTCUTS_PER_TILE, layout.centers.size)
    }
}
