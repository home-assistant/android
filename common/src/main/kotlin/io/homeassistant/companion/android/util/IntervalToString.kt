package io.homeassistant.companion.android.util

import android.content.Context
import io.homeassistant.companion.android.common.R

fun intervalToString(context: Context, interval: Int): String {
    return when {
        interval == 0 -> context.getString(R.string.interval_manual)
        interval == 1 -> context.getString(R.string.interval_in_view)
        interval >= 60 * 60 -> context.resources.getQuantityString(
            R.plurals.interval_hours,
            interval / 60 / 60,
            interval / 60 / 60,
        )
        interval >= 60 -> context.resources.getQuantityString(R.plurals.interval_minutes, interval / 60, interval / 60)
        else -> context.resources.getQuantityString(R.plurals.interval_seconds, interval, interval)
    }
}
