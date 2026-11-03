package io.homeassistant.companion.android.widgets.configure

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem

/**
 * Fields every widget configuration state exposes so the shared [WidgetConfigureScaffold] can render
 * the server selector and the action button without knowing the concrete widget.
 */
internal interface WidgetConfigureState {
    val serversDropdownItems: List<HADropdownItem<Int>>
    val selectedServerId: Int

    /** Whether this screen updates an existing widget rather than adding a new one. */
    val isUpdateWidget: Boolean

    /** Whether the server selector should be shown (more than one server, or none matching the selection). */
    val showServerSelector: Boolean
        get() = serversDropdownItems.size > 1 || serversDropdownItems.none { it.key == selectedServerId }

    /** Label of the action button, e.g. "Add widget" or "Update widget". */
    @get:StringRes
    val actionButtonLabel: Int
        get() = if (isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget

    /** Whether the action button is enabled, i.e. the configuration is complete and valid. */
    val isActionEnabled: Boolean
}
