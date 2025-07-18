package io.homeassistant.companion.android.controls

import android.content.Context
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.actions.ModeAction
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.TemperatureControlTemplate
import android.service.controls.templates.ToggleRangeTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository

@RequiresApi(Build.VERSION_CODES.R)
object ClimateControl : HaControl {
    private data class ClimateState(val currentMode: String, val supportedModes: ArrayList<String>)

    private const val SUPPORT_TARGET_TEMPERATURE = 1
    private const val SUPPORT_TARGET_TEMPERATURE_RANGE = 2
    private val temperatureControlModes = mapOf(
        "cool" to TemperatureControlTemplate.MODE_COOL,
        "heat" to TemperatureControlTemplate.MODE_HEAT,
        "heat_cool" to TemperatureControlTemplate.MODE_HEAT_COOL,
        "off" to TemperatureControlTemplate.MODE_OFF,
    )
    private val temperatureControlModeFlags = mapOf(
        "cool" to TemperatureControlTemplate.FLAG_MODE_COOL,
        "heat" to TemperatureControlTemplate.FLAG_MODE_HEAT,
        "heat_cool" to TemperatureControlTemplate.FLAG_MODE_HEAT_COOL,
        "off" to TemperatureControlTemplate.FLAG_MODE_OFF,
    )
    private val climateStates = HashMap<String, ClimateState>()

    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        entity: Entity,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        val minValue = (entity.attributes["min_temp"] as? Number)?.toFloat() ?: 0f
        val maxValue = (entity.attributes["max_temp"] as? Number)?.toFloat() ?: 100f
        var currentValue = (entity.attributes["temperature"] as? Number)?.toFloat() ?: (
            entity.attributes["current_temperature"] as? Number
            )?.toFloat() ?: 0f
        // Ensure the current value is never lower than the minimum or higher than the maximum
        if (currentValue < minValue) {
            currentValue = minValue
        }
        if (currentValue > maxValue) {
            currentValue = maxValue
        }

        val temperatureUnit = entity.attributes["temperature_unit"] ?: ""
        val temperatureStepSize = (entity.attributes["target_temp_step"] as? Number)?.toFloat()
            ?: when (temperatureUnit) {
                "°C" -> 0.5f
                else -> 1f
            }
        val temperatureFormatSize = if (temperatureStepSize < 1f) "1" else "0"
        val rangeTemplate = RangeTemplate(
            info.systemId,
            minValue,
            maxValue,
            currentValue,
            temperatureStepSize,
            "%.${temperatureFormatSize}f $temperatureUnit",
        )
        if (entityShouldBePresentedAsThermostat(entity)) {
            val state = ClimateState(entity.state, ArrayList())
            val toggleRangeTemplate = ToggleRangeTemplate(
                info.systemId + "_range",
                // Set checked to true to always show the temperature indicator, regardless of climate mode
                true,
                context.getString(commonR.string.widget_tap_action_toggle),
                rangeTemplate,
            )
            var modesFlag = 0
            (entity.attributes["hvac_modes"] as? List<String>)?.forEach {
                modesFlag = modesFlag or temperatureControlModeFlags[it]!!
                state.supportedModes.add(it)
            }
            this.climateStates[info.systemId] = state
            control.setControlTemplate(
                TemperatureControlTemplate(
                    info.systemId,
                    toggleRangeTemplate,
                    temperatureControlModes[entity.state]!!,
                    temperatureControlModes[entity.state]!!,
                    modesFlag,
                ),
            )
        } else {
            control.setControlTemplate(rangeTemplate)
        }

        return control
    }

    override fun getDeviceType(entity: Entity): Int = if (entityShouldBePresentedAsThermostat(entity)) {
        DeviceTypes.TYPE_THERMOSTAT
    } else {
        DeviceTypes.TYPE_AC_HEATER
    }

    override fun getDomainString(context: Context, entity: Entity): String =
        context.getString(commonR.string.domain_climate)

    override suspend fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean {
        val entityStr: String = if (action.templateId.split(".").size > 2) {
            action.templateId.split(".", limit = 2)[1]
        } else {
            action.templateId
        }
        return when (action) {
            is FloatAction -> {
                integrationRepository.callAction(
                    entityStr.split(".")[0],
                    "set_temperature",
                    hashMapOf(
                        "entity_id" to entityStr,
                        "temperature" to (action as? FloatAction)?.newValue.toString(),
                    ),
                )
                true
            }
            is ModeAction -> {
                integrationRepository.callAction(
                    action.templateId.split(".")[0],
                    "set_hvac_mode",
                    hashMapOf(
                        "entity_id" to entityStr,
                        "hvac_mode" to (
                            temperatureControlModes.entries.find {
                                it.value == ((action as? ModeAction)?.newMode ?: -1)
                            }?.key ?: ""
                            ),
                    ),
                )
                true
            }
            is BooleanAction -> {
                if (this.climateStates[action.templateId] == null) {
                    return false
                }
                val supportedModes = this.climateStates[action.templateId]!!.supportedModes
                val currentMode = this.climateStates[action.templateId]!!.currentMode
                val nextMode = (supportedModes.indexOf(currentMode) + 1) % supportedModes.count()
                integrationRepository.callAction(
                    entityStr.split(".")[0],
                    "set_hvac_mode",
                    hashMapOf(
                        "entity_id" to entityStr,
                        "hvac_mode" to supportedModes[nextMode],
                    ),
                )
                true
            }
            else -> {
                false
            }
        }
    }

    private fun entityShouldBePresentedAsThermostat(entity: Entity): Boolean =
        (entity.attributes["hvac_modes"] as? List<String>).let { modes ->
            temperatureControlModes.containsKey(entity.state) &&
                modes?.isNotEmpty() == true &&
                modes.any { it == entity.state } &&
                modes.all { temperatureControlModes.containsKey(it) } &&
                (
                    (
                        (entity.attributes["supported_features"] as Int) and SUPPORT_TARGET_TEMPERATURE ==
                            SUPPORT_TARGET_TEMPERATURE
                        ) ||
                        (
                            (entity.attributes["supported_features"] as Int) and SUPPORT_TARGET_TEMPERATURE_RANGE ==
                                SUPPORT_TARGET_TEMPERATURE_RANGE
                            )
                    )
        }
}
