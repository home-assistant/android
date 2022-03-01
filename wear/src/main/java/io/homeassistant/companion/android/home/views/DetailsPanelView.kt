package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import io.homeassistant.companion.android.common.data.integration.supportsLightBrightness
import io.homeassistant.companion.android.common.data.integration.supportsLightColorTemperature
import io.homeassistant.companion.android.home.HomePresenterImpl
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.util.getColorTemperature
import java.text.DateFormat

@ExperimentalComposeUiApi
@Composable
fun DetailsPanelView(
    entity: Entity<*>,
    onEntityToggled: (String, String) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onColorTempChanged: (Float) -> Unit
) {
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
                            onCheckedChange = { onEntityToggled(entity.entityId, entity.state) },
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .size(ToggleButtonDefaults.SmallToggleButtonSize)
                        ) {
                            ToggleChipDefaults.SwitchIcon(checked = isChecked)
                        }
                    }
                }
            }

            if (entity.domain == "light") {
                if (entity.supportsLightBrightness()) {
                    item {
                        BrightnessSlider(attributes, onBrightnessChanged)
                    }
                }

                if (entity.supportsLightColorTemperature() && attributes["color_mode"] == EntityExt.LIGHT_MODE_COLOR_TEMP) {
                    item {
                        ColorTempSlider(attributes, onColorTempChanged)
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
fun BrightnessSlider(attributes: Map<*, *>, onBrightnessChanged: (Float) -> Unit) {
    val minValue = 0f
    val maxValue = 100f
    var currentValue =
        (attributes["brightness"] as? Number)?.toFloat()?.div(255f)?.times(100)
            ?: 0f
    if (currentValue < minValue)
        currentValue = minValue
    if (currentValue > maxValue)
        currentValue = maxValue

    Column {
        Text(
            stringResource(R.string.brightness, currentValue.toInt()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
        InlineSlider(
            value = currentValue,
            onValueChange = { brightness ->
                onBrightnessChanged(brightness.div(100).times(255))
            },
            steps = 20,
            valueRange = minValue..maxValue,
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
fun ColorTempSlider(attributes: Map<*, *>, onColorTempChanged: (Float) -> Unit) {
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
            onValueChange = onColorTempChanged,
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
