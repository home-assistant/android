package io.homeassistant.companion.android.home.views

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleButton
import androidx.wear.compose.material.ToggleButtonDefaults
import androidx.wear.compose.material.ToggleChipDefaults
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.getFanSpeed
import io.homeassistant.companion.android.common.data.integration.getLightBrightness
import io.homeassistant.companion.android.common.data.integration.supportsFanSetSpeed
import io.homeassistant.companion.android.common.data.integration.supportsLightBrightness
import io.homeassistant.companion.android.common.data.integration.supportsLightColorTemperature
import io.homeassistant.companion.android.home.HomePresenterImpl
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.util.getColorTemperature
import io.homeassistant.companion.android.util.onEntityClickedFeedback
import io.homeassistant.companion.android.util.onEntityFeedback
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn
import java.text.DateFormat

@Composable
fun DetailsPanelView(
    entity: Entity<*>,
    onEntityToggled: (String, String) -> Unit,
    onFanSpeedChanged: (Float) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onColorTempChanged: (Float) -> Unit,
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    WearAppTheme {
        ThemeLazyColumn {
            val attributes = entity.attributes as Map<*, *>

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val friendlyName = attributes["friendly_name"].toString()
                    Text(friendlyName)

                    if (entity.domain in HomePresenterImpl.toggleDomains) {
                        val isChecked = entity.state in listOf("on", "locked", "open", "opening")
                        ToggleButton(
                            checked = isChecked,
                            onCheckedChange = {
                                onEntityToggled(entity.entityId, entity.state)
                                onEntityClickedFeedback(
                                    isToastEnabled,
                                    isHapticEnabled,
                                    context,
                                    friendlyName,
                                    haptic
                                )
                            },
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .size(ToggleButtonDefaults.SmallToggleButtonSize)
                        ) {
                            Icon(
                                imageVector = ToggleChipDefaults.switchIcon(isChecked),
                                contentDescription = if (isChecked)
                                    stringResource(R.string.enabled)
                                else
                                    stringResource(R.string.disabled)
                            )
                        }
                    }
                }
            }

            if (entity.domain == "fan") {
                if (entity.supportsFanSetSpeed()) {
                    item {
                        FanSpeedSlider(entity, onFanSpeedChanged, isToastEnabled, isHapticEnabled)
                    }
                }
            }
            if (entity.domain == "light") {
                if (entity.supportsLightBrightness()) {
                    item {
                        BrightnessSlider(entity, onBrightnessChanged, isToastEnabled, isHapticEnabled)
                    }
                }

                if (entity.supportsLightColorTemperature() && attributes["color_mode"] == EntityExt.LIGHT_MODE_COLOR_TEMP) {
                    item {
                        ColorTempSlider(attributes, onColorTempChanged, isToastEnabled, isHapticEnabled)
                    }
                }
            }

            item {
                ListHeader(R.string.details)
            }
            item {
                Text(
                    stringResource(R.string.state_name, entity.state),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
            item {
                val lastChanged = DateFormat.getDateTimeInstance().format(entity.lastChanged.time)
                Text(
                    stringResource(R.string.last_changed, lastChanged),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
            item {
                val lastUpdated = DateFormat.getDateTimeInstance().format(entity.lastUpdated.time)
                Text(
                    stringResource(R.string.last_updated, lastUpdated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
            item {
                Text(
                    stringResource(R.string.entity_id_name, entity.entityId),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
fun FanSpeedSlider(
    entity: Entity<*>,
    onFanSpeedChanged: (Float) -> Unit,
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val position = entity.getFanSpeed() ?: return

    Column {
        Text(
            stringResource(R.string.speed, position.value.toInt()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
        InlineSlider(
            value = position.value,
            onValueChange = {
                onFanSpeedChanged(it)
                onSliderChangedFeedback(
                    isToastEnabled,
                    isHapticEnabled,
                    it > position.value,
                    context.getString(R.string.slider_fan_speed),
                    context,
                    haptic
                )
            },
            steps = 9,
            valueRange = position.min..position.max,
            decreaseIcon = {
                Image(
                    asset = CommunityMaterial.Icon2.cmd_fan_minus,
                    colorFilter = ColorFilter.tint(Color.White)
                )
            },
            increaseIcon = {
                Image(
                    asset = CommunityMaterial.Icon2.cmd_fan_plus,
                    colorFilter = ColorFilter.tint(Color.White)
                )
            },
            modifier = Modifier.padding(bottom = 8.dp, top = 2.dp)
        )
    }
}

@Composable
fun BrightnessSlider(
    entity: Entity<*>,
    onBrightnessChanged: (Float) -> Unit,
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val position = entity.getLightBrightness() ?: return

    Column {
        Text(
            stringResource(R.string.brightness, position.value.toInt()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
        InlineSlider(
            value = position.value,
            onValueChange = { brightness ->
                onBrightnessChanged(brightness.div(100).times(255))
                onSliderChangedFeedback(
                    isToastEnabled,
                    isHapticEnabled,
                    brightness > position.value,
                    context.getString(R.string.slider_light_brightness),
                    context,
                    haptic
                )
            },
            steps = 20,
            valueRange = position.min..position.max,
            decreaseIcon = {
                Image(
                    asset = CommunityMaterial.Icon.cmd_brightness_4,
                    colorFilter = ColorFilter.tint(Color.White)
                )
            },
            increaseIcon = {
                Image(
                    asset = CommunityMaterial.Icon.cmd_brightness_7,
                    colorFilter = ColorFilter.tint(Color.White)
                )
            },
            modifier = Modifier.padding(bottom = 8.dp, top = 2.dp)
        )
    }
}

@Composable
fun ColorTempSlider(
    attributes: Map<*, *>,
    onColorTempChanged: (Float) -> Unit,
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val minValue = (attributes["min_mireds"] as? Number)?.toFloat() ?: 0f
    val maxValue = (attributes["max_mireds"] as? Number)?.toFloat() ?: 0f
    var currentValue = (attributes["color_temp"] as? Number)?.toFloat() ?: 0f
    if (currentValue < minValue)
        currentValue = minValue
    if (currentValue > maxValue)
        currentValue = maxValue

    Column {
        Text(
            stringResource(R.string.color_temp, currentValue.toInt()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
        InlineSlider(
            value = currentValue,
            onValueChange = {
                onColorTempChanged(it)
                onSliderChangedFeedback(
                    isToastEnabled,
                    isHapticEnabled,
                    it > currentValue,
                    context.getString(R.string.slider_light_colortemp),
                    context,
                    haptic
                )
            },
            steps = 20,
            valueRange = minValue..maxValue,
            decreaseIcon = {
                Image(
                    asset = CommunityMaterial.Icon3.cmd_thermometer_minus,
                    colorFilter = ColorFilter.tint(Color.White)
                )
            },
            increaseIcon = {
                Image(
                    asset = CommunityMaterial.Icon3.cmd_thermometer_plus,
                    colorFilter = ColorFilter.tint(Color.White)
                )
            },
            colors = InlineSliderDefaults.colors(
                selectedBarColor = getColorTemperature(
                    (currentValue - minValue).toDouble() / (maxValue - minValue).toDouble()
                )
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

private fun onSliderChangedFeedback(
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean,
    increase: Boolean,
    sliderName: String,
    context: Context,
    haptic: HapticFeedback
) {
    val fullMessage =
        if (increase) context.getString(R.string.slider_increased, sliderName)
        else context.getString(R.string.slider_decreased, sliderName)
    onEntityFeedback(
        isToastEnabled,
        isHapticEnabled,
        fullMessage,
        context,
        haptic
    )
}
