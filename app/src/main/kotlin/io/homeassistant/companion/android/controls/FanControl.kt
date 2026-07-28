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
object FanControl : HaControl {
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        item: EntityDisplayWithContext,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        val speed = item.fanControls?.speed
        if (speed != null) {
            control.setControlTemplate(
                ToggleRangeTemplate(
                    item.entityId,
                    item.isActive,
                    "",
                    RangeTemplate(
                        item.entityId,
                        speed.min,
                        speed.max,
                        speed.value,
                        1f,
                        "%.0f%%",
                    ),
                ),
            )
        } else {
            control.setControlTemplate(
                ToggleTemplate(
                    item.entityId,
                    ControlButton(
                        item.isActive,
                        "",
                    ),
                ),
            )
        }
        return control
    }

    override fun getDeviceType(item: EntityDisplayWithContext): Int = DeviceTypes.TYPE_FAN

    override fun getDomainString(context: Context, item: EntityDisplayWithContext): String =
        context.getString(commonR.string.domain_fan)

    override suspend fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean {
        when (action) {
            is BooleanAction -> {
                integrationRepository.callAction(
                    action.templateId.split(".")[0],
                    if (action.newState) "turn_on" else "turn_off",
                    hashMapOf("entity_id" to action.templateId),
                )
            }
            is FloatAction -> {
                val convertPercentage = action.newValue
                integrationRepository.callAction(
                    action.templateId.split(".")[0],
                    "set_percentage",
                    hashMapOf(
                        "entity_id" to action.templateId,
                        "percentage" to convertPercentage.toInt(),
                    ),
                )
            }
        }
        return true
    }
}
