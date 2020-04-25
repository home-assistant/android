package io.homeassistant.companion.android.wear.util.resources

import android.widget.TextView
import io.homeassistant.companion.android.wear.R

private val actionIcons get() = mapOf(
    0 to R.drawable.ic_flash_on_black_24dp,
    1 to R.drawable.ic_lightbulb_outline_black_24dp,
    2 to R.drawable.ic_home_black_24dp,
    3 to R.drawable.ic_power_settings_new_black_24dp
)

val actionIconCount: Int = actionIcons.size

fun actionIconById(iconId: Int): Int {
    return actionIcons.getOrElse(iconId) { R.drawable.ic_lightbulb_outline_black_24dp }
}

fun TextView.setActionIcon(iconId: Int) {
    val resourceId = actionIconById(iconId)
    setCompoundDrawablesWithIntrinsicBounds(resourceId, 0, 0, 0)
    tag = iconId
}