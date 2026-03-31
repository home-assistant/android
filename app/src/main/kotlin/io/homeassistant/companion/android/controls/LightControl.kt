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
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.getLightBrightness
import io.homeassistant.companion.android.common.data.integration.isActive
import io.homeassistant.companion.android.common.data.integration.supportsLightBrightness

@RequiresApi(Build.VERSION_CODES.R)
object LightControl : HaControl {
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        entity: Entity,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        val position = entity.getLightBrightness()
        control.setControlTemplate(
            if (entity.supportsLightBrightness()) {
                ToggleRangeTemplate(
                    entity.entityId,
                    entity.isActive(),
                    "",
                    RangeTemplate(
                        entity.entityId,
                        position?.min ?: 0f,
                        position?.max ?: 100f,
                        position?.value ?: 0f,
                        1f,
                        "%.0f%%",
                    ),
                )
            } else {
                ToggleTemplate(
                    entity.entityId,
                    ControlButton(
                        entity.isActive(),
                        "Description",
                    ),
                )
            },
        )
        return control
    }

    override fun getDeviceType(entity: Entity): Int = DeviceTypes.TYPE_LIGHT

    override fun getDomainString(context: Context, entity: Entity): String =
        context.getString(commonR.string.domain_light)

    override suspend fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean {
        return when (action) {
            is BooleanAction -> {
                integrationRepository.callAction(
                    action.templateId.split(".")[0],
                    if (action.newState) "turn_on" else "turn_off",
                    hashMapOf(
                        "entity_id" to action.templateId,
                    ),
                )
                true
            }
            is FloatAction -> {
                val convertBrightness = action.newValue.div(100).times(255)
                integrationRepository.callAction(
                    action.templateId.split(".")[0],
                    "turn_on",
                    hashMapOf(
                        "entity_id" to action.templateId,
                        "brightness" to convertBrightness.toInt(),
                    ),
                )
                true
            }
            else -> {
                false
            }
        }
    }
}
