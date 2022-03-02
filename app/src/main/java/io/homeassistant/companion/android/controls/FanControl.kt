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
import io.homeassistant.companion.android.common.data.integration.supportsFanSetSpeed
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import kotlinx.coroutines.runBlocking
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.R)
class FanControl {
    companion object : HaControl {
        override fun provideControlFeatures(
            context: Context,
            control: Control.StatefulBuilder,
            entity: Entity<Map<String, Any>>,
            area: AreaRegistryResponse?
        ): Control.StatefulBuilder {
            if (entity.supportsFanSetSpeed()) {
                val minValue = 0f
                val maxValue = 100f
                var currentValue =
                    (entity.attributes["percentage"] as? Number)?.toFloat() ?: 0f
                if (currentValue < minValue)
                    currentValue = minValue
                if (currentValue > maxValue)
                    currentValue = maxValue
                control.setControlTemplate(
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
                )
            } else {
                control.setControlTemplate(
                    ToggleTemplate(
                        entity.entityId,
                        ControlButton(
                            entity.state == "on",
                            ""
                        )
                    )
                )
            }
            return control
        }

        override fun getDeviceType(entity: Entity<Map<String, Any>>): Int =
            DeviceTypes.TYPE_FAN

        override fun getDomainString(context: Context, entity: Entity<Map<String, Any>>): String =
            context.getString(commonR.string.domain_fan)

        override fun performAction(
            integrationRepository: IntegrationRepository,
            action: ControlAction
        ): Boolean {
            return runBlocking {
                when (action) {
                    is BooleanAction -> {
                        integrationRepository.callService(
                            action.templateId.split(".")[0],
                            if (action.newState) "turn_on" else "turn_off",
                            hashMapOf("entity_id" to action.templateId)
                        )
                    }
                    is FloatAction -> {
                        val convertPercentage = action.newValue
                        integrationRepository.callService(
                            action.templateId.split(".")[0],
                            "set_percentage",
                            hashMapOf(
                                "entity_id" to action.templateId,
                                "percentage" to convertPercentage.toInt()
                            )
                        )
                    }
                }
                return@runBlocking true
            }
        }
    }
}
