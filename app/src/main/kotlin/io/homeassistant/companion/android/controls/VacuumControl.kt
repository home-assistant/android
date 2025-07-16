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
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.isActive

@RequiresApi(Build.VERSION_CODES.R)
object VacuumControl : HaControl {
    private const val SUPPORT_TURN_ON = 1
    private var entitySupportedFeatures = 0

    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        entity: Entity,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        entitySupportedFeatures = entity.attributes["supported_features"] as Int
        control.setControlTemplate(
            ToggleTemplate(
                entity.entityId,
                ControlButton(
                    entity.isActive(),
                    "Description",
                ),
            ),
        )
        return control
    }

    override fun getDeviceType(entity: Entity): Int = DeviceTypes.TYPE_VACUUM

    override fun getDomainString(context: Context, entity: Entity): String =
        context.getString(commonR.string.domain_vacuum)

    override suspend fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean {
        integrationRepository.callAction(
            action.templateId.split(".")[0],
            if (entitySupportedFeatures and SUPPORT_TURN_ON == SUPPORT_TURN_ON) {
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
