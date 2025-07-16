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
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.isActive
import io.homeassistant.companion.android.common.util.capitalize
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.R)
object DefaultSwitchControl : HaControl {
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        entity: Entity,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
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

    override fun getDeviceType(entity: Entity): Int = when (entity.domain) {
        "humidifier" -> DeviceTypes.TYPE_HUMIDIFIER
        "remote" -> DeviceTypes.TYPE_REMOTE_CONTROL
        "siren" -> DeviceTypes.TYPE_SECURITY_SYSTEM
        "switch" -> DeviceTypes.TYPE_SWITCH
        else -> DeviceTypes.TYPE_GENERIC_ON_OFF
    }

    override fun getDomainString(context: Context, entity: Entity): String = when (entity.domain) {
        "automation" -> context.getString(commonR.string.domain_automation)
        "humidifier" -> context.getString(commonR.string.domain_humidifier)
        "input_boolean" -> context.getString(commonR.string.domain_input_boolean)
        "remote" -> context.getString(commonR.string.domain_remote)
        "siren" -> context.getString(commonR.string.domain_siren)
        "switch" -> context.getString(commonR.string.domain_switch)
        else -> entity.domain.capitalize(Locale.getDefault())
    }

    override suspend fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean {
        integrationRepository.callAction(
            action.templateId.split(".")[0],
            if ((action as? BooleanAction)?.newState == true) "turn_on" else "turn_off",
            hashMapOf("entity_id" to action.templateId),
        )
        return true
    }
}
