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
class VacuumControl {
    companion object : HaControl {
        private const val SUPPORT_TURN_ON = 1
        private var entitySupportedFeatures = 0

        override fun provideControlFeatures(
            context: Context,
            control: Control.StatefulBuilder,
            entity: Entity<Map<String, Any>>,
            area: AreaRegistryResponse?
        ): Control.StatefulBuilder {
            entitySupportedFeatures = entity.attributes["supported_features"] as Int
            if (entitySupportedFeatures and SUPPORT_TURN_ON != SUPPORT_TURN_ON) {
                control.setStatusText(
                    when (entity.state) {
                        "cleaning" -> context.getString(commonR.string.state_cleaning)
                        "docked" -> context.getString(commonR.string.state_docked)
                        "error" -> context.getString(commonR.string.state_error)
                        "idle" -> context.getString(commonR.string.state_idle)
                        "paused" -> context.getString(commonR.string.state_paused)
                        "returning" -> context.getString(commonR.string.state_returning)
                        "unavailable" -> context.getString(commonR.string.state_unavailable)
                        else -> context.getString(commonR.string.state_unknown)
                    }
                )
            }
            control.setControlTemplate(
                ToggleTemplate(
                    entity.entityId,
                    ControlButton(
                        if (entitySupportedFeatures and SUPPORT_TURN_ON == SUPPORT_TURN_ON)
                            entity.state == "on"
                        else
                            entity.state == "cleaning",
                        "Description"
                    )
                )
            )
            return control
        }

        override fun getDeviceType(entity: Entity<Map<String, Any>>): Int =
            DeviceTypes.TYPE_VACUUM

        override fun getDomainString(context: Context, entity: Entity<Map<String, Any>>): String =
            context.getString(commonR.string.domain_vacuum)

        override fun performAction(
            integrationRepository: IntegrationRepository,
            action: ControlAction
        ): Boolean {
            return runBlocking {
                integrationRepository.callService(
                    action.templateId.split(".")[0],
                    if (entitySupportedFeatures and SUPPORT_TURN_ON == SUPPORT_TURN_ON)
                        if ((action as? BooleanAction)?.newState == true) "turn_on" else "turn_off"
                    else if ((action as? BooleanAction)?.newState == true) "start" else "return_to_base",
                    hashMapOf(
                        "entity_id" to action.templateId
                    )
                )
                true
            }
        }
    }
}
