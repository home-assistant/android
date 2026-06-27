package io.homeassistant.companion.android.widgets.climate

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.material.ColorProviders
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.widget.ClimateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme
import io.homeassistant.companion.android.util.compose.glanceHaLightColors
import timber.log.Timber

internal sealed interface ClimateState {
    val backgroundType: WidgetBackgroundType
        get() = if (SdkVersion.isAtLeast(Build.VERSION_CODES.S)) {
            WidgetBackgroundType.DYNAMICCOLOR
        } else {
            WidgetBackgroundType.DAYNIGHT
        }
    val textColor: String?
        get() = null

    companion object {
        @Composable
        fun ClimateState.getColors(): ColorProviders {
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

internal object LoadingClimateState : ClimateState
internal object EmptyClimateState : ClimateState

internal data class ClimateStateWithData(
    override val backgroundType: WidgetBackgroundType,
    override val textColor: String?,
    val serverId: Int,
    val listEntityId: String,
    val climateName: String,
    val currentTemp: Float? = null,
    val climateTemp: Float,
    val hvacMode: String,
    val lastUpdate: String? = null,
    val outOfSync: Boolean,
    val showComplete: Boolean,
) : ClimateState {

    fun isPowerOn(): Boolean {
        return showComplete && hvacMode != "off"
    }

    companion object {
        /**
         * Create a complete [ClimateStateWithData] from the DB and from the server. Set the flag [outOfSync] to false, since the data
         * includes an updated state from the server.
         */
        fun from(
            climateEntity: ClimateWidgetEntity,
            entity: Entity,
        ): ClimateStateWithData {

            val attributes = entity.attributes
            val min = attributes["min_temp"] as? Double
            val max = attributes["max_temp"] as? Double
            val currentTemp = attributes["current_temperature"] as? Double
            val climateTemp = attributes["temperature"] as Double
            val hvacMode = attributes["hvac_modes"]

            Timber.d("entityUpdate: min: $min, max: $max, currentTemp: $currentTemp, climateTemp: $climateTemp, hvacMode: $hvacMode")

            return ClimateStateWithData(
                backgroundType = climateEntity.backgroundType,
                textColor = climateEntity.textColor,
                serverId = climateEntity.serverId,
                listEntityId = entity.entityId,
                climateName = entity.friendlyName,
                currentTemp = currentTemp?.toFloat(),
                climateTemp = climateTemp.toFloat(),
                hvacMode = entity.state,
                outOfSync = false,
                showComplete = climateEntity.showCompleted,
            )
        }

        /**
         * Create a [ClimateStateWithData] with data only from the DB. Set the flag [outOfSync] to true, since the data
         * doesn't have an updated state from the server.
         */
        fun from(climateEntity: ClimateWidgetEntity): ClimateStateWithData {
            return ClimateStateWithData(
                backgroundType = climateEntity.backgroundType,
                textColor = climateEntity.textColor,
                serverId = climateEntity.serverId,
                listEntityId = climateEntity.entityId,
                climateName = "",
                climateTemp = 10f,
                hvacMode = "unknown",
                outOfSync = true,
                showComplete = climateEntity.showCompleted,
            )
        }
    }
}
