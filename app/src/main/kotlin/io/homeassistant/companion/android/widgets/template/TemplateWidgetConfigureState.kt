package io.homeassistant.companion.android.widgets.template

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.configure.WidgetConfigureState
import io.homeassistant.companion.android.widgets.configure.defaultWidgetBackgroundType
import io.homeassistant.companion.android.widgets.configure.dynamicColorAvailable

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
    override val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    override val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val template: String = "",
    val preview: TemplatePreview = TemplatePreview.Empty,
    /** Whether a render for the current [template] is still in flight. */
    val isRenderingPreview: Boolean = false,
    val textSize: String = DEFAULT_TEXT_SIZE,
    val selectedBackgroundType: WidgetBackgroundType = defaultWidgetBackgroundType(),
    val textColorHex: String? = null,
    val dynamicColorAvailable: Boolean = dynamicColorAvailable(),
    override val isUpdateWidget: Boolean = false,
) : WidgetConfigureState<TemplateWidgetConfigureState> {
    @StringRes
    val textSizeError = commonR.string.widget_text_size_error.takeIf { validTextSize == null }

    // Guards against saving a template that hasn't been (re-)validated yet: without
    // `!isRenderingPreview`, editing an already-valid template would keep the action enabled
    // using the *previous* render's result while the new one is still in flight.
    override val isActionEnabled = preview is TemplatePreview.Rendered && !isRenderingPreview && textSizeError == null

    val textSizeOrDefault: Float
        get() = validTextSize ?: DEFAULT_TEXT_SIZE.toFloat()

    private val validTextSize: Float?
        get() = textSize.toFloatOrNull()?.takeIf { it.isFinite() && it > 0 }

    override fun withServersDropdownItems(items: List<HADropdownItem<Int>>) = copy(serversDropdownItems = items)

    override fun withSelectedServerId(serverId: Int) = copy(selectedServerId = serverId)

    override fun withBackgroundType(backgroundType: WidgetBackgroundType) =
        copy(selectedBackgroundType = backgroundType)

    /** The template is re-rendered against the new server by the ViewModel's reload. */
    override fun changeServer(serverId: Int) = copy(selectedServerId = serverId)
}

private const val DEFAULT_TEXT_SIZE = "12"
