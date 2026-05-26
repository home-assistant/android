package io.homeassistant.companion.android.widgets.template

import com.google.android.material.color.DynamicColors
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType

/**
 * Represents the UI state of the Template Widget configuration screen.
 */
data class TemplateWidgetConfigureUiState(
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val templateText: String = "",
    val renderedTemplate: String? = null,
    val isTemplateValid: Boolean = false,
    val textSize: String = "14",
    val selectedBackgroundType: WidgetBackgroundType =
        if (DynamicColors.isDynamicColorAvailable()) {
            WidgetBackgroundType.DYNAMICCOLOR
        } else {
            WidgetBackgroundType.DAYNIGHT
        },
    val textColorIndex: Int = 0,
    val isUpdateWidget: Boolean = false,
    val templateRenderError: TemplateRenderError? = null,
)

/**
 * Represents the type of error encountered when rendering a template.
 */
enum class TemplateRenderError {
    /** Error in the template syntax itself. */
    TEMPLATE_ERROR,

    /** Error communicating with the server or rendering the template. */
    RENDER_ERROR,
}