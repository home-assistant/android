package io.homeassistant.companion.android.settings.shortcuts.v2.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.mikepenz.iconics.IconicsColor
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.utils.backgroundColor
import com.mikepenz.iconics.utils.size
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR

/**
 * Builds adaptive icons from [com.mikepenz.iconics.typeface.IIcon] assets using launcher sizing rules.
 */
internal object ShortcutIconRenderer {
    private const val FOREGROUND_ICON_DP = 48
    private const val ADAPTIVE_ICON_SIZE_DP = 108f

    /**
     * Replicate an adaptive icon by applying the right measure and background.
     * Returns [androidx.core.graphics.drawable.IconCompat.createWithAdaptiveBitmap] to flag the bitmap as adaptive.
     *
     * @see [android.graphics.drawable.AdaptiveIconDrawable] for sizing details.
     */
    fun renderAdaptiveIcon(context: Context, icon: IIcon): IconCompat {
        val iconDrawable = IconicsDrawable(context, icon).apply {
            size = IconicsSize.dp(FOREGROUND_ICON_DP)
            colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(context, commonR.color.colorAccent),
                PorterDuff.Mode.SRC_IN,
            )
            backgroundColor = IconicsColor.Companion.colorInt(Color.TRANSPARENT)
        }

        val adaptiveIconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            ADAPTIVE_ICON_SIZE_DP,
            context.resources.displayMetrics,
        ).toInt()
        val adaptiveBitmap = createBitmap(adaptiveIconSize, adaptiveIconSize)
        val canvas = Canvas(adaptiveBitmap)
        // Use the same color as the foreground of the launcher as background
        canvas.drawColor(ContextCompat.getColor(context, R.color.ic_launcher_foreground))
        // Calculate the position to draw the icon in the center
        val x = (canvas.width - iconDrawable.intrinsicWidth) / 2f
        val y = (canvas.height - iconDrawable.intrinsicHeight) / 2f
        canvas.translate(x, y)
        iconDrawable.draw(canvas)

        return IconCompat.createWithAdaptiveBitmap(adaptiveBitmap)
    }
}
