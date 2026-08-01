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
    val textSize: String = DEFAULT_TEXT_SIZE,
    val selectedBackgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    val textColorHex: String? = null,
    val dynamicColorAvailable: Boolean = false,
    val isUpdateWidget: Boolean = false,
) {
    val showServerSelector = serversDropdownItems.size > 1 ||
        serversDropdownItems.none { it.key == selectedServerId }

    val isActionEnabled = preview is TemplatePreview.Rendered

    @StringRes
    val actionButtonLabel = if (isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget

    val textSizeOrDefault: Float
        get() = textSize.toFloatOrNull()?.takeIf { it.isFinite() && it > 0 } ?: DEFAULT_TEXT_SIZE.toFloat()
}

internal const val DEFAULT_TEXT_SIZE = "12"
