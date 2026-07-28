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
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import java.math.BigDecimal
import java.math.RoundingMode

@RequiresApi(Build.VERSION_CODES.R)
object MediaPlayerControl : HaControl {
    override fun provideControlFeatures(
        context: Context,
        control: Control.StatefulBuilder,
        item: EntityDisplayWithContext,
        info: HaControlInfo,
    ): Control.StatefulBuilder {
        val volume = item.mediaPlayerControls?.volume
        if (volume != null) {
            control.setControlTemplate(
                ToggleRangeTemplate(
                    item.entityId,
                    item.isActive,
                    "",
                    RangeTemplate(
                        item.entityId,
                        volume.min,
                        volume.max,
                        volume.value,
                        item.mediaPlayerControls?.volumeStep ?: 0.1f,
                        "%.0f%%",
                    ),
                ),
            )
        } else {
            control.setControlTemplate(
                ToggleTemplate(
                    item.entityId,
                    ControlButton(
                        item.isActive,
                        "",
                    ),
                ),
            )
        }
        return control
    }

    override fun getDeviceType(item: EntityDisplayWithContext): Int = DeviceTypes.TYPE_TV

    override fun getDomainString(context: Context, item: EntityDisplayWithContext): String =
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
