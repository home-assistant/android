package io.homeassistant.companion.android.controls

import android.content.Context
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.ControlAction
import android.service.controls.templates.StatelessTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import kotlinx.coroutines.runBlocking
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.R)
class SceneControl {
    companion object : HaControl {
        override fun provideControlFeatures(
            context: Context,
            control: Control.StatefulBuilder,
            entity: Entity<Map<String, Any>>,
            area: AreaRegistryResponse?
        ): Control.StatefulBuilder {
            control.setStatusText("")
            control.setControlTemplate(
                StatelessTemplate(
                    entity.entityId
                )
            )
            return control
        }

        override fun getDeviceType(entity: Entity<Map<String, Any>>): Int =
            DeviceTypes.TYPE_ROUTINE

        override fun getDomainString(context: Context, entity: Entity<Map<String, Any>>): String =
            when (entity.entityId.split(".")[0]) {
                "scene" -> context.getString(commonR.string.domain_scene)
                "script" -> context.getString(commonR.string.domain_script)
                else -> entity.entityId.split(".")[0].capitalize()
            }

        override fun performAction(
            integrationRepository: IntegrationRepository,
            action: ControlAction
        ): Boolean {
            return runBlocking {
                integrationRepository.callService(
                    action.templateId.split(".")[0],
                    "turn_on",
                    hashMapOf("entity_id" to action.templateId)
                )
                return@runBlocking true
            }
        }
    }
}
