package io.homeassistant.companion.android.home.views

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.IconToggleButton
import androidx.wear.compose.material3.IconToggleButtonDefaults
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SliderDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.touchTargetAwareSize
import androidx.wear.tooling.preview.devices.WearDevices
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.ColorTemperatureControl
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.EntityPosition
import io.homeassistant.companion.android.common.data.integration.FanControls
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.util.formatForLocal
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getInlineSliderDefaultColors
import io.homeassistant.companion.android.theme.wearColorScheme
import io.homeassistant.companion.android.util.getColorTemperature
import io.homeassistant.companion.android.util.onEntityClickedFeedback
import io.homeassistant.companion.android.util.onEntityFeedback
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity2
import io.homeassistant.companion.android.util.previewEntity4
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn
import java.time.format.FormatStyle

@Composable
fun DetailsPanelView(
    entity: EntityDisplay,
    onEntityToggled: (String, String) -> Unit,
    onFanSpeedChanged: (Float) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onColorTempChanged: (Float, Boolean) -> Unit,
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    WearAppTheme {
        ThemeLazyColumn {
            item {
                // Style similar to icon on frontend tile card
                val isChecked = entity.isActive
                if (entity.domain in EntityExt.DOMAINS_TOGGLE) {
                    IconToggleButton(
                        checked = isChecked,
                        onCheckedChange = {
                            onEntityToggled(entity.entityId, entity.rawState)
                            onEntityClickedFeedback(
                                isToastEnabled,
                                isHapticEnabled,
                                context,
                                entity.name,
                                haptic,
                            )
                        },
                        colors = IconToggleButtonDefaults.colors(
                            checkedContainerColor = wearColorScheme.tertiary.copy(alpha = 0.2f),
                            uncheckedContainerColor = wearColorScheme.surfaceContainerLow,
                        ),
                        modifier = Modifier.touchTargetAwareSize(IconButtonDefaults.SmallButtonSize),
                    ) {
                        Image(
                            asset = entity.icon,
                            colorFilter = ColorFilter.tint(
                                if (isChecked) wearColorScheme.tertiary else wearColorScheme.onSurface,
                            ),
                            contentDescription = stringResource(if (isChecked) R.string.enabled else R.string.disabled),
                            modifier = Modifier.size(
                                IconButtonDefaults.iconSizeFor(IconButtonDefaults.SmallButtonSize),
                            ),
                        )
                    }
                } else {
                    Image(
                        asset = entity.icon,
                        colorFilter = ColorFilter.tint(wearColorScheme.onSurface),
                    )
                }
            }
            item {
                ListHeader(entity.name)
            }

            entity.fanControls?.let { fanControls ->
                item {
                    FanSpeedSlider(fanControls, onFanSpeedChanged, isToastEnabled, isHapticEnabled)
                }
            }
            entity.lightControls?.brightness?.let { brightness ->
                item {
                    BrightnessSlider(brightness, onBrightnessChanged, isToastEnabled, isHapticEnabled)
                }
            }
            entity.lightControls?.colorTemperature?.let { colorTemperature ->
                item {
                    ColorTempSlider(colorTemperature, onColorTempChanged, isToastEnabled, isHapticEnabled)
                }
            }

            item {
                ListHeader(R.string.details)
            }
            item {
                Text(
                    stringResource(R.string.state_name, entity.rawState),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
            item {
                val lastChanged = entity.lastChanged?.formatForLocal(FormatStyle.MEDIUM).orEmpty()
                Text(
                    stringResource(R.string.last_changed, lastChanged),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
            item {
                val lastUpdated = entity.lastUpdated?.formatForLocal(FormatStyle.MEDIUM).orEmpty()
                Text(
                    stringResource(R.string.last_updated, lastUpdated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
            item {
                Text(
                    stringResource(R.string.entity_id_name, entity.entityId),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
fun FanSpeedSlider(
    controls: FanControls,
    onFanSpeedChanged: (Float) -> Unit,
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val position = controls.speed
    val steps = controls.steps

    Column {
        Text(
            stringResource(R.string.speed, position.value.toInt()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
        Slider(
            value = position.value,
            onValueChange = {
                onFanSpeedChanged(it)
                onSliderChangedFeedback(
                    isToastEnabled,
                    isHapticEnabled,
                    it > position.value,
                    context.getString(R.string.slider_fan_speed),
                    context,
                    haptic,
                )
            },
            steps = steps,
            valueRange = position.min..position.max,
            decreaseIcon = {
                Image(
                    asset = CommunityMaterial.Icon2.cmd_fan_minus,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(IconButtonDefaults.iconSizeFor(IconButtonDefaults.ExtraSmallButtonSize)),
                )
            },
            increaseIcon = {
                Image(
                    asset = CommunityMaterial.Icon2.cmd_fan_plus,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(IconButtonDefaults.iconSizeFor(IconButtonDefaults.ExtraSmallButtonSize)),
                )
            },
            modifier = Modifier.padding(bottom = 8.dp, top = 2.dp),
            colors = getInlineSliderDefaultColors(),
        )
    }
}

@Composable
fun BrightnessSlider(
    position: EntityPosition,
    onBrightnessChanged: (Float) -> Unit,
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    Column {
        Text(
            stringResource(R.string.brightness, position.value.toInt()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
        Slider(
            value = position.value,
            onValueChange = { brightness ->
                onBrightnessChanged(brightness.div(100).times(255))
                onSliderChangedFeedback(
                    isToastEnabled,
                    isHapticEnabled,
                    brightness > position.value,
                    context.getString(R.string.slider_light_brightness),
                    context,
                    haptic,
                )
            },
            steps = 20,
            valueRange = position.min..position.max,
            decreaseIcon = {
                Image(
                    asset = CommunityMaterial.Icon.cmd_brightness_4,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(IconButtonDefaults.iconSizeFor(IconButtonDefaults.ExtraSmallButtonSize)),
                )
            },
            increaseIcon = {
                Image(
                    asset = CommunityMaterial.Icon.cmd_brightness_7,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(IconButtonDefaults.iconSizeFor(IconButtonDefaults.ExtraSmallButtonSize)),
                )
            },
            modifier = Modifier.padding(bottom = 8.dp, top = 2.dp),
            colors = getInlineSliderDefaultColors(),
        )
    }
}

@Composable
fun ColorTempSlider(
    controls: ColorTemperatureControl,
    onColorTempChanged: (Float, Boolean) -> Unit,
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val useKelvin = controls.isKelvin
    val minValue = controls.min
    val maxValue = controls.max
    val currentValue = controls.current

    Column {
        Text(
            stringResource(
                R.string.color_temp,
                "${currentValue.toInt()}${if (useKelvin) " K" else ""}",
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
        Slider(
            value = currentValue,
            onValueChange = {
                onColorTempChanged(it, useKelvin)
                onSliderChangedFeedback(
                    isToastEnabled,
                    isHapticEnabled,
                    it > currentValue,
                    context.getString(R.string.slider_light_colortemp),
                    context,
                    haptic,
                )
            },
            steps = 20,
            valueRange = minValue..maxValue,
            decreaseIcon = {
                Image(
                    asset = CommunityMaterial.Icon3.cmd_thermometer_minus,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(IconButtonDefaults.iconSizeFor(IconButtonDefaults.ExtraSmallButtonSize)),
                )
            },
            increaseIcon = {
                Image(
                    asset = CommunityMaterial.Icon3.cmd_thermometer_plus,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(IconButtonDefaults.iconSizeFor(IconButtonDefaults.ExtraSmallButtonSize)),
                )
            },
            colors = SliderDefaults.sliderColors(
                selectedBarColor = getColorTemperature(
                    ratio = (currentValue - minValue).toDouble() / (maxValue - minValue).toDouble(),
                    isKelvin = useKelvin,
                ),
                containerColor = wearColorScheme.surfaceContainerLow,
            ),
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

private fun onSliderChangedFeedback(
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean,
    increase: Boolean,
    sliderName: String,
    context: Context,
    haptic: HapticFeedback,
) {
    val fullMessage =
        if (increase) {
            context.getString(R.string.slider_increased, sliderName)
        } else {
            context.getString(R.string.slider_decreased, sliderName)
        }
    onEntityFeedback(
        isToastEnabled,
        isHapticEnabled,
        fullMessage,
        context,
        haptic,
    )
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewDetailsPaneViewEntityFanOn() {
    CompositionLocalProvider {
        DetailsPanelView(
            entity = EntityDisplayWithoutContext(previewEntity4),
            onEntityToggled = { _, _ -> },
            onFanSpeedChanged = {},
            onBrightnessChanged = {},
            onColorTempChanged = { _, _ -> },
            isToastEnabled = false,
            isHapticEnabled = false,
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewDetailsPaneViewEntityLightOn() {
    CompositionLocalProvider {
        DetailsPanelView(
            entity = EntityDisplayWithoutContext(previewEntity1),
            onEntityToggled = { _, _ -> },
            onFanSpeedChanged = {},
            onBrightnessChanged = {},
            onColorTempChanged = { _, _ -> },
            isToastEnabled = false,
            isHapticEnabled = false,
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewDetailsPaneViewEntityLightOff() {
    CompositionLocalProvider {
        DetailsPanelView(
            entity = EntityDisplayWithoutContext(previewEntity2),
            onEntityToggled = { _, _ -> },
            onFanSpeedChanged = {},
            onBrightnessChanged = {},
            onColorTempChanged = { _, _ -> },
            isToastEnabled = false,
            isHapticEnabled = false,
        )
    }
}
