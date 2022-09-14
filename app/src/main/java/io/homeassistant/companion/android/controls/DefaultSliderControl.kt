package io.homeassistant.companion.android.controls

import android.content.Context
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.RangeTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import kotlinx.coroutines.runBlocking
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.R)
object DefaultSliderControl : HaControl {
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        entity: Entity<Map<String, Any>>,
        area: AreaRegistryResponse?,
        baseUrl: String?
    ): Control.StatefulBuilder {
        control.setStatusText("")
        control.setControlTemplate(
            RangeTemplate(
                entity.entityId,
                (entity.attributes["min"] as? Number)?.toFloat() ?: 0f,
                (entity.attributes["max"] as? Number)?.toFloat() ?: 1f,
                entity.state.toFloatOrNull() ?: 0f,
                (entity.attributes["step"] as? Number)?.toFloat() ?: 1f,
                null
            )
        )
        return control
    }

    override fun getDeviceType(entity: Entity<Map<String, Any>>): Int =
        DeviceTypes.TYPE_UNKNOWN

    override fun getDomainString(context: Context, entity: Entity<Map<String, Any>>): String =
        context.getString(commonR.string.domain_input_number)

    override suspend fun performAction(
        integrationRepository: IntegrationRepository,
        action: ControlAction
    ): Boolean {
        return runBlocking {
            integrationRepository.callService(
                action.templateId.split(".")[0],
                "set_value",
                hashMapOf(
                    "entity_id" to action.templateId,
                    "value" to (action as? FloatAction)?.newValue.toString()
                )
            )
            return@runBlocking true
        }
    }
}
