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
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext

@RequiresApi(Build.VERSION_CODES.R)
object LightControl : HaControl {
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        item: EntityDisplayWithContext,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        val brightness = item.lightControls?.brightness
        control.setControlTemplate(
            if (brightness != null) {
                ToggleRangeTemplate(
                    item.entityId,
                    item.isActive,
                    "",
                    RangeTemplate(
                        item.entityId,
                        brightness.min,
                        brightness.max,
                        brightness.value,
                        1f,
                        "%.0f%%",
                    ),
                )
            } else {
                ToggleTemplate(
                    item.entityId,
                    ControlButton(
                        item.isActive,
                        "Description",
                    ),
                )
            },
        )
        return control
    }

    override fun getDeviceType(item: EntityDisplayWithContext): Int = DeviceTypes.TYPE_LIGHT

    override fun getDomainString(context: Context, item: EntityDisplayWithContext): String =
        context.getString(commonR.string.domain_light)

    override suspend fun performAction(
        integrationRepository: IntegrationRepository,
        action: ControlAction,
        serverId: Int,
    ): Boolean {
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
