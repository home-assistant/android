package io.homeassistant.companion.android.complications

import android.graphics.Color
import android.graphics.drawable.Icon
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
import com.mikepenz.iconics.utils.colorInt
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.awaitLoadedOrNull
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.wear.EntityStateComplicationsDao
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class EntityStateDataSourceService : SuspendingComplicationDataSourceService() {

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var entityStateComplicationsDao: EntityStateComplicationsDao

    @Inject
    lateinit var entitiesForDisplayManager: EntitiesForDisplayManager

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT &&
            request.complicationType != ComplicationType.LONG_TEXT
        ) {
            return null
        }

        val settings = entityStateComplicationsDao.get(request.complicationInstanceId)
        val entityId = settings?.entityId
            ?: return getErrorComplication(request, R.string.complication_entity_invalid, true)

        val displayEntity = entitiesForDisplayManager.snapshot(ServerManager.SERVER_ID_ACTIVE, listOf(entityId))
            .awaitLoadedOrNull()
            ?.entity(entityId)
            ?: return getErrorComplication(request, R.string.complication_entity_invalid)

        val iconBitmap = IconicsDrawable(this, displayEntity.icon).apply {
            colorInt = Color.WHITE
        }.toBitmap()

        val title = if (settings.showTitle) {
            PlainComplicationText.Builder(displayEntity.name).build()
        } else {
            null
        }

        val text = PlainComplicationText.Builder(
            displayEntity.state.resolve(this, withUnit = settings.showUnit),
        ).build()

        val contentDescription = PlainComplicationText.Builder(
            getText(R.string.complication_entity_state_content_description),
        ).build()
        val monochromaticImage = MonochromaticImage.Builder(Icon.createWithBitmap(iconBitmap)).build()
        val tapAction = ComplicationReceiver.getComplicationToggleIntent(this, request.complicationInstanceId)

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = text,
                    contentDescription = contentDescription,
                )
                    .setTitle(title)
                    .setTapAction(tapAction)
                    .setMonochromaticImage(monochromaticImage)
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = text,
                    contentDescription = contentDescription,
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
        val contentDescription = PlainComplicationText.Builder(
            getText(R.string.complication_entity_state_content_description),
        ).build()
        val title = PlainComplicationText.Builder(getText(R.string.entity)).build()
        val monochromaticImage = MonochromaticImage.Builder(
            Icon.createWithResource(
                this,
                io.homeassistant.companion.android.R.drawable.ic_lightbulb,
            ),
        ).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = text,
                    contentDescription = contentDescription,
                )
                    .setTitle(title)
                    .setMonochromaticImage(monochromaticImage)
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = text,
                    contentDescription = contentDescription,
                )
                    .setTitle(title)
                    .setMonochromaticImage(monochromaticImage)
                    .build()
            }

            else -> {
                Timber.w("Preview for unsupported complication type $type requested")
                null
            }
        }
    }

    /**
     * Get a simple complication for errors with [textRes] in the text slot.
     *
     * @param setTapAction If tapping the complication should open configuration
     */
    private fun getErrorComplication(
        request: ComplicationRequest,
        @StringRes textRes: Int,
        setTapAction: Boolean = false,
    ): ComplicationData {
        val text = PlainComplicationText.Builder(
            if (setTapAction) {
                "+"
            } else {
                getText(textRes)
            },
        ).build()
        val contentDescription = PlainComplicationText.Builder(
            getText(R.string.complication_entity_state_content_description),
        ).build()
        val tapAction = if (setTapAction) {
            ComplicationReceiver.getComplicationConfigureIntent(this, request.complicationInstanceId)
        } else {
            null
        }
        return if (request.complicationType == ComplicationType.SHORT_TEXT) {
            ShortTextComplicationData.Builder(
                text = text,
                contentDescription = contentDescription,
            ).setTapAction(tapAction).build()
        } else {
            LongTextComplicationData.Builder(
                text = text,
                contentDescription = contentDescription,
            ).setTapAction(tapAction).build()
        }
    }
}
