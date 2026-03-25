package io.homeassistant.companion.android.widgets.common

import android.widget.RemoteViews
import androidx.annotation.IdRes
import coil3.Image
import coil3.target.Target
import coil3.toBitmap

/**
 * [Target] implementation to load images into a [RemoteViews] ImageView with Coil.
 *
 * This target only mutates the provided [remoteViews] instance by setting the bitmap on
 * [imageViewResId]. It does **not** call [android.appwidget.AppWidgetManager.updateAppWidget].
 * Callers are responsible for triggering the widget update themselves after the request
 * completes (for example, in the surrounding widget update logic).
 *
 * Based on https://coil-kt.github.io/coil/recipes/#remote-views
 */
class RemoteViewsTarget(private val remoteViews: RemoteViews, @IdRes private val imageViewResId: Int) : Target {

    override fun onStart(placeholder: Image?) {
        // Skip if null to avoid blinking (there is no placeholder)
        placeholder?.let { setDrawable(it) }
    }

    override fun onError(error: Image?) = setDrawable(error)

    override fun onSuccess(result: Image) = setDrawable(result)

    private fun setDrawable(image: Image?) {
        remoteViews.setImageViewBitmap(imageViewResId, image?.toBitmap())
    }
}
