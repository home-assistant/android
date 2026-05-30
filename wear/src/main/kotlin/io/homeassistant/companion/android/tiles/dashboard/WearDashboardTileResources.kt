package io.homeassistant.companion.android.tiles.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.protolayout.ResourceBuilders
import com.mikepenz.iconics.IconicsColor
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.backgroundColor
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.util.getIcon
import java.nio.ByteBuffer
import kotlin.math.roundToInt

private const val ICON_SIZE_DP = 24f

/**
 * Builds ProtoLayout tile resources for dashboard icon bindings.
 */
object WearDashboardTileResources {

    /**
     * Adds inline image resources for [iconResourceIds] to [builder].
     */
    fun addIconResources(
        context: Context,
        builder: ResourceBuilders.Resources.Builder,
        iconResourceIds: Set<String>,
        screenDensity: Float,
    ) {
        val iconSizePx = (ICON_SIZE_DP * screenDensity).roundToInt()
        iconResourceIds.forEach { resourceId ->
            val iconName = resourceId.substringAfterLast(':')
            val icon = getIcon(iconName, "sensor", context)
            val iconBitmap = IconicsDrawable(context, icon).apply {
                colorInt = Color.WHITE
                sizeDp = ICON_SIZE_DP.roundToInt()
                backgroundColor = IconicsColor.colorRes(R.color.colorOverlay)
            }.toBitmap(iconSizePx, iconSizePx, Bitmap.Config.RGB_565)

            val bitmapData = ByteBuffer.allocate(iconBitmap.byteCount).apply {
                iconBitmap.copyPixelsToBuffer(this)
            }.array()

            builder.addIdToImageMapping(
                resourceId,
                ResourceBuilders.ImageResource.Builder()
                    .setInlineResource(
                        ResourceBuilders.InlineImageResource.Builder()
                            .setData(bitmapData)
                            .setWidthPx(iconSizePx)
                            .setHeightPx(iconSizePx)
                            .setFormat(ResourceBuilders.IMAGE_FORMAT_RGB_565)
                            .build(),
                    )
                    .build(),
            )
        }
    }
}
