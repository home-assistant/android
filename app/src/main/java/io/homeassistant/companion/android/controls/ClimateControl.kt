package io.homeassistant.companion.android.controls

import android.content.Context
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.actions.ModeAction
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.TemperatureControlTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import kotlinx.coroutines.runBlocking
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.R)
class ClimateControl {
    companion object : HaControl {
        private const val SUPPORT_TARGET_TEMPERATURE = 1
        private const val SUPPORT_TARGET_TEMPERATURE_RANGE = 2
        private val temperatureControlModes = mapOf(
            "cool" to TemperatureControlTemplate.MODE_COOL,
            "heat" to TemperatureControlTemplate.MODE_HEAT,
            "heat_cool" to TemperatureControlTemplate.MODE_HEAT_COOL,
            "off" to TemperatureControlTemplate.MODE_OFF
        )
        private val temperatureControlModeFlags = mapOf(
            "cool" to TemperatureControlTemplate.FLAG_MODE_COOL,
            "heat" to TemperatureControlTemplate.FLAG_MODE_HEAT,
            "heat_cool" to TemperatureControlTemplate.FLAG_MODE_HEAT_COOL,
            "off" to TemperatureControlTemplate.FLAG_MODE_OFF
        )

        override fun provideControlFeatures(
            context: Context,
            control: Control.StatefulBuilder,
            entity: Entity<Map<String, Any>>,
            area: AreaRegistryResponse?
        ): Control.StatefulBuilder {
            control.setStatusText(
                when (entity.state) {
                    "auto" -> context.getString(commonR.string.state_auto)
                    "cool" -> context.getString(commonR.string.state_cool)
                    "dry" -> context.getString(commonR.string.state_dry)
                    "fan_only" -> context.getString(commonR.string.state_fan_only)
                    "heat" -> context.getString(commonR.string.state_heat)
                    "heat_cool" -> context.getString(commonR.string.state_heat_cool)
                    "off" -> context.getString(commonR.string.state_off)
                    "unavailable" -> context.getString(commonR.string.state_unavailable)
                    else -> entity.state
                }
            )
            val minValue = (entity.attributes["min_temp"] as? Number)?.toFloat() ?: 0f
            val maxValue = (entity.attributes["max_temp"] as? Number)?.toFloat() ?: 100f
            var currentValue = (entity.attributes["temperature"] as? Number)?.toFloat() ?: (
                entity.attributes["current_temperature"] as? Number
                )?.toFloat() ?: 0f
            // Ensure the current value is never lower than the minimum or higher than the maximum
            if (currentValue < minValue)
                currentValue = minValue
            if (currentValue > maxValue)
                currentValue = maxValue

            val temperatureUnit = entity.attributes["temperature_unit"] ?: ""
            val temperatureStepSize = (entity.attributes["target_temperature_step"] as? Number)?.toFloat()
                ?: when (temperatureUnit) {
                    "°C" -> 0.5f
                    else -> 1f
                }
            val temperatureFormatSize = if (temperatureStepSize < 1f) "1" else "0"
            val rangeTemplate = RangeTemplate(
                entity.entityId,
                minValue,
                maxValue,
                currentValue,
                temperatureStepSize,
                "%.${temperatureFormatSize}f $temperatureUnit"
            )
            if (entityShouldBePresentedAsThermostat(entity)) {
                var modesFlag = 0
                (entity.attributes["hvac_modes"] as? List<String>)?.forEach {
                    modesFlag = modesFlag or temperatureControlModeFlags[it]!!
                }
                control.setControlTemplate(
                    TemperatureControlTemplate(
                        entity.entityId,
                        rangeTemplate,
                        temperatureControlModes[entity.state]!!,
                        temperatureControlModes[entity.state]!!,
                        modesFlag
                    )
                )
            } else {
                control.setControlTemplate(rangeTemplate)
            }

            return control
        }

        override fun getDeviceType(entity: Entity<Map<String, Any>>): Int =
            if (entityShouldBePresentedAsThermostat(entity))
                DeviceTypes.TYPE_THERMOSTAT
            else
                DeviceTypes.TYPE_AC_HEATER

        override fun getDomainString(context: Context, entity: Entity<Map<String, Any>>): String =
            context.getString(commonR.string.domain_climate)

        override fun performAction(
            integrationRepository: IntegrationRepository,
            action: ControlAction
        ): Boolean {
            return runBlocking {
                return@runBlocking when (action) {
                    is FloatAction -> {
                        integrationRepository.callService(
                            action.templateId.split(".")[0],
                            "set_temperature",
                            hashMapOf(
                                "entity_id" to action.templateId,
                                "temperature" to (action as? FloatAction)?.newValue.toString()
                            )
                        )
                        true
                    }
                    is ModeAction -> {
                        integrationRepository.callService(
                            action.templateId.split(".")[0],
                            "set_hvac_mode",
                            hashMapOf(
                                "entity_id" to action.templateId,
                                "hvac_mode" to (
                                    temperatureControlModes.entries.find {
                                        it.value == ((action as? ModeAction)?.newMode ?: -1)
                                    }?.key ?: ""
                                    )
                            )
                        )
                        true
                    }
                    else -> {
                        false
                    }
                }
            }
        }

        private fun entityShouldBePresentedAsThermostat(entity: Entity<Map<String, Any>>): Boolean {
            return temperatureControlModes.containsKey(entity.state) &&
                ((entity.attributes["hvac_modes"] as? List<String>)?.isNotEmpty() == true) &&
                ((entity.attributes["hvac_modes"] as? List<String>)?.all { temperatureControlModes.containsKey(it) } == true) &&
                (
                    ((entity.attributes["supported_features"] as Int) and SUPPORT_TARGET_TEMPERATURE == SUPPORT_TARGET_TEMPERATURE) ||
                        ((entity.attributes["supported_features"] as Int) and SUPPORT_TARGET_TEMPERATURE_RANGE == SUPPORT_TARGET_TEMPERATURE_RANGE)
                    )
        }
    }
}
