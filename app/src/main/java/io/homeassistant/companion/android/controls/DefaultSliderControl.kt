package io.homeassistant.companion.android.controls

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.RangeTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.runBlocking

@RequiresApi(Build.VERSION_CODES.R)
class DefaultSliderControl {
    companion object : HaControl {
        override fun createControl(
            context: Context,
            entity: Entity<Map<String, Any>>
        ): Control {
            val control = Control.StatefulBuilder(
                entity.entityId,
                PendingIntent.getActivity(
                    context,
                    0,
                    WebViewActivity.newInstance(context.applicationContext).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_CANCEL_CURRENT
                )
            )
            control.setTitle(entity.attributes["friendly_name"].toString())
            control.setDeviceType(DeviceTypes.TYPE_UNKNOWN)
            control.setStatus(Control.STATUS_OK)
            control.setControlTemplate(
                RangeTemplate(
                    entity.entityId,
                    (entity.attributes["min"] as? Number)?.toFloat() ?: 0f,
                    (entity.attributes["max"] as? Number)?.toFloat() ?: 0f,
                    entity.state.toFloatOrNull() ?: 0f,
                    (entity.attributes["step"] as? Number)?.toFloat() ?: 0f,
                    null
                )
            )
            return control.build()
        }

        override fun performAction(
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
}
