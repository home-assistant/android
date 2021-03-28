package io.homeassistant.companion.android.controls

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.ToggleRangeTemplate
import android.service.controls.templates.ToggleTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.runBlocking

@RequiresApi(Build.VERSION_CODES.R)
class CoverControl {
    companion object : HaControl {
        const val SUPPORT_SET_POSITION = 4
        override fun createControl(
            context: Context,
            entity: Entity<Map<String, Any>>
        ): Control {
            val control = Control.StatefulBuilder(
                    entity.entityId,
                    PendingIntent.getActivity(
                            context,
                            0,
                            WebViewActivity.newInstance(context.applicationContext, "entityId:${entity.entityId}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_CANCEL_CURRENT
                    )
            )
            control.setTitle(
                    (entity.attributes["friendly_name"] ?: entity.entityId) as CharSequence
            )
            control.setDeviceType(
                    when (entity.attributes["device_class"]) {
                        "awning" -> DeviceTypes.TYPE_AWNING
                        "blind" -> DeviceTypes.TYPE_BLINDS
                        "curtain" -> DeviceTypes.TYPE_CURTAIN
                        "door" -> DeviceTypes.TYPE_DOOR
                        "garage" -> DeviceTypes.TYPE_GARAGE
                        "gate" -> DeviceTypes.TYPE_GATE
                        "shutter" -> DeviceTypes.TYPE_SHUTTER
                        "window" -> DeviceTypes.TYPE_WINDOW
                        else -> DeviceTypes.TYPE_GENERIC_OPEN_CLOSE
                    }
            )
            control.setZone(context.getString(R.string.domain_cover))
            control.setStatus(Control.STATUS_OK)
            control.setStatusText(
                    when (entity.state) {
                        "closed" -> context.getString(R.string.state_closed)
                        "closing" -> context.getString(R.string.state_closing)
                        "open" -> context.getString(R.string.state_open)
                        "opening" -> context.getString(R.string.state_opening)
                        else -> entity.state
                    }
            )
            val minValue = 0f
            val maxValue = 100f
            var currentValue =
                    (entity.attributes["current_position"] as? Number)?.toFloat() ?: 0f
            if (currentValue < minValue)
                currentValue = minValue
            if (currentValue > maxValue)
                currentValue = maxValue
            control.setControlTemplate(
                    if ((entity.attributes["supported_features"] as Int) and SUPPORT_SET_POSITION == SUPPORT_SET_POSITION)
                        ToggleRangeTemplate(
                                entity.entityId,
                                entity.state !in listOf("closed", "closing"),
                                "",
                                RangeTemplate(
                                        entity.entityId,
                                        minValue,
                                        maxValue,
                                        currentValue,
                                        1f,
                                        "%.0f%%"
                                )
                        )
                    else
                        ToggleTemplate(
                                entity.entityId,
                                ControlButton(
                                        entity.state in listOf("open", "opening"),
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
                return@runBlocking when (action) {
                    is BooleanAction -> {
                        integrationRepository.callService(
                                action.templateId.split(".")[0],
                                if ((action as? BooleanAction)?.newState == true) "open_cover" else "close_cover",
                                hashMapOf(
                                        "entity_id" to action.templateId
                                )
                        )
                        true
                    }
                    is FloatAction -> {
                        val convertPosition = action.newValue
                        integrationRepository.callService(
                                action.templateId.split(".")[0],
                                "set_cover_position",
                                hashMapOf(
                                        "entity_id" to action.templateId,
                                        "position" to convertPosition.toInt()
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
