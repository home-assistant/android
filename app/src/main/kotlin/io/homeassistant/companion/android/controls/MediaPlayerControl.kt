package io.homeassistant.companion.android.controls

import android.content.Context
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
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.getVolumeLevel
import io.homeassistant.companion.android.common.data.integration.getVolumeStep
import io.homeassistant.companion.android.common.data.integration.isActive
import io.homeassistant.companion.android.common.data.integration.supportsVolumeSet
import java.math.BigDecimal
import java.math.RoundingMode

@RequiresApi(Build.VERSION_CODES.R)
object MediaPlayerControl : HaControl {
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        entity: Entity,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        if (entity.supportsVolumeSet()) {
            val volumeLevel = entity.getVolumeLevel()
            control.setControlTemplate(
                ToggleRangeTemplate(
                    entity.entityId,
                    entity.isActive(),
                    "",
                    RangeTemplate(
                        entity.entityId,
                        volumeLevel?.min ?: 0f,
                        volumeLevel?.max ?: 100f,
                        volumeLevel?.value ?: 0f,
                        entity.getVolumeStep(),
                        "%.0f%%",
                    ),
                ),
            )
        } else {
            control.setControlTemplate(
                ToggleTemplate(
                    entity.entityId,
                    ControlButton(
                        entity.isActive(),
                        "",
                    ),
                ),
            )
        }
        return control
    }

    override fun getDeviceType(entity: Entity): Int = DeviceTypes.TYPE_TV

    override fun getDomainString(context: Context, entity: Entity): String =
        context.getString(commonR.string.media_player)

    override suspend fun performAction(integrationRepository: IntegrationRepository, action: ControlAction): Boolean {
        when (action) {
            is BooleanAction -> {
                integrationRepository.callAction(
                    action.templateId.split(".")[0],
                    "media_play_pause",
                    hashMapOf("entity_id" to action.templateId),
                )
            }
            is FloatAction -> {
                // Convert back to accepted format:
                // https://github.com/home-assistant/frontend/blob/dev/src/dialogs/more-info/controls/more-info-media_player.ts#L289
                val volumeLevel = action.newValue.div(100)
                integrationRepository.callAction(
                    action.templateId.split(".")[0],
                    "volume_set",
                    hashMapOf(
                        "entity_id" to action.templateId,
                        "volume_level" to BigDecimal(volumeLevel.toDouble()).setScale(2, RoundingMode.HALF_UP),
                    ),
                )
            }
        }
        return true
    }
}
