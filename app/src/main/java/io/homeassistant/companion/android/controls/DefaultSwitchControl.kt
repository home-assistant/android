package io.homeassistant.companion.android.controls

import android.content.Context
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.ToggleTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import kotlinx.coroutines.runBlocking
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.R)
class DefaultSwitchControl {
    companion object : HaControl {
        override fun provideControlFeatures(
            context: Context,
            control: Control.StatefulBuilder,
            entity: Entity<Map<String, Any>>,
            area: AreaRegistryResponse?
        ): Control.StatefulBuilder {
            control.setControlTemplate(
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
            when (entity.entityId.split(".")[0]) {
                "switch" -> DeviceTypes.TYPE_SWITCH
                else -> DeviceTypes.TYPE_GENERIC_ON_OFF
            }

        override fun getDomainString(context: Context, entity: Entity<Map<String, Any>>): String =
            when (entity.entityId.split(".")[0]) {
                "automation" -> context.getString(commonR.string.domain_automation)
                "input_boolean" -> context.getString(commonR.string.domain_input_boolean)
                "switch" -> context.getString(commonR.string.domain_switch)
                else -> entity.entityId.split(".")[0].capitalize()
            }

        override fun performAction(
            integrationRepository: IntegrationRepository,
            action: ControlAction
        ): Boolean {
            return runBlocking {
                integrationRepository.callService(
                    action.templateId.split(".")[0],
                    if ((action as? BooleanAction)?.newState == true) "turn_on" else "turn_off",
                    hashMapOf("entity_id" to action.templateId)
                )
                return@runBlocking true
            }
        }
    }
}
