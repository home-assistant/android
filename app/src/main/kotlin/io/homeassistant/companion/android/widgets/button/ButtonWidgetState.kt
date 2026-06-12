package io.homeassistant.companion.android.widgets.button

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.material.ColorProviders
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme
import io.homeassistant.companion.android.util.compose.glanceHaLightColors

sealed interface ButtonWidgetState {
    val backgroundType: WidgetBackgroundType
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WidgetBackgroundType.DYNAMICCOLOR
        } else {
            WidgetBackgroundType.DAYNIGHT
        }
    val textColor: String?
        get() = null

    companion object {
        @Composable
        fun ButtonWidgetState.getColors(): ColorProviders {
            return when (backgroundType) {
                WidgetBackgroundType.DYNAMICCOLOR -> GlanceTheme.colors
                WidgetBackgroundType.DAYNIGHT -> HomeAssistantGlanceTheme.colors
                WidgetBackgroundType.TRANSPARENT -> ColorProviders(
                    glanceHaLightColors
                        .copy(
                            background = Color.Transparent,
                            onSurface = Color(
                                textColor?.toColorInt() ?: glanceHaLightColors.onSurface.toArgb(),
                            ),
                        ),
                )
            }
        }
    }
}

internal object Loading : ButtonWidgetState
internal object Error : ButtonWidgetState
internal object Success : ButtonWidgetState

internal data class ButtonStateWithData(
    override val backgroundType: WidgetBackgroundType,
    override val textColor: String?,
    val id: Int,
    val label: String?,
    val icon: String,
    val requiresAuthentication: Boolean
) : ButtonWidgetState {

    companion object {
        /**
         * Create a complete [ButtonStateWithData] from the DB
         */
        fun from(
            buttonEntity: ButtonWidgetEntity,
        ): ButtonStateWithData {
            return ButtonStateWithData(
                id = buttonEntity.id,
                backgroundType = buttonEntity.backgroundType,
                textColor = buttonEntity.textColor,
                label = buttonEntity.label,
                icon = buttonEntity.iconName,
                requiresAuthentication = buttonEntity.requireAuthentication
            )
        }
    }
}
