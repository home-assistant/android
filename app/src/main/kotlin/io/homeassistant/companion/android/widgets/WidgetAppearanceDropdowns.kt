package io.homeassistant.companion.android.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType

/**
 * Dropdown letting the user pick the background of a widget.
 *
 * Shared by the widget configuration screens, which all offer the same choices.
 *
 * @param selected The currently selected background.
 * @param dynamicColorAvailable Whether the device supports dynamic color, which adds that choice.
 * @param onSelected Invoked with the background the user picked.
 * @param modifier Optional [Modifier] to be applied to the dropdown.
 */
@Composable
fun WidgetBackgroundTypeDropdown(
    selected: WidgetBackgroundType,
    dynamicColorAvailable: Boolean,
    onSelected: (WidgetBackgroundType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dynamicColorLabel = stringResource(commonR.string.widget_background_type_dynamiccolor)
    val dayNightLabel = stringResource(commonR.string.widget_background_type_daynight)
    val transparentLabel = stringResource(commonR.string.widget_background_type_transparent)
    val items = remember(dynamicColorAvailable, dynamicColorLabel, dayNightLabel, transparentLabel) {
        buildList {
            if (dynamicColorAvailable) {
                add(HADropdownItem(key = WidgetBackgroundType.DYNAMICCOLOR, label = dynamicColorLabel))
            }
            add(HADropdownItem(key = WidgetBackgroundType.DAYNIGHT, label = dayNightLabel))
            add(HADropdownItem(key = WidgetBackgroundType.TRANSPARENT, label = transparentLabel))
        }
    }

    HADropdownMenu(
        items = items,
        selectedKey = selected,
        onItemSelected = onSelected,
        label = stringResource(commonR.string.widget_background_type_label),
        modifier = modifier,
    )
}

/**
 * Dropdown letting the user pick the text color of a widget, which only applies to a transparent
 * background.
 *
 * Shared by the widget configuration screens, which all offer the same choices.
 *
 * @param selected The currently selected text color.
 * @param onSelected Invoked with the text color the user picked.
 * @param modifier Optional [Modifier] to be applied to the dropdown.
 */
@Composable
fun WidgetTextColorDropdown(
    selected: WidgetTextColor,
    onSelected: (WidgetTextColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val whiteLabel = stringResource(commonR.string.widget_text_color_white)
    val blackLabel = stringResource(commonR.string.widget_text_color_black)
    val items = remember(whiteLabel, blackLabel) {
        listOf(
            HADropdownItem(key = WidgetTextColor.WHITE, label = whiteLabel),
            HADropdownItem(key = WidgetTextColor.BLACK, label = blackLabel),
        )
    }

    HADropdownMenu(
        items = items,
        selectedKey = selected,
        onItemSelected = onSelected,
        label = stringResource(commonR.string.widget_text_color_label),
        modifier = modifier,
    )
}
