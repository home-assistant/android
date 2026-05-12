package io.homeassistant.companion.android.common.compose.util

import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Loads a drawable as a Compose [Painter], with fallback support for [AdaptiveIconDrawable].
 *
 * Compose's stock [painterResource] only handles [android.graphics.drawable.VectorDrawable] and
 * rasterized assets (PNG / JPG / WebP); it throws on adaptive icons (`<adaptive-icon>` XML in
 * `mipmap-anydpi-v26`), which is the format launcher icons use on API 26+. This helper detects
 * that case and rasterises the adaptive icon to a [android.graphics.Bitmap] via [toBitmap], then
 * wraps it in a [BitmapPainter]. For every other resource type and on pre-O devices where
 * adaptive icons don't exist it delegates to [painterResource] unchanged.
 *
 * Typical use: rendering the app launcher icon (e.g. `R.mipmap.ic_launcher_round`) inside Compose.
 *
 * Note: callers that want a circular icon (matching launcher behaviour on API 26+) should clip
 * the resulting [androidx.compose.foundation.Image] with `Modifier.clip(CircleShape)`; this
 * helper returns the unmasked bitmap.
 *
 * Based on https://gist.github.com/tkuenneth/ddf598663f041dc79960cda503d14448.
 */
@Composable
fun adaptiveIconPainterResource(@DrawableRes id: Int): Painter {
    val resources = LocalResources.current
    val theme = LocalContext.current.theme

    val adaptivePainter: Painter? = remember(id, resources, theme) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@remember null
        val drawable = ResourcesCompat.getDrawable(resources, id, theme) as? AdaptiveIconDrawable
            ?: return@remember null
        BitmapPainter(drawable.toBitmap().asImageBitmap())
    }

    return adaptivePainter ?: painterResource(id)
}
