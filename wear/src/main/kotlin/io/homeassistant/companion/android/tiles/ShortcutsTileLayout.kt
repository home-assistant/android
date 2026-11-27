package io.homeassistant.companion.android.tiles

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/** Maximum number of shortcuts a single Shortcuts Tile can hold. */
internal const val MAX_SHORTCUTS_PER_TILE = 14

/** Screen diameter in dp assumed when the real one is unknown. */
internal const val DEFAULT_SCREEN_DIAMETER_DP = 200f

private const val PACKED_GAP = 3f
private const val PACKED_EDGE_MARGIN = 2f

/** Distance in pitches between two rows of circles shifted by half a circle: sqrt(3) / 2. */
private val SHIFTED_ROW_DISTANCE = sqrt(3f) / 2

/** Offset in dp of the center of a shortcut from the center of the screen (y points down). */
internal data class ShortcutCenter(val x: Float, val y: Float)

/**
 * The position and size of the shortcuts on a round screen.
 *
 * @property circleSize diameter in dp of the circle behind each shortcut
 * @property centers the center of each shortcut relative to the center of the screen, in reading order
 */
internal data class ShortcutLayout(val circleSize: Float, val centers: List<ShortcutCenter>)

/**
 * The horizontal slots of a row, from left to right. A slot is the distance of a circle from the center
 * of the screen in steps of half a circle, so `-1, 1` are two neighbors and `-2, 0, 2` are three.
 */
private fun centeredRow(count: Int): List<Int> = List(count) { 2 * it - (count - 1) }

/**
 * The rows, from top to bottom, for 1 to 7 shortcuts. These are placed with the normal circle size and spacing,
 * with the rows exactly below each other.
 */
private val NORMAL_ROWS: Map<Int, List<List<Int>>> = mapOf(
    1 to listOf(centeredRow(1)),
    2 to listOf(centeredRow(2)),
    3 to listOf(centeredRow(2), centeredRow(1)),
    4 to listOf(centeredRow(2), centeredRow(2)),
    5 to listOf(centeredRow(2), centeredRow(3)),
    6 to listOf(centeredRow(2), centeredRow(3), centeredRow(1)),
    7 to listOf(centeredRow(2), centeredRow(3), centeredRow(2)),
)

/**
 * The rows, from top to bottom, for 8 to 14 shortcuts. Each layout is mirrored left to right, and the
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
)

/**
 * Computes the layout for [count] shortcuts on a round screen of [screenDiameterDp].
 *
 * Up to 7 shortcuts use the normal circle size ([CIRCLE_SIZE]) and spacing ([SPACING]). More shortcuts are
 * placed in rows with equally sized circles, as large as possible while staying inside the screen, since the
 * tile cannot scroll. These circles are never larger than the normal size. Counts above
 * [MAX_SHORTCUTS_PER_TILE] are capped. The shortcuts are in reading order: from top to bottom, and from left to
 * right within a row.
 */
internal fun shortcutLayout(count: Int, screenDiameterDp: Float = DEFAULT_SCREEN_DIAMETER_DP): ShortcutLayout {
    val shortcuts = count.coerceIn(1, MAX_SHORTCUTS_PER_TILE)
    val normalRows = NORMAL_ROWS[shortcuts]
    return if (normalRows != null) {
        ShortcutLayout(CIRCLE_SIZE, unitCenters(normalRows, interlocking = false).scaledBy(CIRCLE_SIZE + SPACING))
    } else {
        fitRowsOnScreen(PACKED_ROWS.getValue(shortcuts), screenDiameterDp)
    }
}

/**
 * Places the [rows] as large as possible inside the screen.
 *
 * The positions are first computed in units of the distance between neighboring circles (the pitch). The
 * circle farthest from the center must just touch the edge of the screen, which gives the pitch directly.
 */
private fun fitRowsOnScreen(rows: List<List<Int>>, screenDiameterDp: Float): ShortcutLayout {
    val unitCenters = unitCenters(rows, interlocking = true)
    val farthest = unitCenters.maxOf { hypot(it.x, it.y) }
    val screenRadius = screenDiameterDp / 2 - PACKED_EDGE_MARGIN
    // The farthest edge is (farthest + 0.5) pitches from the center, and a circle is one gap smaller than a pitch.
    val pitch = (screenRadius + PACKED_GAP / 2) / (farthest + 0.5f)
    return ShortcutLayout(
        circleSize = min(pitch - PACKED_GAP, CIRCLE_SIZE),
        centers = unitCenters.scaledBy(pitch),
    )
}

private fun List<ShortcutCenter>.scaledBy(pitch: Float) = map { ShortcutCenter(x = it.x * pitch, y = it.y * pitch) }

/** Centers of the circles of the [rows] in pitches, with the rows centered vertically on the screen. */
private fun unitCenters(rows: List<List<Int>>, interlocking: Boolean): List<ShortcutCenter> {
    val rowTops = mutableListOf(0f)
    rows.zipWithNext { above, below -> rowTops += rowTops.last() + rowDistance(above, below, interlocking) }
    val middle = (rowTops.first() + rowTops.last()) / 2
    return rows.flatMapIndexed { index, slots ->
        slots.map { slot -> ShortcutCenter(x = slot / 2f, y = rowTops[index] - middle) }
    }
}

/**
 * The vertical distance in pitches between two rows. Rows are one pitch apart, unless the rows are [interlocking]
 * and their circles are shifted by half a circle, which lets the rows fit into each other and sit closer.
 */
private fun rowDistance(above: List<Int>, below: List<Int>, interlocking: Boolean): Float =
    if (interlocking && above.first() % 2 != below.first() % 2) SHIFTED_ROW_DISTANCE else 1f
