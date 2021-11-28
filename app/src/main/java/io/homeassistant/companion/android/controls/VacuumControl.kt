package io.homeassistant.companion.android.controls

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.ToggleTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.runBlocking
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.R)
class VacuumControl {
    companion object : HaControl {
        private const val SUPPORT_TURN_ON = 1
        private var entitySupportedFeatures = 0

        override fun createControl(
            context: Context,
            entity: Entity<Map<String, Any>>
        ): Control {
            entitySupportedFeatures = entity.attributes["supported_features"] as Int
            val control = Control.StatefulBuilder(
                entity.entityId,
                PendingIntent.getActivity(
                    context,
                    0,
                    WebViewActivity.newInstance(context.applicationContext, "entityId:${entity.entityId}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            control.setTitle((entity.attributes["friendly_name"] ?: entity.entityId) as CharSequence)
            control.setDeviceType(DeviceTypes.TYPE_VACUUM)
            control.setZone(context.getString(commonR.string.domain_vacuum))
            control.setStatus(Control.STATUS_OK)
            control.setStatusText(
                if (entitySupportedFeatures and SUPPORT_TURN_ON == SUPPORT_TURN_ON) {
                    when (entity.state) {
                        "off" -> context.getString(commonR.string.state_off)
                        "on" -> context.getString(commonR.string.state_on)
                        "unavailable" -> context.getString(commonR.string.state_unavailable)
                        else -> context.getString(commonR.string.state_unknown)
                    }
                } else {
                    when (entity.state) {
                        "cleaning" -> context.getString(commonR.string.state_cleaning)
                        "docked" -> context.getString(commonR.string.state_docked)
                        "error" -> context.getString(commonR.string.state_error)
                        "idle" -> context.getString(commonR.string.state_idle)
                        "paused" -> context.getString(commonR.string.state_paused)
                        "returning" -> context.getString(commonR.string.state_returning)
                        "unavailable" -> context.getString(commonR.string.state_unavailable)
                        else -> context.getString(commonR.string.state_unknown)
                    }
                }
            )
            control.setControlTemplate(
                ToggleTemplate(
                    entity.entityId,
                    ControlButton(
                        if (entitySupportedFeatures and SUPPORT_TURN_ON == SUPPORT_TURN_ON)
                            entity.state == "on"
                        else
                            entity.state == "cleaning",
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
                    if (entitySupportedFeatures and SUPPORT_TURN_ON == SUPPORT_TURN_ON)
                        if ((action as? BooleanAction)?.newState == true) "turn_on" else "turn_off"
                    else if ((action as? BooleanAction)?.newState == true) "start" else "return_to_base",
                    hashMapOf(
                        "entity_id" to action.templateId
                    )
                )
                true
            }
        }
    }
}
