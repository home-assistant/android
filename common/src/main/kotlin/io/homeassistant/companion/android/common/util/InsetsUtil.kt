package io.homeassistant.companion.android.common.util

import android.util.DisplayMetrics
import android.webkit.WebView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.util.TypedValueCompat.pxToDp
import timber.log.Timber

fun WebView.applyInsets(
    insets: WindowInsets,
    density: Density,
    displayMetrics: DisplayMetrics,
    layoutDirection: LayoutDirection,
) {
    Timber.e("Apply insets")
    val safeInsetTop = pxToDp(insets.getTop(density).toFloat(), displayMetrics)
    val safeInsetRight = pxToDp(insets.getRight(density, layoutDirection).toFloat(), displayMetrics)
    val safeInsetBottom = pxToDp(insets.getBottom(density).toFloat(), displayMetrics)
    val safeInsetLeft = pxToDp(insets.getLeft(density, layoutDirection).toFloat(), displayMetrics)
    val safeAreaJs = """
                        document.documentElement.style.setProperty('--app-safe-area-inset-top', '${safeInsetTop}px');
                        document.documentElement.style.setProperty('--app-safe-area-inset-bottom', '${safeInsetBottom}px');
                        document.documentElement.style.setProperty('--app-safe-area-inset-left', '${safeInsetLeft}px');
                        document.documentElement.style.setProperty('--app-safe-area-inset-right', '${safeInsetRight}px');
    """.trimIndent()
    Timber.e("Safe area is $safeAreaJs")

    evaluateJavascript(safeAreaJs, null)
}
