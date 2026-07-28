package io.homeassistant.companion.android.controls

import android.content.Context
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.ControlAction
import android.service.controls.templates.StatelessTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.util.capitalize
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.R)
object DefaultButtonControl : HaControl {
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        item: EntityDisplayWithContext,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        control.setStatusText("")
        control.setControlTemplate(
            StatelessTemplate(
                item.entityId,
            ),
        )
        return control
    }

    override fun getDeviceType(item: EntityDisplayWithContext): Int = when (item.domain) {
        "scene", "script" -> DeviceTypes.TYPE_ROUTINE
        else -> DeviceTypes.TYPE_UNKNOWN
    }

    override fun getDomainString(context: Context, item: EntityDisplayWithContext): String = when (item.domain) {
        "button" -> context.getString(commonR.string.domain_button)
        "input_button" -> context.getString(commonR.string.domain_input_button)
        "scene" -> context.getString(commonR.string.domain_scene)
        "script" -> context.getString(commonR.string.domain_script)
        else -> item.domain.capitalize(Locale.getDefault())
    }

    override suspend fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean {
        integrationRepository.callAction(
            action.templateId.split(".")[0],
            when (action.templateId.split(".")[0]) {
                "button", "input_button" -> "press"
                else -> "turn_on"
            },
            hashMapOf("entity_id" to action.templateId),
        )
        return true
    }
}
