package io.homeassistant.companion.android.util

fun IntervalToString(interval: Int): String {
    return when {
        interval == 0 -> "Manual"
        interval >= 60 * 60 -> "${interval / 60 / 60} hours"
        interval >= 60 -> "${interval / 60} minutes"
        else -> "$interval seconds"
    }
}
