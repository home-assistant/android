package io.homeassistant.companion.android.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BrushPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.wear.compose.material.ContentAlpha
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ToggleChipColors
import androidx.wear.compose.material.contentColorFor

object WearToggleChipDefaults {
    /**
     * A copy of [androidx.wear.compose.material.ToggleChipDefaults.toggleChipColors] that returns
     * [WearToggleChipColors] which allows the app to set the Painter for advanced use cases instead
     * of only providing a Color.
     */
    @Composable
    fun defaultChipColors(
        checkedStartBackgroundColor: Color = MaterialTheme.colors.surface.copy(alpha = 0.75f),
        checkedEndBackgroundColor: Color = MaterialTheme.colors.primary.copy(alpha = 0.325f),
        checkedContentColor: Color = MaterialTheme.colors.onSurface,
        checkedSecondaryContentColor: Color = MaterialTheme.colors.onSurfaceVariant,
        checkedToggleIconTintColor: Color = MaterialTheme.colors.secondary,
        uncheckedStartBackgroundColor: Color = MaterialTheme.colors.surface,
        uncheckedEndBackgroundColor: Color = MaterialTheme.colors.surface,
        uncheckedContentColor: Color = contentColorFor(checkedEndBackgroundColor),
        uncheckedSecondaryContentColor: Color = uncheckedContentColor,
        uncheckedToggleIconTintColor: Color = uncheckedContentColor,
        splitBackgroundOverlayColor: Color = Color.White.copy(alpha = 0.05f),
        gradientDirection: LayoutDirection = LocalLayoutDirection.current
    ): WearToggleChipColors {
        val checkedBackgroundColors: List<Color>
        val disabledCheckedBackgroundColors: List<Color>
        if (gradientDirection == LayoutDirection.Ltr) {
            checkedBackgroundColors = listOf(
                checkedStartBackgroundColor,
                checkedEndBackgroundColor
            )
            disabledCheckedBackgroundColors = listOf(
                checkedStartBackgroundColor.copy(alpha = ContentAlpha.disabled),
                checkedEndBackgroundColor.copy(alpha = ContentAlpha.disabled)
            )
        } else {
            checkedBackgroundColors = listOf(
                checkedEndBackgroundColor,
                checkedStartBackgroundColor
            )
            disabledCheckedBackgroundColors = listOf(
                checkedEndBackgroundColor.copy(alpha = ContentAlpha.disabled),
                checkedStartBackgroundColor.copy(alpha = ContentAlpha.disabled),
            )
        }
        val uncheckedBackgroundColors: List<Color>
        val disabledUncheckedBackgroundColors: List<Color>
        if (gradientDirection == LayoutDirection.Ltr) {
            uncheckedBackgroundColors = listOf(
                uncheckedStartBackgroundColor,
                uncheckedEndBackgroundColor
            )
            disabledUncheckedBackgroundColors = listOf(
                uncheckedStartBackgroundColor.copy(alpha = ContentAlpha.disabled),
                uncheckedEndBackgroundColor.copy(alpha = ContentAlpha.disabled)
            )
        } else {
            uncheckedBackgroundColors = listOf(
                uncheckedEndBackgroundColor,
                uncheckedStartBackgroundColor
            )
            disabledUncheckedBackgroundColors = listOf(
                uncheckedEndBackgroundColor.copy(alpha = ContentAlpha.disabled),
                uncheckedStartBackgroundColor.copy(alpha = ContentAlpha.disabled),
            )
        }

        return WearToggleChipColors(
            checkedBackgroundPainter = BrushPainter(Brush.linearGradient(checkedBackgroundColors)),
            checkedContentColor = checkedContentColor,
            checkedSecondaryContentColor = checkedSecondaryContentColor,
            checkedIconTintColor = checkedToggleIconTintColor,
            checkedSplitBackgroundOverlay = splitBackgroundOverlayColor,
            uncheckedBackgroundPainter = BrushPainter(
                Brush.linearGradient(uncheckedBackgroundColors)
            ),
            uncheckedContentColor = uncheckedContentColor,
            uncheckedSecondaryContentColor = uncheckedSecondaryContentColor,
            uncheckedIconTintColor = uncheckedToggleIconTintColor,
            uncheckedSplitBackgroundOverlay = splitBackgroundOverlayColor,
            disabledCheckedBackgroundPainter = BrushPainter(
                Brush.linearGradient(disabledCheckedBackgroundColors)
            ),
            disabledCheckedContentColor = checkedContentColor.copy(alpha = ContentAlpha.disabled),
            disabledCheckedSecondaryContentColor = checkedSecondaryContentColor.copy(
                alpha = ContentAlpha.disabled
            ),
            disabledCheckedIconTintColor = checkedToggleIconTintColor.copy(
                alpha = ContentAlpha.disabled
            ),
            disabledCheckedSplitBackgroundOverlay = splitBackgroundOverlayColor,
            disabledUncheckedBackgroundPainter = BrushPainter(
                Brush.linearGradient(disabledUncheckedBackgroundColors)
            ),
            disabledUncheckedContentColor = uncheckedContentColor.copy(
                alpha = ContentAlpha.disabled
            ),
            disabledUncheckedSecondaryContentColor = uncheckedSecondaryContentColor.copy(
                alpha = ContentAlpha.disabled
            ),
            disabledUncheckedIconTintColor = uncheckedToggleIconTintColor.copy(
                alpha = ContentAlpha.disabled
            ),
            disabledUncheckedSplitBackgroundOverlay = splitBackgroundOverlayColor
        )
    }
}

/**
 * A copy of [androidx.wear.compose.material.DefaultToggleChipColors] with a public constructor and mutable
 * properties to allow the app to set the Painter for advanced use cases instead of only providing a Color.
 */
class WearToggleChipColors(
    var checkedBackgroundPainter: Painter,
    var checkedContentColor: Color,
    var checkedSecondaryContentColor: Color,
    var checkedIconTintColor: Color,
    var checkedSplitBackgroundOverlay: Color,
    var disabledCheckedBackgroundPainter: Painter,
    var disabledCheckedContentColor: Color,
    var disabledCheckedSecondaryContentColor: Color,
    var disabledCheckedIconTintColor: Color,
    var disabledCheckedSplitBackgroundOverlay: Color,
    var uncheckedBackgroundPainter: Painter,
    var uncheckedContentColor: Color,
    var uncheckedSecondaryContentColor: Color,
    var uncheckedIconTintColor: Color,
    var uncheckedSplitBackgroundOverlay: Color,
    var disabledUncheckedBackgroundPainter: Painter,
    var disabledUncheckedContentColor: Color,
    var disabledUncheckedSecondaryContentColor: Color,
    var disabledUncheckedIconTintColor: Color,
    var disabledUncheckedSplitBackgroundOverlay: Color,
) : ToggleChipColors {

    @Composable
    override fun background(enabled: Boolean, checked: Boolean): State<Painter> {
        return rememberUpdatedState(
            if (enabled) {
                if (checked) checkedBackgroundPainter else uncheckedBackgroundPainter
            } else {
                if (checked) disabledCheckedBackgroundPainter else
                    disabledUncheckedBackgroundPainter
            }
        )
    }

    @Composable
    override fun contentColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(
            if (enabled) {
                if (checked) checkedContentColor else uncheckedContentColor
            } else {
                if (checked) disabledCheckedContentColor else disabledUncheckedContentColor
            }
        )
    }

    @Composable
    override fun secondaryContentColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(
            if (enabled) {
                if (checked) checkedSecondaryContentColor else uncheckedSecondaryContentColor
            } else {
                if (checked) disabledCheckedSecondaryContentColor else
                    disabledUncheckedSecondaryContentColor
            }
        )
    }

    @Composable
    override fun toggleIconTintColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(
            if (enabled) {
                if (checked) checkedIconTintColor else uncheckedIconTintColor
            } else {
                if (checked) disabledCheckedIconTintColor else disabledUncheckedIconTintColor
            }
        )
    }

    @Composable
    override fun splitBackgroundOverlay(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(
            if (enabled) {
                if (checked) checkedSplitBackgroundOverlay else uncheckedSplitBackgroundOverlay
            } else {
                if (checked) disabledCheckedSplitBackgroundOverlay else
                    disabledUncheckedSplitBackgroundOverlay
            }
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        other as WearToggleChipColors

        if (checkedBackgroundPainter != other.checkedBackgroundPainter) return false
        if (checkedContentColor != other.checkedContentColor) return false
        if (checkedIconTintColor != other.checkedIconTintColor) return false
        if (checkedSecondaryContentColor != other.checkedSecondaryContentColor) return false
        if (checkedSplitBackgroundOverlay != other.checkedSplitBackgroundOverlay) return false
        if (uncheckedBackgroundPainter != other.uncheckedBackgroundPainter) return false
        if (uncheckedContentColor != other.uncheckedContentColor) return false
        if (uncheckedIconTintColor != other.uncheckedIconTintColor) return false
        if (uncheckedSecondaryContentColor != other.uncheckedSecondaryContentColor) return false
        if (uncheckedSplitBackgroundOverlay != other.uncheckedSplitBackgroundOverlay) return false
        if (disabledCheckedBackgroundPainter != other.disabledCheckedBackgroundPainter) return false
        if (disabledCheckedContentColor != other.disabledCheckedContentColor) return false
        if (disabledCheckedIconTintColor != other.disabledCheckedIconTintColor) return false
        if (disabledCheckedSecondaryContentColor !=
            other.disabledCheckedSecondaryContentColor
        ) return false
        if (disabledCheckedSplitBackgroundOverlay !=
            other.disabledCheckedSplitBackgroundOverlay
        ) return false
        if (disabledUncheckedBackgroundPainter !=
            other.disabledUncheckedBackgroundPainter
        ) return false
        if (disabledUncheckedContentColor != other.disabledUncheckedContentColor) return false
        if (disabledUncheckedIconTintColor != other.disabledUncheckedIconTintColor) return false
        if (disabledUncheckedSecondaryContentColor !=
            other.disabledUncheckedSecondaryContentColor
        ) return false
        if (disabledUncheckedSplitBackgroundOverlay !=
            other.disabledUncheckedSplitBackgroundOverlay
        ) return false

        return true
    }

    override fun hashCode(): Int {
        var result = checkedBackgroundPainter.hashCode()
        result = 31 * result + checkedContentColor.hashCode()
        result = 31 * result + checkedSecondaryContentColor.hashCode()
        result = 31 * result + checkedIconTintColor.hashCode()
        result = 31 * result + checkedSplitBackgroundOverlay.hashCode()
        result = 31 * result + uncheckedBackgroundPainter.hashCode()
        result = 31 * result + uncheckedContentColor.hashCode()
        result = 31 * result + uncheckedSecondaryContentColor.hashCode()
        result = 31 * result + uncheckedIconTintColor.hashCode()
        result = 31 * result + uncheckedSplitBackgroundOverlay.hashCode()
        result = 31 * result + disabledCheckedBackgroundPainter.hashCode()
        result = 31 * result + disabledCheckedContentColor.hashCode()
        result = 31 * result + disabledCheckedSecondaryContentColor.hashCode()
        result = 31 * result + disabledCheckedIconTintColor.hashCode()
        result = 31 * result + disabledCheckedSplitBackgroundOverlay.hashCode()
        result = 31 * result + disabledUncheckedBackgroundPainter.hashCode()
        result = 31 * result + disabledUncheckedContentColor.hashCode()
        result = 31 * result + disabledUncheckedSecondaryContentColor.hashCode()
        result = 31 * result + disabledUncheckedIconTintColor.hashCode()
        result = 31 * result + disabledUncheckedSplitBackgroundOverlay.hashCode()
        return result
    }
}

/**
 * A copy of [androidx.wear.compose.material.BrushPainter] because that class is marked as internal,
 * but contains an important override of the `intrinsicSize` property to Size.Unspecified which allows
 * it to work when using it with a Chip, which [androidx.compose.ui.graphics.painter.BrushPainter] does not.
 */
class WearBrushPainter(val brush: Brush) : Painter() {
    private var alpha: Float = 1.0f

    private var colorFilter: ColorFilter? = null

    override fun DrawScope.onDraw() {
        drawRect(brush = brush, alpha = alpha, colorFilter = colorFilter)
    }

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BrushPainter) return false

        if (brush != other.brush) return false

        return true
    }

    override fun hashCode(): Int {
        return brush.hashCode()
    }

    override fun toString(): String {
        return "ColorPainter(brush=$brush)"
    }

    override val intrinsicSize: Size = Size.Unspecified
}
