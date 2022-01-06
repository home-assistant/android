package io.homeassistant.companion.android.controls

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.controls.Control
import android.service.controls.actions.ControlAction
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.webview.WebViewActivity

@RequiresApi(Build.VERSION_CODES.R)
interface HaControl {

    fun createControl(context: Context, entity: Entity<Map<String, Any>>, area: AreaRegistryResponse?): Control {
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
        control.setDeviceType(getDeviceType(entity))
        control.setZone(area?.name ?: getDomainString(context, entity))
        control.setStatus(Control.STATUS_OK)
        control.setStatusText(
            when (entity.state) {
                "off" -> context.getString(R.string.state_off)
                "on" -> context.getString(R.string.state_on)
                "unavailable" -> context.getString(R.string.state_unavailable)
                else -> context.getString(R.string.state_unknown)
            }
        )

        return provideControlFeatures(context, control, entity, area).build()
    }

    fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        entity: Entity<Map<String, Any>>,
        area: AreaRegistryResponse?
    ): Control.StatefulBuilder

    fun getDeviceType(entity: Entity<Map<String, Any>>): Int

    fun getDomainString(context: Context, entity: Entity<Map<String, Any>>): String

    fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean
}
