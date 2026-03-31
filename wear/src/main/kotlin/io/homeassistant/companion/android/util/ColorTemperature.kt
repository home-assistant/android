package io.homeassistant.companion.android.util

import androidx.compose.ui.graphics.Color
import java.util.TreeMap
import kotlin.math.abs

fun getColorTemperature(ratio: Double, isKelvin: Boolean): Color {
    val colorMap = sortedMapOf(
        0.00 to 0xffa6d1ff,
        0.05 to 0xffafd6ff,
        0.10 to 0xffb8dbff,
        0.15 to 0xffc1e0ff,
        0.20 to 0xffcae4ff,
        0.25 to 0xffd3e9ff,
        0.30 to 0xffdcedff,
        0.35 to 0xffe5f2ff,
        0.40 to 0xffeef6ff,
        0.45 to 0xfff7fbff,
        0.50 to 0xffffffff,
        0.55 to 0xfffff6e6,
        0.60 to 0xffffeccc,
        0.65 to 0xffffe3b3,
        0.70 to 0xffffd999,
        0.75 to 0xffffd080,
        0.80 to 0xffffc666,
        0.85 to 0xffffbd4d,
        0.90 to 0xffffb333,
        0.95 to 0xffffaa1a,
        1.00 to 0xffffa000,
    ) as TreeMap<Double, Long>

    val useRatio = if (isKelvin) (1 - ratio) else ratio
    val previous = colorMap.floorEntry(useRatio)
    val next = colorMap.ceilingEntry(useRatio)
    if (previous == null) return Color(next?.value ?: 0xffa6d1ff) // next and previous should never both be null
    if (next == null) return Color(previous.value)
    return if (abs(ratio - previous.key) < abs(ratio - next.key)) Color(previous.value) else Color(next.value)
}
