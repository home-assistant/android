package io.homeassistant.companion.android.complications

import android.graphics.Color
import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.colorInt
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.wear.EntityStateComplicationsDao
import javax.inject.Inject

@AndroidEntryPoint
class EntityStateDataSourceService : SuspendingComplicationDataSourceService() {

    @Inject
    lateinit var serverManager: ServerManager
    @Inject
    lateinit var entityStateComplicationsDao: EntityStateComplicationsDao

    companion object {
        const val TAG = "EntityStateDataSourceService"
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT)
            return null

        val id = request.complicationInstanceId

        val entityId = entityStateComplicationsDao.get(id)?.entityId
            ?: return ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(getText(R.string.complication_entity_invalid)).build(),
                contentDescription = PlainComplicationText.Builder(getText(R.string.complication_entity_state_content_description))
                    .build()
            ).build()

        val entity = try {
            serverManager.integrationRepository().getEntity(entityId)
                ?: return ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(getText(R.string.state_unknown)).build(),
                    contentDescription = PlainComplicationText.Builder(getText(R.string.complication_entity_state_content_description))
                        .build()
                ).build()
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to get entity state for $entityId: ${t.message}")
            return null
        }

        val attributes = entity.attributes as Map<*, *>
        val icon = entity.getIcon(applicationContext) ?: CommunityMaterial.Icon.cmd_bookmark
        val iconBitmap = IconicsDrawable(this, icon).apply {
            colorInt = Color.WHITE
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
