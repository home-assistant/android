package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ContentAlpha
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipColors
import androidx.wear.compose.material.ToggleChipDefaults
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityPosition
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.getCoverPosition
import io.homeassistant.companion.android.common.data.integration.getFanSpeed
import io.homeassistant.companion.android.common.data.integration.getLightBrightness
import io.homeassistant.companion.android.common.data.integration.getLightColor
import io.homeassistant.companion.android.home.HomePresenterImpl
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.WearBrushPainter
import io.homeassistant.companion.android.util.WearToggleChipDefaults
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.onEntityClickedFeedback
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity3

@Composable
fun EntityUi(
    entity: Entity<*>,
    onEntityClicked: (String, String) -> Unit,
    isHapticEnabled: Boolean,
    isToastEnabled: Boolean,
    onEntityLongPressed: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val attributes = entity.attributes as Map<*, *>
    val iconBitmap = getIcon(entity as Entity<Map<String, Any>>, entity.domain, LocalContext.current)
    val friendlyName = attributes["friendly_name"].toString()

    if (entity.domain in HomePresenterImpl.toggleDomains) {
        val isChecked = entity.state in listOf("on", "locked", "open", "opening")
        ToggleChip(
            checked = isChecked,
            onCheckedChange = {
                onEntityClicked(entity.entityId, entity.state)
                onEntityClickedFeedback(isToastEnabled, isHapticEnabled, context, friendlyName, haptic)
            },
            modifier = Modifier
                .fillMaxWidth(),
            appIcon = {
                Image(
                    asset = iconBitmap ?: CommunityMaterial.Icon.cmd_bookmark,
                    colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                )
            },
            label = {
                Text(
                    text = friendlyName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                onEntityClicked(entity.entityId, entity.state)
                                onEntityClickedFeedback(
                                    isToastEnabled,
                                    isHapticEnabled,
                                    context,
                                    friendlyName,
                                    haptic
                                )
                            },
                            onLongPress = {
                                onEntityLongPressed(entity.entityId)
                            }
                        )
                    }
                )
            },
            enabled = entity.state != "unavailable",
            toggleIcon = { ToggleChipDefaults.SwitchIcon(isChecked) },
            colors = entityToggleChipBackgroundColors(entity, isChecked)
        )
    } else {
        Chip(
            modifier = Modifier
                .fillMaxWidth(),
            icon = {
                Image(
                    asset = iconBitmap ?: CommunityMaterial.Icon.cmd_bookmark,
                    colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                )
            },
            label = {
                Text(
                    text = friendlyName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                onEntityClicked(entity.entityId, entity.state)
                                onEntityClickedFeedback(
                                    isToastEnabled,
                                    isHapticEnabled,
                                    context,
                                    friendlyName,
                                    haptic
                                )
                            },
                            onLongPress = {
                                onEntityLongPressed(entity.entityId)
                            }
                        )
                    }
                )
            },
            enabled = entity.state != "unavailable",
            onClick = {
                onEntityClicked(entity.entityId, entity.state)
                onEntityClickedFeedback(isToastEnabled, isHapticEnabled, context, friendlyName, haptic)
            },
            colors = ChipDefaults.secondaryChipColors()
        )
    }
}

/**
 * A function that provides chip colors that mostly follow the default toggle chip colors, but when supported
 * provide a background for active entities that reflects their state (position and color). Gradient code
 * is based on [androidx.wear.compose.material.ToggleChipDefaults.toggleChipColors].
 *
 * @param entity The entity state on which the background for the active state should be based
 */
@Composable
private fun entityToggleChipBackgroundColors(entity: Entity<*>, checked: Boolean): ToggleChipColors {
    // For a toggleable entity, a custom background should only be used if it has:
    // a. a position (eg. fan speed, light brightness)
    // b. a custom color (eg. light color)
    // If there is a position (a) but no color (b), use the default (theme) color for the 'active' part.
    // If there is a color (b) but no position (a), use a smooth gradient similar to ToggleChip.
    // If it doesn't have either or is 'off', it should use the default chip background.

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
        if (entityColor != null) Color(entityColor) else MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.primary
    }

    return when {
        (hasPosition || hasColor) -> {
            val checkedStartBackgroundColor = contentBackgroundColor.copy(alpha = 0.325f)
            val checkedEndBackgroundColor = MaterialTheme.colors.surface
            val uncheckedBackgroundColor = MaterialTheme.colors.surface

            var checkedBackgroundColors = listOf(
                checkedStartBackgroundColor,
                checkedEndBackgroundColor
            )
            var disabledCheckedBackgroundColors = listOf(
                checkedStartBackgroundColor.copy(alpha = ContentAlpha.disabled),
                checkedEndBackgroundColor.copy(alpha = ContentAlpha.disabled)
            )
            val uncheckedBackgroundColors = listOf(
                uncheckedBackgroundColor,
                uncheckedBackgroundColor
            )
            val disabledUncheckedBackgroundColors = listOf(
                uncheckedBackgroundColor.copy(alpha = ContentAlpha.disabled),
                uncheckedBackgroundColor.copy(alpha = ContentAlpha.disabled)
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

            WearToggleChipDefaults.defaultChipColors().apply {
                checkedBackgroundPainter = checkedBackgroundPaint
                disabledCheckedBackgroundPainter = disabledCheckedBackgroundPaint
                uncheckedBackgroundPainter = uncheckedBackgroundPaint
                disabledUncheckedBackgroundPainter = disabledUncheckedBackgroundPaint
            }
        }
        else -> ToggleChipDefaults.toggleChipColors()
    }
}

@Preview
@Composable
private fun PreviewEntityUI() {
    Column {
        EntityUi(
            entity = previewEntity1,
            onEntityClicked = { _, _ -> },
            isHapticEnabled = true,
            isToastEnabled = false,
            onEntityLongPressed = { _ -> }
        )
        EntityUi(
            entity = previewEntity3,
            onEntityClicked = { _, _ -> },
            isHapticEnabled = false,
            isToastEnabled = true,
            onEntityLongPressed = { _ -> }
        )
    }
}
