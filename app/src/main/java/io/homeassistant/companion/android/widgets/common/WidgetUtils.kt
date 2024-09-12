package io.homeassistant.companion.android.widgets.common

import android.content.Context
import com.google.android.material.color.DynamicColors
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType

/**
 * Shared helpers for working with widgets.
 */
object WidgetUtils {

    /**
     * Create an adapter for the list of background colour options for a widget.
     */
    fun getBackgroundOptionList(context: Context): Array<String> {
        val backgroundTypeValues = mutableListOf(
            context.getString(R.string.widget_background_type_daynight),
            context.getString(R.string.widget_background_type_transparent)
        )
        if (DynamicColors.isDynamicColorAvailable()) {
            backgroundTypeValues.add(0, context.getString(R.string.widget_background_type_dynamiccolor))
        }
        return backgroundTypeValues.toTypedArray()
    }

    fun getSelectedBackgroundOption(context: Context, selectedType: WidgetBackgroundType, options: Array<String>) = when {
        selectedType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable() ->
            options.indexOf(context.getString(R.string.widget_background_type_dynamiccolor))
        selectedType == WidgetBackgroundType.TRANSPARENT ->
            options.indexOf(context.getString(R.string.widget_background_type_transparent))
        else ->
            options.indexOf(context.getString(R.string.widget_background_type_daynight))
    }
}
