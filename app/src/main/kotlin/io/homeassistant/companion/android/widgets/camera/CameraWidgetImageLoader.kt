package io.homeassistant.companion.android.widgets.camera

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/** Authenticated image fetcher using the app's configured Coil loader. */
internal class CameraWidgetImageLoader @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun fetch(url: String, widgetId: Int): Bitmap? = try {
        context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .diskCachePolicy(CachePolicy.DISABLED)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .networkCachePolicy(CachePolicy.READ_ONLY)
                .size(widgetBitmapSize(widgetId))
                .precision(Precision.INEXACT)
                .build(),
        ).image?.toBitmap()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Timber.e(error, "Unable to fetch camera image")
        null
    }

    private fun widgetBitmapSize(widgetId: Int): Size {
        val resources = Resources.getSystem()
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val maxPixels = (1.35 * screenWidth * screenHeight).toInt()
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val density = resources.displayMetrics.density
        val widthPx: Int
        val heightPx: Int
        if (widthDp <= 0) {
            widthPx = screenWidth
            heightPx = screenHeight
        } else {
            widthPx = (widthDp * density).toInt()
            heightPx = if (heightDp > 0) (heightDp * density).toInt() else 0
        }
        if (heightPx <= 0) {
            val cappedWidth = minOf(widthPx, maxPixels / maxOf(screenHeight, 1)).coerceAtLeast(1)
            return Size(cappedWidth, (maxPixels / cappedWidth).coerceAtLeast(1))
        }
        val pixels = widthPx.toLong() * heightPx
        return if (pixels > maxPixels) {
            val scale = sqrt(maxPixels.toDouble() / pixels).toFloat()
            Size((widthPx * scale).toInt().coerceAtLeast(1), (heightPx * scale).toInt().coerceAtLeast(1))
        } else {
            Size(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))
        }
    }
}
