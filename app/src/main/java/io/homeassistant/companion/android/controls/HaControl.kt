package io.homeassistant.companion.android.controls

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.service.controls.Control
import android.service.controls.actions.ControlAction
import androidx.annotation.RequiresApi
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.toAndroidIconCompat
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.webview.WebViewActivity

@RequiresApi(Build.VERSION_CODES.R)
interface HaControl {

    fun createControl(
        context: Context,
        entity: Entity<Map<String, Any>>,
        area: AreaRegistryResponse?,
        authRequired: Boolean,
        baseUrl: String?
    ): Control {
        val controlPath = "entityId:${entity.entityId}"
        val control = Control.StatefulBuilder(
            entity.entityId,
            PendingIntent.getActivity(
                context,
                controlPath.hashCode(),
                WebViewActivity.newInstance(context.applicationContext, controlPath).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            control.setAuthRequired(authRequired)
        }
        if (entity.attributes["icon"]?.toString()?.startsWith("mdi:") == true &&
            !entity.attributes["icon"]?.toString()?.substringAfter(":").isNullOrBlank()
        ) {
            val iconName = entity.attributes["icon"]!!.toString().split(':')[1]
            val iconDrawable =
                IconicsDrawable(context, "cmd-$iconName")
            val colorTint = when (entity.state) {
                "on" -> "#FDD663"
                "off", "unavailable", "unknown" -> "#A6A6A7"
                else -> "#8AB4F8"
            }
            iconDrawable.setTint(Color.parseColor(colorTint))
            control.setCustomIcon(iconDrawable.toAndroidIconCompat().toIcon(context))
        }

        return provideControlFeatures(context, control, entity, area, baseUrl).build()
    }

    fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        entity: Entity<Map<String, Any>>,
        area: AreaRegistryResponse?,
        baseUrl: String?
    ): Control.StatefulBuilder

    fun getDeviceType(entity: Entity<Map<String, Any>>): Int

    fun getDomainString(context: Context, entity: Entity<Map<String, Any>>): String

    suspend fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean
}
