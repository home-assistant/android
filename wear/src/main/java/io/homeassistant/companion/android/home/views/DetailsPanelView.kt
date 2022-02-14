package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.Text
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.theme.WearAppTheme
import java.text.DateFormat

const val SUPPORT_BRIGHTNESS_DEPR = 1
val NO_BRIGHTNESS_SUPPORT = listOf("unknown", "onoff")
const val SUPPORT_COLOR_TEMP_DEPR = 2
const val SUPPORT_COLOR_TEMP = "color_temp"

@ExperimentalComposeUiApi
@Composable
fun DetailsPanelView(
    entity: Entity<*>,
    onBrightnessChanged: (Float) -> Unit,
    onColorTempChanged: (Float) -> Unit
) {
    WearAppTheme {
        ThemeLazyColumn {
            val attributes = entity.attributes as Map<*, *>
            item {
                val friendlyName = attributes["friendly_name"].toString()
                ListHeader(friendlyName)
            }

            if (entity.entityId.split('.')[0] == "light") {
                // Brightness
                // On HA Core 2021.5 and later brightness detection has changed
                // to simplify things in the app lets use both methods for now
                val supportedColorModes = attributes["supported_color_modes"] as? List<String>
                val supportsBrightness =
                    if (supportedColorModes == null) false else (supportedColorModes - NO_BRIGHTNESS_SUPPORT).isNotEmpty()
                if (supportsBrightness || ((attributes["supported_features"] as Int) and SUPPORT_BRIGHTNESS_DEPR == SUPPORT_BRIGHTNESS_DEPR)) {
                    val minValue = 0f
                    val maxValue = 100f
                    var currentValue =
                        (attributes["brightness"] as? Number)?.toFloat()?.div(255f)?.times(100)
                            ?: 0f
                    if (currentValue < minValue)
                        currentValue = minValue
                    if (currentValue > maxValue)
                        currentValue = maxValue

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            Image(
                                asset = CommunityMaterial.Icon.cmd_brightness_5,
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                            Text(
                                text = stringResource(R.string.brightness),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    item {
                        InlineSlider(
                            value = currentValue,
                            onValueChange = { brightness ->
                                onBrightnessChanged(brightness.div(100).times(255))
                            },
                            steps = 20,
                            valueRange = minValue..maxValue,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // Color temp
                val supportsColorTemp = supportedColorModes?.contains(SUPPORT_COLOR_TEMP) ?: false
                val supportsColorTempComplete =
                    supportsColorTemp || ((attributes["supported_features"] as Int) and SUPPORT_COLOR_TEMP_DEPR == SUPPORT_COLOR_TEMP_DEPR)
                if (supportsColorTempComplete && attributes["color_mode"] == SUPPORT_COLOR_TEMP) {
                    val minValue = (attributes["min_mireds"] as? Number)?.toFloat() ?: 0f
                    val maxValue = (attributes["max_mireds"] as? Number)?.toFloat() ?: 0f
                    var currentValue = (attributes["color_temp"] as? Number)?.toFloat() ?: 0f
                    if (currentValue < minValue)
                        currentValue = minValue
                    if (currentValue > maxValue)
                        currentValue = maxValue

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            Image(
                                asset = CommunityMaterial.Icon3.cmd_thermometer,
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                            Text(
                                text = stringResource(R.string.color_temp),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    item {
                        InlineSlider(
                            value = currentValue,
                            onValueChange = onColorTempChanged,
                            steps = 20,
                            valueRange = minValue..maxValue,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }

            item {
                ListHeader(R.string.details)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.entity_id) + ": ${entity.entityId}")
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.state) + ": ${entity.state}")
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    val lastChanged = DateFormat.getDateTimeInstance().format(entity.lastChanged.time)
                    Text(stringResource(R.string.last_changed) + ": $lastChanged")
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    val lastUpdated = DateFormat.getDateTimeInstance().format(entity.lastUpdated.time)
                    Text(stringResource(R.string.last_updated) + ": $lastUpdated")
                }
            }
        }
    }
}
