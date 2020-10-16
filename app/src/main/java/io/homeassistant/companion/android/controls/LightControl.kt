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
class LightControl {
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
            control.setDeviceType(DeviceTypes.TYPE_LIGHT)
            control.setStatus(Control.STATUS_OK)
            control.setStatusText(if (entity.state == "off") "Off" else "On")
            control.setControlTemplate(
                ToggleRangeTemplate(
                    entity.entityId,
                    entity.state != "off",
                    "",
                    RangeTemplate(
                        entity.entityId,
                        0f,
                        100f,
                        (entity.attributes["brightness"] as? Number)
                            ?.toFloat()
                            ?.div(255f)
                            ?.times(100) ?: 0f,
                        1f,
                        "%.0f%%"
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
                return@runBlocking when (action) {
                    is BooleanAction -> {
                        integrationRepository.callService(
                            action.templateId.split(".")[0],
                            if (action.newState) "turn_on" else "turn_off",
                            hashMapOf(
                                "entity_id" to action.templateId
                            )
                        )
                        true
                    }
                    is FloatAction -> {
                        val convertBrightness = action.newValue.div(100).times(255)
                        integrationRepository.callService(
                            action.templateId.split(".")[0],
                            "turn_on",
                            hashMapOf(
                                "entity_id" to action.templateId,
                                "brightness" to convertBrightness.toInt()
                            )
                        )
                        true
                    }
                    else -> {
                        false
                    }
                }
            }
        }
    }
}
