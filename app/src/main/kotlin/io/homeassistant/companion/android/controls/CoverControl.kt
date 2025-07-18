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
import io.homeassistant.companion.android.common.data.integration.getCoverPosition
import io.homeassistant.companion.android.common.data.integration.isActive

@RequiresApi(Build.VERSION_CODES.R)
object CoverControl : HaControl {
    private const val SUPPORT_SET_POSITION = 4
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        entity: Entity,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        val position = entity.getCoverPosition()
        control.setControlTemplate(
            if ((entity.attributes["supported_features"] as Int) and SUPPORT_SET_POSITION == SUPPORT_SET_POSITION) {
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

    override fun getDeviceType(entity: Entity): Int = when (entity.attributes["device_class"]) {
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

    override fun getDomainString(context: Context, entity: Entity): String =
        context.getString(commonR.string.domain_cover)

    override suspend fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean {
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
