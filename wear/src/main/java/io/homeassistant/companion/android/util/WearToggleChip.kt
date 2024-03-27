package io.homeassistant.companion.android.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.wear.compose.material.ToggleChipColors
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material3.contentColorFor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityPosition
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.getCoverPosition
import io.homeassistant.companion.android.common.data.integration.getFanSpeed
import io.homeassistant.companion.android.common.data.integration.getLightBrightness
import io.homeassistant.companion.android.common.data.integration.getLightColor
import io.homeassistant.companion.android.theme.wearColorScheme

object WearToggleChip {
    /**
     * A function that provides chip colors that mostly follow M3 styling, but for use with M2
     * components that can to, when supported, provide a background for active entities that
     * reflects their state (position and color). M3's ToggleButton does not allow anything more
     * complicated than a single color on a button. Gradient code is based on
     * [androidx.wear.compose.material.ToggleChipDefaults.toggleChipColors].
     *
     * @param entity The entity state on which the background for the active state should be based
     */
    @Composable
    fun entityToggleChipBackgroundColors(entity: Entity<*>, checked: Boolean): ToggleChipColors {
        // For a toggleable entity, a custom background should only be used if it has:
        // a. a position (eg. fan speed, light brightness)
        // b. a custom color (eg. light color)
        // If there is a position (a) but no color (b), use the on (surfaceBright) color for the 'active' part.
        // If there is a color (b) but no position (a), use the calculated color for the background.
        // If it doesn't have either or is 'off', it should use the default off (surfaceDim) background.

        val hasPosition = when (entity.domain) {
            "cover" -> entity.state != "closed" && entity.getCoverPosition() != null
            "fan" -> checked && entity.getFanSpeed() != null
            "light" -> checked && entity.getLightBrightness() != null
            else -> false
        }
        val hasColor = entity.getLightColor() != null
        val gradientDirection = LocalLayoutDirection.current

        val contentBackgroundColor = if (hasColor) {
            val entityColor = entity.getLightColor()
            if (entityColor != null) Color(entityColor) else wearColorScheme.surfaceBright
        } else {
            wearColorScheme.surfaceBright
        }

        return when {
            (hasPosition || hasColor) -> {
                val checkedStartBackgroundColor = if (hasColor) {
                    contentBackgroundColor.copy(alpha = 0.5f).compositeOver(wearColorScheme.surfaceDim)
                } else {
                    wearColorScheme.surfaceBright
                }
                val checkedEndBackgroundColor = if (hasPosition) {
                    wearColorScheme.surfaceDim // Used as 'off' color
                } else {
                    checkedStartBackgroundColor // On no position = entire background 'on'
                }
                val uncheckedBackgroundColor = wearColorScheme.surfaceDim

                var checkedBackgroundColors = listOf(
                    checkedStartBackgroundColor,
                    checkedEndBackgroundColor
                )
                var disabledCheckedBackgroundColors = listOf(
                    checkedStartBackgroundColor.copy(alpha = 0.38f),
                    checkedEndBackgroundColor.copy(alpha = 0.38f)
                )
                val uncheckedBackgroundColors = listOf(
                    uncheckedBackgroundColor,
                    uncheckedBackgroundColor
                )
                val disabledUncheckedBackgroundColors = listOf(
                    uncheckedBackgroundColor.copy(alpha = 0.38f),
                    uncheckedBackgroundColor.copy(alpha = 0.38f)
                )
                if (gradientDirection != LayoutDirection.Ltr) {
                    checkedBackgroundColors = checkedBackgroundColors.reversed()
                    disabledCheckedBackgroundColors = disabledCheckedBackgroundColors.reversed()
                    // No need to reverse unchecked
                }

                val checkedBackgroundPaint: Painter
                val disabledCheckedBackgroundPaint: Painter
                val uncheckedBackgroundPaint: Painter
                val disabledUncheckedBackgroundPaint: Painter
                if (hasPosition) {
                    // Insert colors in the middle again to act as a 'hard stop'
                    checkedBackgroundColors = checkedBackgroundColors.toMutableList().apply {
                        addAll(1, checkedBackgroundColors)
                    }
                    disabledCheckedBackgroundColors = disabledCheckedBackgroundColors.toMutableList().apply {
                        addAll(1, disabledCheckedBackgroundColors)
                    }

                    // Use position info to provide stop points
                    // Minimum/maximum stops are not set to 0f/1f to make 1%/100% values visible
                    val position = when (entity.domain) {
                        "cover" -> entity.getCoverPosition()
                        "fan" -> entity.getFanSpeed()
                        "light" -> entity.getLightBrightness()
                        else -> null
                    } ?: EntityPosition(value = 1f, min = 0f, max = 2f) // This should never happen
                    val positionValueRelative = if (gradientDirection == LayoutDirection.Ltr) {
                        ((position.value - position.min) / (position.max - position.min))
                    } else {
                        1 - ((position.value - position.min) / (position.max - position.min))
                    }
                    val checkedColorStops = checkedBackgroundColors.mapIndexed { index, color ->
                        when (index) {
                            0 -> 0.025f to color
                            1, 2 -> positionValueRelative to color
                            else -> 0.975f to color // index: 3
                        }
                    }.toTypedArray()
                    val disabledCheckedColorStops = disabledCheckedBackgroundColors.mapIndexed { index, color ->
                        when (index) {
                            0 -> 0.025f to color
                            1, 2 -> positionValueRelative to color
                            else -> 0.975f to color // index: 3
                        }
                    }.toTypedArray()

                    // Painters that use the color stops
                    // For unchecked with position, we can reuse the checked painter
                    checkedBackgroundPaint = WearBrushPainter(
                        Brush.horizontalGradient(*checkedColorStops)
                    )
                    disabledCheckedBackgroundPaint = WearBrushPainter(
                        Brush.horizontalGradient(*disabledCheckedColorStops)
                    )
                    uncheckedBackgroundPaint = WearBrushPainter(
                        Brush.horizontalGradient(*checkedColorStops)
                    )
                    disabledUncheckedBackgroundPaint = WearBrushPainter(
                        Brush.horizontalGradient(*disabledCheckedColorStops)
                    )
                } else {
                    // Color should be towards the end to match other enabled ToggleChips
                    // No need to reverse unchecked because it is the same color
                    checkedBackgroundColors = checkedBackgroundColors.reversed()
                    disabledCheckedBackgroundColors = disabledCheckedBackgroundColors.reversed()

                    // Painters that match ToggleChipDefaults
                    checkedBackgroundPaint = WearBrushPainter(Brush.linearGradient(checkedBackgroundColors))
                    disabledCheckedBackgroundPaint = WearBrushPainter(Brush.linearGradient(disabledCheckedBackgroundColors))
                    uncheckedBackgroundPaint = WearBrushPainter(Brush.linearGradient(uncheckedBackgroundColors))
                    disabledUncheckedBackgroundPaint = WearBrushPainter(Brush.linearGradient(disabledUncheckedBackgroundColors))
                }

                defaultChipColors(
                    checkedContentColor = wearColorScheme.onPrimaryContainer,
                    checkedSecondaryContentColor = wearColorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    uncheckedContentColor = wearColorScheme.onSurface,
                    uncheckedSecondaryContentColor = wearColorScheme.onSurfaceVariant
                ).apply {
                    checkedBackgroundPainter = checkedBackgroundPaint
                    disabledCheckedBackgroundPainter = disabledCheckedBackgroundPaint
                    uncheckedBackgroundPainter = uncheckedBackgroundPaint
                    disabledUncheckedBackgroundPainter = disabledUncheckedBackgroundPaint
                }
            }
            else -> ToggleChipDefaults.toggleChipColors(
                checkedStartBackgroundColor = wearColorScheme.surfaceBright,
                checkedEndBackgroundColor = wearColorScheme.surfaceBright,
                checkedContentColor = wearColorScheme.onPrimaryContainer,
                checkedSecondaryContentColor = wearColorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                uncheckedStartBackgroundColor = wearColorScheme.surfaceDim,
                uncheckedEndBackgroundColor = wearColorScheme.surfaceDim,
                uncheckedContentColor = wearColorScheme.onSurface,
                uncheckedSecondaryContentColor = wearColorScheme.onSurfaceVariant
            )
        }
    }

    /**
     * A copy of [androidx.wear.compose.material.ToggleChipDefaults.toggleChipColors] that returns
     * [WearToggleChipColors] which allows the app to set the Painter for advanced use cases instead
     * of only providing a Color.
     */
    @Composable
    private fun defaultChipColors(
        checkedStartBackgroundColor: Color =
            wearColorScheme.outlineVariant.copy(alpha = 0f)
                .compositeOver(wearColorScheme.tertiary),
        checkedEndBackgroundColor: Color =
            wearColorScheme.primary.copy(alpha = 0.5f)
                .compositeOver(wearColorScheme.tertiary),
        checkedContentColor: Color = wearColorScheme.onTertiary,
        checkedSecondaryContentColor: Color = wearColorScheme.tertiary,
        checkedToggleControlColor: Color = wearColorScheme.tertiary,
        uncheckedStartBackgroundColor: Color = wearColorScheme.outlineVariant,
        uncheckedEndBackgroundColor: Color = uncheckedStartBackgroundColor,
        uncheckedContentColor: Color = contentColorFor(checkedEndBackgroundColor),
        uncheckedSecondaryContentColor: Color = uncheckedContentColor,
        uncheckedToggleControlColor: Color = uncheckedContentColor,
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
                checkedStartBackgroundColor.copy(alpha = 0.38f),
                checkedEndBackgroundColor.copy(alpha = 0.38f)
            )
        } else {
            checkedBackgroundColors = listOf(
                checkedEndBackgroundColor,
                checkedStartBackgroundColor
            )
            disabledCheckedBackgroundColors = listOf(
                checkedEndBackgroundColor.copy(alpha = 0.38f),
                checkedStartBackgroundColor.copy(alpha = 0.38f)
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
                uncheckedStartBackgroundColor.copy(alpha = 0.38f),
                uncheckedEndBackgroundColor.copy(alpha = 0.38f)
            )
        } else {
            uncheckedBackgroundColors = listOf(
                uncheckedEndBackgroundColor,
                uncheckedStartBackgroundColor
            )
            disabledUncheckedBackgroundColors = listOf(
                uncheckedEndBackgroundColor.copy(alpha = 0.38f),
                uncheckedStartBackgroundColor.copy(alpha = 0.38f)
            )
        }

        return WearToggleChipColors(
            checkedBackgroundPainter = WearBrushPainter(Brush.linearGradient(checkedBackgroundColors)),
            checkedContentColor = checkedContentColor,
            checkedSecondaryContentColor = checkedSecondaryContentColor,
            checkedIconColor = checkedToggleControlColor,
            uncheckedBackgroundPainter = WearBrushPainter(
                Brush.linearGradient(uncheckedBackgroundColors)
            ),
            uncheckedContentColor = uncheckedContentColor,
            uncheckedSecondaryContentColor = uncheckedSecondaryContentColor,
            uncheckedIconColor = uncheckedToggleControlColor,
            disabledCheckedBackgroundPainter = WearBrushPainter(
                Brush.linearGradient(disabledCheckedBackgroundColors)
            ),
            disabledCheckedContentColor = checkedContentColor.copy(alpha = 0.38f),
            disabledCheckedSecondaryContentColor = checkedSecondaryContentColor.copy(
                alpha = 0.38f
            ),
            disabledCheckedIconColor = checkedToggleControlColor.copy(
                alpha = 0.38f
            ),
            disabledUncheckedBackgroundPainter = WearBrushPainter(
                Brush.linearGradient(disabledUncheckedBackgroundColors)
            ),
            disabledUncheckedContentColor = uncheckedContentColor.copy(
                alpha = 0.38f
            ),
            disabledUncheckedSecondaryContentColor = uncheckedSecondaryContentColor.copy(
                alpha = 0.38f
            ),
            disabledUncheckedIconColor = uncheckedToggleControlColor.copy(
                alpha = 0.38f
            )
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
    var checkedIconColor: Color,
    var disabledCheckedBackgroundPainter: Painter,
    var disabledCheckedContentColor: Color,
    var disabledCheckedSecondaryContentColor: Color,
    var disabledCheckedIconColor: Color,
    var uncheckedBackgroundPainter: Painter,
    var uncheckedContentColor: Color,
    var uncheckedSecondaryContentColor: Color,
    var uncheckedIconColor: Color,
    var disabledUncheckedBackgroundPainter: Painter,
    var disabledUncheckedContentColor: Color,
    var disabledUncheckedSecondaryContentColor: Color,
    var disabledUncheckedIconColor: Color
) : ToggleChipColors {

    @Composable
    override fun background(enabled: Boolean, checked: Boolean): State<Painter> {
        return rememberUpdatedState(
            if (enabled) {
                if (checked) checkedBackgroundPainter else uncheckedBackgroundPainter
            } else {
                if (checked) {
                    disabledCheckedBackgroundPainter
                } else {
                    disabledUncheckedBackgroundPainter
                }
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
                if (checked) {
                    disabledCheckedSecondaryContentColor
                } else {
                    disabledUncheckedSecondaryContentColor
                }
            }
        )
    }

    @Composable
    override fun toggleControlColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(
            if (enabled) {
                if (checked) checkedIconColor else uncheckedIconColor
            } else {
                if (checked) disabledCheckedIconColor else disabledUncheckedIconColor
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
        if (checkedIconColor != other.checkedIconColor) return false
        if (checkedSecondaryContentColor != other.checkedSecondaryContentColor) return false
        if (uncheckedBackgroundPainter != other.uncheckedBackgroundPainter) return false
        if (uncheckedContentColor != other.uncheckedContentColor) return false
        if (uncheckedIconColor != other.uncheckedIconColor) return false
        if (uncheckedSecondaryContentColor != other.uncheckedSecondaryContentColor) return false
        if (disabledCheckedBackgroundPainter != other.disabledCheckedBackgroundPainter) return false
        if (disabledCheckedContentColor != other.disabledCheckedContentColor) return false
        if (disabledCheckedIconColor != other.disabledCheckedIconColor) return false
        if (disabledCheckedSecondaryContentColor !=
            other.disabledCheckedSecondaryContentColor
        ) {
            return false
        }
        if (disabledUncheckedBackgroundPainter !=
            other.disabledUncheckedBackgroundPainter
        ) {
            return false
        }
        if (disabledUncheckedContentColor != other.disabledUncheckedContentColor) return false
        if (disabledUncheckedIconColor != other.disabledUncheckedIconColor) return false
        if (disabledUncheckedSecondaryContentColor !=
            other.disabledUncheckedSecondaryContentColor
        ) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = checkedBackgroundPainter.hashCode()
        result = 31 * result + checkedContentColor.hashCode()
        result = 31 * result + checkedSecondaryContentColor.hashCode()
        result = 31 * result + checkedIconColor.hashCode()
        result = 31 * result + uncheckedBackgroundPainter.hashCode()
        result = 31 * result + uncheckedContentColor.hashCode()
        result = 31 * result + uncheckedSecondaryContentColor.hashCode()
        result = 31 * result + uncheckedIconColor.hashCode()
        result = 31 * result + disabledCheckedBackgroundPainter.hashCode()
        result = 31 * result + disabledCheckedContentColor.hashCode()
        result = 31 * result + disabledCheckedSecondaryContentColor.hashCode()
        result = 31 * result + disabledCheckedIconColor.hashCode()
        result = 31 * result + disabledUncheckedBackgroundPainter.hashCode()
        result = 31 * result + disabledUncheckedContentColor.hashCode()
        result = 31 * result + disabledUncheckedSecondaryContentColor.hashCode()
        result = 31 * result + disabledUncheckedIconColor.hashCode()
        return result
    }
}

/**
 * A copy of [androidx.wear.compose.material.BrushPainter] because that class is marked as internal,
 * but contains an important override of the `intrinsicSize` property to Size.Unspecified which allows
 * it to work when using a horizontal gradient as the Chip background.
 * [androidx.compose.ui.graphics.painter.BrushPainter] only works for gradients that do not specify
 * offsets, so only linear gradients from top left to bottom right (= diagonal).
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
        if (other !is WearBrushPainter) return false

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
