package io.homeassistant.companion.android.widgets.template

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.dashboards.Dashboard
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction

/** The dashboards of the selected server, offered for [WidgetTapAction.OPEN_DASHBOARD]. */
internal sealed interface DashboardsState {
    /** Not requested yet, or being loaded. */
    data object Loading : DashboardsState

    data class Loaded(val dashboards: List<Dashboard>) : DashboardsState

    /** The dashboards could not be retrieved from the server. */
    data object Error : DashboardsState
}

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
    val selectedTapAction: WidgetTapAction = WidgetTapAction.REFRESH,
    val selectedDashboardUrlPath: String? = null,
    val dashboards: DashboardsState = DashboardsState.Loading,
    val dynamicColorAvailable: Boolean = false,
    val isUpdateWidget: Boolean = false,
) {
    val showServerSelector = serversDropdownItems.size > 1 ||
        serversDropdownItems.none { it.key == selectedServerId }

    @StringRes
    val textSizeError = commonR.string.widget_text_size_error.takeIf { validTextSize == null }

    // Guards against saving a template that hasn't been (re-)validated yet: without
    // `!isRenderingPreview`, editing an already-valid template would keep the action enabled
    // using the *previous* render's result while the new one is still in flight.
    /** Opening a dashboard needs a dashboard to open. */
    private val isTapActionComplete =
        selectedTapAction != WidgetTapAction.OPEN_DASHBOARD || selectedDashboardUrlPath != null

    val isActionEnabled =
        preview is TemplatePreview.Rendered && !isRenderingPreview && textSizeError == null && isTapActionComplete

    @StringRes
    val actionButtonLabel = if (isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget

    val textSizeOrDefault: Float
        get() = validTextSize ?: DEFAULT_TEXT_SIZE.toFloat()

    private val validTextSize: Float?
        get() = textSize.toFloatOrNull()?.takeIf { it.isFinite() && it > 0 }
}

private const val DEFAULT_TEXT_SIZE = "12"
