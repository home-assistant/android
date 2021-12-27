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
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.runBlocking
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.R)
class FanControl {
    companion object : HaControl {
        private const val SUPPORT_SET_SPEED = 1
        override fun createControl(
            context: Context,
            entity: Entity<Map<String, Any>>,
            area: AreaRegistryResponse?
        ): Control {
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
            control.setSubtitle(area?.name ?: "")
            control.setDeviceType(DeviceTypes.TYPE_FAN)
            control.setZone(context.getString(commonR.string.domain_fan))
            control.setStatus(Control.STATUS_OK)
            control.setStatusText(
                when (entity.state) {
                    "off" -> context.getString(commonR.string.state_off)
                    "on" -> context.getString(commonR.string.state_on)
                    "unavailable" -> context.getString(commonR.string.state_unavailable)
                    else -> context.getString(commonR.string.state_unknown)
                }
            )
            if ((entity.attributes["supported_features"] as Int) and SUPPORT_SET_SPEED == SUPPORT_SET_SPEED) {
                val minValue = 0f
                val maxValue = 100f
                var currentValue =
                    (entity.attributes["percentage"] as? Number)?.toFloat() ?: 0f
                if (currentValue < minValue)
                    currentValue = minValue
                if (currentValue > maxValue)
                    currentValue = maxValue
                control.setControlTemplate(
                    ToggleRangeTemplate(
                        entity.entityId,
                        entity.state == "on",
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
                )
            } else {
                control.setControlTemplate(
                    ToggleTemplate(
                        entity.entityId,
                        ControlButton(
                            entity.state == "on",
                            ""
                        )
                    )
                )
            }
            return control.build()
        }

        override fun performAction(
            integrationRepository: IntegrationRepository,
            action: ControlAction
        ): Boolean {
            return runBlocking {
                when (action) {
                    is BooleanAction -> {
                        integrationRepository.callService(
                            action.templateId.split(".")[0],
                            if (action.newState) "turn_on" else "turn_off",
                            hashMapOf("entity_id" to action.templateId)
                        )
                    }
                    is FloatAction -> {
                        val convertPercentage = action.newValue
                        integrationRepository.callService(
                            action.templateId.split(".")[0],
                            "set_percentage",
                            hashMapOf(
                                "entity_id" to action.templateId,
                                "percentage" to convertPercentage.toInt()
                            )
                        )
                    }
                }
                return@runBlocking true
            }
        }
    }
}
