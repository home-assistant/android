package io.homeassistant.companion.android.controls

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.controls.Control
import android.service.controls.actions.ControlAction
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.timoptr.mdiicons.toBitmap
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CLIMATE_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.LIGHT_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.intentLaunchWithNavigateTo

private const val CONTROL_ICON_SIZE_DP = 48

@RequiresApi(Build.VERSION_CODES.R)
interface HaControl {

    @SuppressLint("ResourceType")
    fun createControl(context: Context, item: EntityDisplayWithContext, info: HaControlInfo): Control {
        val controlIntent =
            context.applicationContext.intentLaunchWithNavigateTo(
                FrontendTarget.EntityMoreInfo(info.entityId),
                info.serverId,
            )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val control = Control.StatefulBuilder(
            info.systemId,
            PendingIntent.getActivity(
                context,
                info.entityId.hashCode(),
                controlIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        )
        control.setTitle(item.name)
        control.setSubtitle(item.areaName ?: "")
        control.setDeviceType(getDeviceType(item))

        if (info.splitMultiServerIntoStructure && info.serverName != null) {
            control.setZone(item.areaName ?: getDomainString(context, item))
            control.setStructure(info.serverName)
        } else {
            control.setZone(
                (if (info.serverName != null) "${info.serverName}: " else "") +
                    (item.areaName ?: getDomainString(context, item)),
            )
        }
        control.setStatus(Control.STATUS_OK)
        control.setStatusText(item.state.resolve(context))
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            control.setAuthRequired(info.authRequired)
        }
        // Render the resolved icon to match the HA frontend rather than the provided device type
        val colorTint = when {
            item.domain == LIGHT_DOMAIN && item.rawState == "on" -> R.color.colorDeviceControlsLightOn
            item.domain == CAMERA_DOMAIN -> R.color.colorDeviceControlsCamera
            item.domain == CLIMATE_DOMAIN && item.rawState == "heat"
            -> R.color.colorDeviceControlsThermostatHeat

            item.rawState in listOf(
                "off",
                "unavailable",
                "unknown",
            ) -> R.color.colorDeviceControlsOff

            else -> R.color.colorDeviceControlsDefaultOn
        }
        val iconBitmap = item.icon.toBitmap(context, CONTROL_ICON_SIZE_DP, ContextCompat.getColor(context, colorTint))
        control.setCustomIcon(IconCompat.createWithBitmap(iconBitmap).toIcon(context))

        return provideControlFeatures(context, control, item, info).build()
    }

    fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        item: EntityDisplayWithContext,
        info: HaControlInfo,
    ): Control.StatefulBuilder

    fun getDeviceType(item: EntityDisplayWithContext): Int

    fun getDomainString(context: Context, item: EntityDisplayWithContext): String

    suspend fun performAction(
        integrationRepository: IntegrationRepository,
        action: ControlAction,
        serverId: Int,
    ): Boolean
}
