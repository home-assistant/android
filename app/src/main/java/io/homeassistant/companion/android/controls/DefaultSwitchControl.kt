package io.homeassistant.companion.android.controls

import android.app.PendingIntent
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
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.runBlocking

@RequiresApi(Build.VERSION_CODES.R)
class DefaultSwitchControl {
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
                    WebViewActivity.newInstance(context),
                    PendingIntent.FLAG_CANCEL_CURRENT
                )
            )
            control.setTitle(entity.attributes["friendly_name"].toString())
            control.setDeviceType(
                when (entity.entityId.split(".")[0]) {
                    "switch" -> DeviceTypes.TYPE_SWITCH
                    else -> DeviceTypes.TYPE_GENERIC_ON_OFF
                }
            )
            control.setStatus(Control.STATUS_OK)
            control.setStatusText(if (entity.state == "off") "Off" else "On")
            control.setControlTemplate(
                ToggleTemplate(
                    entity.entityId,
                    ControlButton(
                        entity.state == "on",
                        "Description"
                    )
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
                    if ((action as? BooleanAction)?.newState == true) "turn_on" else "turn_off",
                    hashMapOf("entity_id" to action.templateId)
                )
                return@runBlocking true
            }
        }
    }
}
