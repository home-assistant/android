package io.homeassistant.companion.android.complications

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.mikepenz.iconics.IconicsColor
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.backgroundColor
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.util.getIcon
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class EntityStateDataSourceService : SuspendingComplicationDataSourceService() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT)
            return null

        val entity = integrationUseCase.getEntity("light.keuken_lampen")
            ?: return ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(getText(R.string.state_unknown)).build(),
                contentDescription = PlainComplicationText.Builder(getText(R.string.complication_entity_state_content_description))
                    .build()
            ).build()

        val attributes = entity.attributes as Map<*, *>
        val icon = getIcon(entity, entity.domain, applicationContext) ?: CommunityMaterial.Icon.cmd_cellphone
        val iconBitmap = IconicsDrawable(this, icon).apply {
            colorInt = Color.WHITE
            backgroundColor = IconicsColor.colorRes(io.homeassistant.companion.android.R.color.colorOverlay)
        }.toBitmap()

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(entity.state).build(),
            contentDescription = PlainComplicationText.Builder(getText(R.string.complication_entity_state_content_description))
                .build()
        )
            .setTapAction(ComplicationReceiver.getComplicationToggleIntent(this, request.complicationInstanceId))
            .setMonochromaticImage(MonochromaticImage.Builder(Icon.createWithBitmap(iconBitmap)).build())
            .setTitle(PlainComplicationText.Builder(attributes["friendly_name"] as String? ?: entity.entityId).build())
            .build()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(getText(R.string.complication_entity_state_preview)).build(),
            contentDescription = PlainComplicationText.Builder(getText(R.string.complication_entity_state_content_description)).build()
        )
            .setMonochromaticImage(
                MonochromaticImage.Builder(
                    Icon.createWithResource(
                        this,
                        io.homeassistant.companion.android.R.drawable.ic_lightbulb,
                    ),
                ).build(),
            )
            .setTitle(PlainComplicationText.Builder(getText(R.string.entity)).build())
            .build()
}