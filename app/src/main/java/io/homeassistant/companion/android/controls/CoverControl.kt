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
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import kotlinx.coroutines.runBlocking
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.R)
class CoverControl {
    companion object : HaControl {
        const val SUPPORT_SET_POSITION = 4
        override fun provideControlFeatures(
            context: Context,
            control: Control.StatefulBuilder,
            entity: Entity<Map<String, Any>>,
            area: AreaRegistryResponse?
        ): Control.StatefulBuilder {
            control.setStatusText(
                when (entity.state) {
                    "closed" -> context.getString(commonR.string.state_closed)
                    "closing" -> context.getString(commonR.string.state_closing)
                    "open" -> context.getString(commonR.string.state_open)
                    "opening" -> context.getString(commonR.string.state_opening)
                    "unavailable" -> context.getString(commonR.string.state_unavailable)
                    else -> entity.state
                }
            )
            val minValue = 0f
            val maxValue = 100f
            var currentValue =
                (entity.attributes["current_position"] as? Number)?.toFloat() ?: 0f
            if (currentValue < minValue)
                currentValue = minValue
            if (currentValue > maxValue)
                currentValue = maxValue
            control.setControlTemplate(
                if ((entity.attributes["supported_features"] as Int) and SUPPORT_SET_POSITION == SUPPORT_SET_POSITION)
                    ToggleRangeTemplate(
                        entity.entityId,
                        entity.state in listOf("open", "opening"),
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
                            entity.state in listOf("open", "opening"),
                            "Description"
                        )
                    )
            )
            return control
        }

        override fun getDeviceType(entity: Entity<Map<String, Any>>): Int =
            when (entity.attributes["device_class"]) {
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

        override fun getDomainString(context: Context, entity: Entity<Map<String, Any>>): String =
            context.getString(commonR.string.domain_cover)

        override fun performAction(
            integrationRepository: IntegrationRepository,
            action: ControlAction
        ): Boolean {
            return runBlocking {
                return@runBlocking when (action) {
                    is BooleanAction -> {
                        integrationRepository.callService(
                            action.templateId.split(".")[0],
                            if ((action as? BooleanAction)?.newState == true) "open_cover" else "close_cover",
                            hashMapOf(
                                "entity_id" to action.templateId
                            )
                        )
                        true
                    }
                    is FloatAction -> {
                        val convertPosition = action.newValue
                        integrationRepository.callService(
                            action.templateId.split(".")[0],
                            "set_cover_position",
                            hashMapOf(
                                "entity_id" to action.templateId,
                                "position" to convertPosition.toInt()
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
