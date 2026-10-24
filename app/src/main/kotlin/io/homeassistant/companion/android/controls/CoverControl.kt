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
object CoverControl : HaControl {
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        item: EntityDisplayWithContext,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        val position = item.coverControls?.position
        control.setControlTemplate(
            if (item.coverControls?.supportsSetPosition == true) {
                ToggleRangeTemplate(
                    item.entityId,
                    item.isActive,
                    "",
                    RangeTemplate(
                        item.entityId,
                        position?.min ?: 0f,
                        position?.max ?: 100f,
                        position?.value ?: 0f,
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

    override fun getDeviceType(item: EntityDisplayWithContext): Int = when (item.deviceClass) {
        "awning" -> DeviceTypes.TYPE_AWNING
        "blind" -> DeviceTypes.TYPE_BLINDS
        "curtain" -> DeviceTypes.TYPE_CURTAIN
        "door" -> DeviceTypes.TYPE_DOOR
        "garage" -> DeviceTypes.TYPE_GARAGE
        "gate" -> DeviceTypes.TYPE_GATE
        "shutter" -> DeviceTypes.TYPE_SHUTTER
        "window" -> DeviceTypes.TYPE_WINDOW
        else -> DeviceTypes.TYPE_GENERIC_OPEN_CLOSE
    }

    override fun getDomainString(context: Context, item: EntityDisplayWithContext): String =
        context.getString(commonR.string.domain_cover)

    override suspend fun performAction(
        integrationRepository: IntegrationRepository,
        action: ControlAction,
        serverId: Int,
    ): Boolean {
        return when (action) {
            is BooleanAction -> {
                integrationRepository.callAction(
                    action.templateId.split(".")[0],
                    if ((action as? BooleanAction)?.newState == true) "open_cover" else "close_cover",
                    hashMapOf(
                        "entity_id" to action.templateId,
                    ),
                )
                true
            }
            is FloatAction -> {
                val convertPosition = action.newValue
                integrationRepository.callAction(
                    action.templateId.split(".")[0],
                    "set_cover_position",
                    hashMapOf(
                        "entity_id" to action.templateId,
                        "position" to convertPosition.toInt(),
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
