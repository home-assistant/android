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
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.colorInt
import io.homeassistant.companion.android.common.R

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
        val icon = CommunityMaterial.Icon.cmd_comment_processing_outline
        return IconicsDrawable(this, icon).apply {
            colorInt = Color.WHITE
        }.toBitmap()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData = MonochromaticImageComplicationData.Builder(
        monochromaticImage = MonochromaticImage.Builder(Icon.createWithBitmap(getAssistIcon())).build(),
        contentDescription = PlainComplicationText.Builder(getText(R.string.assist)).build(),
    )
        .build()
}
