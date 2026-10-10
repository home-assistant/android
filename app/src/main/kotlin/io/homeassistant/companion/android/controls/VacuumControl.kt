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
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import java.util.concurrent.ConcurrentHashMap

@RequiresApi(Build.VERSION_CODES.R)
object VacuumControl : HaControl {
    private data class EntityKey(val serverId: Int, val entityId: String)

    private val entitySupportsTurnOn = ConcurrentHashMap<EntityKey, Boolean>()

    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        item: EntityDisplayWithContext,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        entitySupportsTurnOn[EntityKey(info.serverId, item.entityId)] = item.vacuumControls?.supportsTurnOn == true
        control.setControlTemplate(
            ToggleTemplate(
                item.entityId,
                ControlButton(
                    item.isActive,
                    "Description",
                ),
            ),
        )
        return control
    }

    override fun getDeviceType(item: EntityDisplayWithContext): Int = DeviceTypes.TYPE_VACUUM

    override fun getDomainString(context: Context, item: EntityDisplayWithContext): String =
        context.getString(commonR.string.domain_vacuum)

    override suspend fun performAction(
        integrationRepository: IntegrationRepository,
        action: ControlAction,
        serverId: Int,
    ): Boolean {
        integrationRepository.callAction(
            action.templateId.split(".")[0],
            if (entitySupportsTurnOn[EntityKey(serverId, action.templateId)] == true) {
                if ((action as? BooleanAction)?.newState == true) "turn_on" else "turn_off"
            } else if ((action as? BooleanAction)?.newState == true) {
                "start"
            } else {
                "return_to_base"
            },
            hashMapOf(
                "entity_id" to action.templateId,
            ),
        )
        return true
    }
}
