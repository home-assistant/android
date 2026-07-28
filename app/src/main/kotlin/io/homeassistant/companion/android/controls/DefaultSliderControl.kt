package io.homeassistant.companion.android.controls

import android.content.Context
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.RangeTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext

@RequiresApi(Build.VERSION_CODES.R)
object DefaultSliderControl : HaControl {
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        item: EntityDisplayWithContext,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        control.setStatusText("")
        control.setControlTemplate(
            RangeTemplate(
                item.entityId,
                item.numberControls?.range?.min ?: 0f,
                item.numberControls?.range?.max ?: 1f,
                item.numberControls?.range?.value ?: 0f,
                item.numberControls?.step ?: 1f,
                null,
            ),
        )
        return control
    }

    override fun getDeviceType(item: EntityDisplayWithContext): Int = DeviceTypes.TYPE_UNKNOWN

    override fun getDomainString(context: Context, item: EntityDisplayWithContext): String =
        if (item.domain == "input_number") {
            context.getString(commonR.string.domain_input_number)
        } else {
            context.getString(commonR.string.domain_number)
        }

    override suspend fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean {
        integrationRepository.callAction(
            action.templateId.split(".")[0],
            "set_value",
            hashMapOf(
                "entity_id" to action.templateId,
                "value" to (action as? FloatAction)?.newValue.toString(),
            ),
        )
        return true
    }
}
