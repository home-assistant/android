package io.homeassistant.companion.android.controls

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.ToggleRangeTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.runBlocking

@RequiresApi(Build.VERSION_CODES.R)
class FanControl {
    companion object : HaControl {
        override fun createControl(
            context: Context,
            entity: Entity<Map<String, Any>>
        ): Control {
            val speeds = entity.attributes["speed_list"].toString().split(", ")

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
            control.setDeviceType(DeviceTypes.TYPE_FAN)
            control.setStatus(Control.STATUS_OK)
            control.setControlTemplate(
                ToggleRangeTemplate(
                    entity.entityId,
                    entity.state != "off",
                    "",
                    RangeTemplate(
                        entity.entityId,
                        0f,
                        speeds.size.toFloat(),
                        speeds.indexOf(entity.state).toFloat(),
                        1f,
                        ""
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
                // TODO: Get these from entity
                val speeds = listOf("off", "low", "medium", "high")
                val speed: String = if (action is BooleanAction) {
                    if (action.newState) speeds.last() else speeds.first()
                } else if (action is FloatAction) {
                    speeds[action.newValue.toInt()]
                } else {
                    ""
                }
                integrationRepository.callService(
                    action.templateId.split(".")[0],
                    "set_speed",
                    hashMapOf(
                        "entity_id" to action.templateId,
                        "speed" to speed
                    )
                )
                return@runBlocking true
            }
        }
    }
}
