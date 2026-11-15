package io.homeassistant.companion.android.tiles

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/** Maximum number of shortcuts a single Shortcuts Tile can hold. */
internal const val MAX_SHORTCUTS_PER_TILE = 15

/** Screen diameter in dp assumed when the real one is unknown. */
internal const val DEFAULT_SCREEN_DIAMETER_DP = 200f

/** Up to this number of shortcuts the tile keeps its original layout; more shortcuts use [packedShortcutLayout]. */
internal const val MAX_SHORTCUTS_WITH_DEFAULT_LAYOUT = 7

private const val PACKED_GAP = 3f
private const val PACKED_EDGE_MARGIN = 2f

/** Distance in pitches between two rows of circles shifted by half a circle: sqrt(3) / 2. */
private val SHIFTED_ROW_DISTANCE = sqrt(3f) / 2

/** Offset in dp of the center of a shortcut from the center of the screen (y points down). */
internal data class ShortcutCenter(val x: Float, val y: Float)

/**
 * Shortcuts in rows that use the round screen as well as possible. The circles of neighboring rows
 * are shifted by half a circle, so the rows can sit closer together.
 *
 * @property circleSize diameter in dp of the circle behind each shortcut
 * @property centers the center of each shortcut relative to the center of the screen, in reading order
 */
internal data class PackedShortcutLayout(val circleSize: Float, val centers: List<ShortcutCenter>)

/**
 * The horizontal slots of a row, from left to right. A slot is the distance of a circle from the center
 * of the screen in steps of half a circle, so `-1, 1` are two neighbors and `-2, 0, 2` are three.
 */
private fun centeredRow(count: Int): List<Int> = List(count) { 2 * it - (count - 1) }

/**
 * The rows, from top to bottom, for 8 to 15 shortcuts. Each layout is mirrored left to right, and the
 * sizes were chosen to give the largest circles that fit a round screen, but 11 and 13 reuse the layout of
 * 12 and 14 with a circle left out, so the circles never get smaller when a shortcut is removed.
 */
private val PACKED_ROWS: Map<Int, List<List<Int>>> = mapOf(
    8 to listOf(centeredRow(3), centeredRow(2), centeredRow(3)),
    9 to listOf(centeredRow(3), centeredRow(3), centeredRow(3)),
    10 to listOf(centeredRow(2), centeredRow(3), centeredRow(3), centeredRow(2)),
    11 to listOf(centeredRow(2), centeredRow(4), centeredRow(4), centeredRow(1)),
    12 to listOf(centeredRow(2), centeredRow(4), centeredRow(4), centeredRow(2)),
    13 to listOf(centeredRow(3), centeredRow(4), centeredRow(4), listOf(-2, 2)),
    14 to listOf(centeredRow(3), centeredRow(4), centeredRow(4), centeredRow(3)),
    15 to listOf(centeredRow(2), centeredRow(4), centeredRow(3), centeredRow(4), centeredRow(2)),
)

/**
 * Computes the layout for [count] shortcuts on a round screen of [screenDiameterDp], or returns null when the
 * original layout is used (up to [MAX_SHORTCUTS_WITH_DEFAULT_LAYOUT] shortcuts).
 *
 * Equally sized circles are placed in rows, as large as possible while staying inside the screen, since the
 * tile cannot scroll. Counts above [MAX_SHORTCUTS_PER_TILE] are capped. The shortcuts are in reading order:
 * from top to bottom, and from left to right within a row.
 */
internal fun packedShortcutLayout(
    count: Int,
    screenDiameterDp: Float = DEFAULT_SCREEN_DIAMETER_DP,
): PackedShortcutLayout? {
    if (count <= MAX_SHORTCUTS_WITH_DEFAULT_LAYOUT) return null
    return fitRowsOnScreen(PACKED_ROWS.getValue(min(count, MAX_SHORTCUTS_PER_TILE)), screenDiameterDp)
}

/**
 * Places the [rows] as large as possible inside the screen.
 *
 * The positions are first computed in units of the distance between neighboring circles (the pitch). The
 * circle farthest from the center must just touch the edge of the screen, which gives the pitch directly.
 */
private fun fitRowsOnScreen(rows: List<List<Int>>, screenDiameterDp: Float): PackedShortcutLayout {
    val unitCenters = unitCenters(rows)
    val farthest = unitCenters.maxOf { hypot(it.x, it.y) }
    val screenRadius = screenDiameterDp / 2 - PACKED_EDGE_MARGIN
    // The farthest edge is (farthest + 0.5) pitches from the center, and a circle is one gap smaller than a pitch.
    val pitch = (screenRadius + PACKED_GAP / 2) / (farthest + 0.5f)
    return PackedShortcutLayout(
        circleSize = pitch - PACKED_GAP,
        centers = unitCenters.map { ShortcutCenter(x = it.x * pitch, y = it.y * pitch) },
    )
}

/** Centers of the circles of the [rows] in pitches, with the rows centered vertically on the screen. */
private fun unitCenters(rows: List<List<Int>>): List<ShortcutCenter> {
    val rowTops = mutableListOf(0f)
    rows.zipWithNext { above, below -> rowTops += rowTops.last() + rowDistance(above, below) }
    val middle = (rowTops.first() + rowTops.last()) / 2
    return rows.flatMapIndexed { index, slots ->
        slots.map { slot -> ShortcutCenter(x = slot / 2f, y = rowTops[index] - middle) }
    }
}

/**
 * The vertical distance in pitches between two rows. Rows whose circles line up have to be one pitch apart,
 * while rows shifted by half a circle fit into each other and can be closer.
 */
private fun rowDistance(above: List<Int>, below: List<Int>): Float =
    if (above.first() % 2 == below.first() % 2) 1f else SHIFTED_ROW_DISTANCE
