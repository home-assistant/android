package io.homeassistant.companion.android.controls

import android.content.Context
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.ToggleRangeTemplate
import android.service.controls.templates.ToggleTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.supportsLightBrightness
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import kotlinx.coroutines.runBlocking
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.R)
class LightControl {
    companion object : HaControl {
        override fun provideControlFeatures(
            context: Context,
            control: Control.StatefulBuilder,
            entity: Entity<Map<String, Any>>,
            area: AreaRegistryResponse?
        ): Control.StatefulBuilder {
            val minValue = 0f
            val maxValue = 100f
            var currentValue = (entity.attributes["brightness"] as? Number)?.toFloat()?.div(255f)?.times(100) ?: 0f
            if (currentValue < minValue)
                currentValue = minValue
            if (currentValue > maxValue)
                currentValue = maxValue
            control.setControlTemplate(
                if (entity.supportsLightBrightness())
                    ToggleRangeTemplate(
                        entity.entityId,
                        entity.state == "on",
                        "",
                        RangeTemplate(
                            entity.entityId,
                            minValue,
                            maxValue,
                            currentValue,
                            1f,
                            "%.0f%%"
                        )
                    )
                else
                    ToggleTemplate(
                        entity.entityId,
                        ControlButton(
                            entity.state == "on",
                            "Description"
                        )
                    )
            )
            return control
        }

        override fun getDeviceType(entity: Entity<Map<String, Any>>): Int =
            DeviceTypes.TYPE_LIGHT

        override fun getDomainString(context: Context, entity: Entity<Map<String, Any>>): String =
            context.getString(commonR.string.domain_light)

        override fun performAction(
            integrationRepository: IntegrationRepository,
            action: ControlAction
        ): Boolean {
            return runBlocking {
                return@runBlocking when (action) {
                    is BooleanAction -> {
                        integrationRepository.callService(
                            action.templateId.split(".")[0],
                            if (action.newState) "turn_on" else "turn_off",
                            hashMapOf(
                                "entity_id" to action.templateId
                            )
                        )
                        true
                    }
                    is FloatAction -> {
                        val convertBrightness = action.newValue.div(100).times(255)
                        integrationRepository.callService(
                            action.templateId.split(".")[0],
                            "turn_on",
                            hashMapOf(
                                "entity_id" to action.templateId,
                                "brightness" to convertBrightness.toInt()
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
    }
}
