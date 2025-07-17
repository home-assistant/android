package io.homeassistant.companion.android.widgets.common

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.IdRes
import coil3.Image
import coil3.target.Target
import coil3.toBitmap

/**
 * Load images into RemoteViews with Coil
 * (based on https://coil-kt.github.io/coil/recipes/#remote-views)
 */
class RemoteViewsTarget(
    private val context: Context,
    private val appWidgetId: Int,
    private val remoteViews: RemoteViews,
    @IdRes private val imageViewResId: Int,
) : Target {

    override fun onStart(placeholder: Image?) {
        // Skip if null to avoid blinking (there is no placeholder)
        placeholder?.let { setDrawable(it) }
    }

    override fun onError(error: Image?) = setDrawable(error)

    override fun onSuccess(result: Image) = setDrawable(result)

    private fun setDrawable(image: Image?) {
        remoteViews.setImageViewBitmap(imageViewResId, image?.toBitmap())
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews)
    }
}
