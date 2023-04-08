package io.homeassistant.companion.android.complications

import android.graphics.Color
import android.graphics.drawable.Icon
import android.util.Log
import androidx.annotation.StringRes
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
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
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.friendlyState
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
        if (request.complicationType != ComplicationType.SHORT_TEXT && request.complicationType != ComplicationType.LONG_TEXT) {
            return null
        }

        val id = request.complicationInstanceId
        val isShort = request.complicationType == ComplicationType.SHORT_TEXT

        val entityId = entityStateComplicationsDao.get(id)?.entityId
            ?: return getErrorComplication(isShort, R.string.complication_entity_invalid)

        val entity = try {
            serverManager.integrationRepository().getEntity(entityId)
                ?: return getErrorComplication(isShort, R.string.state_unknown)
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to get entity state for $entityId: ${t.message}")
            return null
        }

        val icon = entity.getIcon(applicationContext) ?: CommunityMaterial.Icon.cmd_bookmark
        val iconBitmap = IconicsDrawable(this, icon).apply {
            colorInt = Color.WHITE
        }.toBitmap()

        val title = PlainComplicationText.Builder(entity.friendlyName).build()
        val text = PlainComplicationText.Builder(entity.friendlyState(this)).build()
        val contentDescription = PlainComplicationText.Builder(getText(R.string.complication_entity_state_content_description)).build()
        val monochromaticImage = MonochromaticImage.Builder(Icon.createWithBitmap(iconBitmap)).build()
        val tapAction = ComplicationReceiver.getComplicationToggleIntent(this, request.complicationInstanceId)

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = text,
                    contentDescription = contentDescription
                )
                    .setTitle(title)
                    .setTapAction(tapAction)
                    .setMonochromaticImage(monochromaticImage)
                    .build()
            }
            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = text,
                    contentDescription = contentDescription
                )
                    .setTitle(title)
                    .setTapAction(tapAction)
                    .setMonochromaticImage(monochromaticImage)
                    .build()
            }
            else -> null // Already handled at the start of the function
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val text = PlainComplicationText.Builder(getText(R.string.complication_entity_state_preview)).build()
        val contentDescription = PlainComplicationText.Builder(getText(R.string.complication_entity_state_content_description)).build()
        val title = PlainComplicationText.Builder(getText(R.string.entity)).build()
        val monochromaticImage = MonochromaticImage.Builder(
            Icon.createWithResource(
                this,
                io.homeassistant.companion.android.R.drawable.ic_lightbulb
            )
        ).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = text,
                    contentDescription = contentDescription
                )
                    .setTitle(title)
                    .setMonochromaticImage(monochromaticImage)
                    .build()
            }
            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = text,
                    contentDescription = contentDescription
                )
                    .setTitle(title)
                    .setMonochromaticImage(monochromaticImage)
                    .build()
            }
            else -> {
                Log.w(TAG, "Preview for unsupported complication type $type requested")
                null
            }
        }
    }

    /**
     * Get a simple complication for errors with [textRes] in the text slot
     */
    private fun getErrorComplication(isShort: Boolean, @StringRes textRes: Int): ComplicationData {
        val text = PlainComplicationText.Builder(getText(textRes)).build()
        val contentDescription = PlainComplicationText.Builder(getText(R.string.complication_entity_state_content_description)).build()
        return if (isShort) {
            ShortTextComplicationData.Builder(
                text = text,
                contentDescription = contentDescription
            ).build()
        } else {
            LongTextComplicationData.Builder(
                text = text,
                contentDescription = contentDescription
            ).build()
        }
    }
}
