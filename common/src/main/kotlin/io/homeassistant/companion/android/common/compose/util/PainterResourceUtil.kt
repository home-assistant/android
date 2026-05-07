package io.homeassistant.companion.android.common.compose.util

import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
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
 * the resulting [androidx.compose.foundation.Image] with `Modifier.clip(CircleShape)` this
 * helper returns the unmasked bitmap.
 *
 * Got from https://gist.github.com/tkuenneth/ddf598663f041dc79960cda503d14448.
 */
@Composable
fun adaptiveIconPainterResource(@DrawableRes id: Int): Painter {
    val res = LocalResources.current
    val theme = LocalContext.current.theme

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Android O supports adaptive icons, try loading this first (even though this is least likely to be the format).
        val adaptiveIcon = ResourcesCompat.getDrawable(res, id, theme) as? AdaptiveIconDrawable
        if (adaptiveIcon != null) {
            BitmapPainter(adaptiveIcon.toBitmap().asImageBitmap())
        } else {
            // We couldn't load the drawable as an Adaptive Icon, just use painterResource
            painterResource(id)
        }
    } else {
        // We're not on Android O or later, just use painterResource
        painterResource(id)
    }
}
