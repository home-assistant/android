package io.homeassistant.companion.android.widgets

import android.content.Context
import androidx.annotation.ColorRes
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.getHexForColor

/**
 * Text color a widget uses when it has a transparent background.
 *
 * Widgets persist the resolved hex value, so a [Context] is needed to convert between the two.
 */
enum class WidgetTextColor(@ColorRes private val colorRes: Int) {
    WHITE(android.R.color.white),
    BLACK(commonR.color.colorWidgetButtonLabelBlack),
    ;

    /** Hex value a widget persists for this text color. */
    fun resolve(context: Context): String = context.getHexForColor(colorRes)

    companion object {
        /** Text color the persisted [hex] represents, defaulting to [WHITE] when it matches none. */
        fun fromHex(context: Context, hex: String?): WidgetTextColor =
            entries.firstOrNull { it.resolve(context) == hex } ?: WHITE
    }
}
