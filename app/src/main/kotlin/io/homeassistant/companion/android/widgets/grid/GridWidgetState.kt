package io.homeassistant.companion.android.widgets.grid

import android.os.Build
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.material.ColorProviders
import androidx.glance.unit.ColorProvider
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme
import io.homeassistant.companion.android.util.compose.glanceHaLightColors
import kotlinx.parcelize.Parcelize

@Parcelize
internal data object GridButtonState : Parcelable

internal sealed interface GridWidgetState {
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
        fun GridWidgetState.getColors(): ColorProviders {
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

        @Composable
        @ReadOnlyComposable
        fun GridWidgetState.getButtonColors(): GridButtonColors {
            return when (backgroundType) {
                WidgetBackgroundType.DYNAMICCOLOR -> GridButtonColors(
                    backgroundColor = GlanceTheme.colors.primaryContainer,
                    contentColor = GlanceTheme.colors.onPrimaryContainer,
                    activeBackgroundColor = GlanceTheme.colors.primary,
                    activeContentColor = GlanceTheme.colors.onPrimary,
                )
                else -> GridButtonColors.Default
            }
        }
    }
}

internal object LoadingGridState : GridWidgetState

internal data class GridStateWithData(val label: String?, val items: List<GridButtonData>) : GridWidgetState

internal data class GridButtonData(
    val id: String,
    val label: String,
    val icon: String,
    val state: String? = null,
    val isActive: Boolean = false,
)

internal data class GridButtonColors(
    val backgroundColor: ColorProvider,
    val contentColor: ColorProvider,
    val activeBackgroundColor: ColorProvider = backgroundColor,
    val activeContentColor: ColorProvider = contentColor,
) {
    companion object {
        val Default
            @Composable
            @ReadOnlyComposable
            get() = GridButtonColors(
                backgroundColor = GlanceTheme.colors.primary,
                contentColor = GlanceTheme.colors.onPrimary,
            )
    }
}
