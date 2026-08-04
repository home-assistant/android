package io.homeassistant.companion.android.widgets.template

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType

/** What the template preview area shows. */
internal sealed interface TemplatePreview {
    /** Nothing to render: the template is blank. */
    data object Empty : TemplatePreview

    /** The template was rendered successfully. */
    data class Rendered(val text: String) : TemplatePreview

    /** Rendering failed; [messageRes] explains why. */
    data class Error(@StringRes val messageRes: Int) : TemplatePreview
}

@Stable
internal data class TemplateWidgetConfigureState(
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val template: String = "",
    val preview: TemplatePreview = TemplatePreview.Empty,
    /** Whether a render for the current [template] is still in flight. */
    val isRenderingPreview: Boolean = false,
    val textSize: String = DEFAULT_TEXT_SIZE,
    val selectedBackgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    val textColorHex: String? = null,
    val dynamicColorAvailable: Boolean = false,
    val isUpdateWidget: Boolean = false,
) {
    val showServerSelector = serversDropdownItems.size > 1 ||
        serversDropdownItems.none { it.key == selectedServerId }

    // Guards against saving a template that hasn't been (re-)validated yet: without
    // `!isRenderingPreview`, editing an already-valid template would keep the action enabled
    // using the *previous* render's result while the new one is still in flight.
    val isActionEnabled = preview is TemplatePreview.Rendered && !isRenderingPreview && validTextSize != null

    @StringRes
    val actionButtonLabel = if (isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget

    val textSizeOrDefault: Float
        get() = validTextSize ?: DEFAULT_TEXT_SIZE.toFloat()

    private val validTextSize: Float?
        get() = textSize.toFloatOrNull()?.takeIf { it.isFinite() && it > 0 }
}

internal const val DEFAULT_TEXT_SIZE = "12"
