package io.homeassistant.companion.android.widgets.configure

import androidx.annotation.StringRes
import com.google.android.material.color.DynamicColors
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType

/**
 * Fields every widget configuration state exposes so the shared [WidgetConfigureScaffold] can render
 * the server selector and the action button without knowing the concrete widget.
 *
 * The state also owns its own mutations: each implementation returns a modified copy of itself (the
 * self type [S]), so [BaseWidgetConfigureViewModel] can update the state without knowing the concrete
 * data class.
 *
 * @param S the concrete state type (self type).
 */
internal interface WidgetConfigureState<S : WidgetConfigureState<S>> {
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

    /** Returns a copy exposing the available [items] in the server selector. */
    fun withServersDropdownItems(items: List<HADropdownItem<Int>>): S

    /** Returns a copy with the selected server set, leaving everything else untouched. */
    fun withSelectedServerId(serverId: Int): S

    /** Returns a copy for a server switch, resetting the fields that only made sense for the previous one. */
    fun changeServer(serverId: Int): S

    /** Returns a copy with the background type set. */
    fun withBackgroundType(backgroundType: WidgetBackgroundType): S
}

/** Whether dynamic color (Material You) is available, which adds that background option. */
internal fun dynamicColorAvailable(): Boolean = DynamicColors.isDynamicColorAvailable()

/** The background type a newly configured widget starts with. */
internal fun defaultWidgetBackgroundType(): WidgetBackgroundType =
    if (dynamicColorAvailable()) WidgetBackgroundType.DYNAMICCOLOR else WidgetBackgroundType.DAYNIGHT
