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
import io.homeassistant.companion.android.common.data.integration.HvacMode
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.widget.ClimateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme
import io.homeassistant.companion.android.util.compose.glanceHaLightColors

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
    val climateTemp: Float? = null,
    val hvacSelectedMode: HvacMode? = null,
    val hvacSupportedModes: List<HvacMode> = emptyList(),
    val lastUpdate: String? = null,
    val outOfSync: Boolean,
    val showComplete: Boolean,
) : ClimateState {

    fun hasDisplayableItems(): Boolean {
        return showComplete  && climateTemp != null

    }

    companion object {
        /**
         * Create a complete [ClimateStateWithData] from the DB and from the server. Set the flag [outOfSync] to false, since the data
         * includes an updated state from the server.
         */
        fun from(
            climateEntity: ClimateWidgetEntity,
            entity: EntityDisplay,
        ): ClimateStateWithData {
            val climateControls = entity.climateControls
            val hvacSupportedModes = climateControls?.hvacSupportedModes?.mapNotNull { HvacMode.from(it) }

            return ClimateStateWithData(
                backgroundType = climateEntity.backgroundType,
                textColor = climateEntity.textColor,
                serverId = climateEntity.serverId,
                listEntityId = entity.entityId,
                climateName = entity.name,
                currentTemp = climateControls?.currentTemperature,
                climateTemp = climateControls?.targetTemperature,
                hvacSelectedMode = HvacMode.from(entity.rawState),
                hvacSupportedModes = hvacSupportedModes ?: emptyList(),
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
                hvacSelectedMode = null,
                outOfSync = true,
                showComplete = climateEntity.showCompleted,
            )
        }
    }
}

fun Map<String, Any?>.toHvacModes(key: String): List<HvacMode> =
    ((this[key] as? List<*>) ?: emptyList<Any>())
        .mapNotNull { HvacMode.from(it as? String) }

fun Map<String, Any?>.toDouble(key: String): Double? =
    (this[key] as? Number)?.toDouble()
