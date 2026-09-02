package io.homeassistant.companion.android.complications

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.CommentProcessingOutline
import io.github.timoptr.mdiicons.toBitmap
import io.homeassistant.companion.android.common.R
private const val COMPLICATION_ICON_SIZE_DP = 24

class AssistDataSourceService : ComplicationDataSourceService() {

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        if (request.complicationType != ComplicationType.MONOCHROMATIC_IMAGE) {
            return
        }

        listener.onComplicationData(
            MonochromaticImageComplicationData.Builder(
                monochromaticImage = MonochromaticImage.Builder(Icon.createWithBitmap(getAssistIcon())).build(),
                contentDescription = PlainComplicationText.Builder(getText(R.string.assist))
                    .build(),
            )
                .setTapAction(ComplicationReceiver.getAssistIntent(this))
                .build(),
        )
    }

    private fun getAssistIcon(): Bitmap {
        return Mdi.CommentProcessingOutline.toBitmap(this, COMPLICATION_ICON_SIZE_DP, Color.WHITE)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData = MonochromaticImageComplicationData.Builder(
        monochromaticImage = MonochromaticImage.Builder(Icon.createWithBitmap(getAssistIcon())).build(),
        contentDescription = PlainComplicationText.Builder(getText(R.string.assist)).build(),
    )
        .build()
}
