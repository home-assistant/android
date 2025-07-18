package io.homeassistant.companion.android.controls

import android.content.Context
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.actions.ControlAction
import android.service.controls.templates.StatelessTemplate
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.util.capitalize
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.R)
object HaFailedControl : HaControl {
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        entity: Entity,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        control.setStatus(if (entity.state == "notfound") Control.STATUS_NOT_FOUND else Control.STATUS_ERROR)
        control.setStatusText("")
        control.setControlTemplate(
            StatelessTemplate(
                entity.entityId,
            ),
        )
        return control
    }

    override fun getDeviceType(entity: Entity): Int = DeviceTypes.TYPE_UNKNOWN

    override fun getDomainString(context: Context, entity: Entity): String =
        entity.domain.capitalize(Locale.getDefault())

    override suspend fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean {
        return false
    }
}
