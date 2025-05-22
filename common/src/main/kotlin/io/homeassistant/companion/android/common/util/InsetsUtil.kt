package io.homeassistant.companion.android.common.util

import android.os.Build
import android.view.View
import android.webkit.WebView
import androidx.core.util.TypedValueCompat.pxToDp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding

fun WebView.applyInsets(rootContainer: View, serverHandleInsets: Boolean) {
    ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { v, windowInsets ->
        val safeInsets = windowInsets.getInsets(
            systemBars() or displayCutout() or ime()
        )

        // If the server doesn't handle the insets we have to manually apply them when we are on API 36
        // since Edge to Edge is enable by default otherwise the webview is drawn bellow
        if (!serverHandleInsets && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            v.updatePadding(
                top = safeInsets.top,
                bottom = safeInsets.bottom,
                left = safeInsets.left,
                right = safeInsets.right,
            )
        } else {
            // From https://medium.com/androiddevelopers/make-webviews-edge-to-edge-a6ef319adfac
            // Convert raw pixels to density independent pixels
            val displayMetrics = resources.displayMetrics
            val safeInsetTop = pxToDp(safeInsets.top.toFloat(), displayMetrics)
            val safeInsetRight = pxToDp(safeInsets.right.toFloat(), displayMetrics)
            val safeInsetBottom = pxToDp(safeInsets.bottom.toFloat(), displayMetrics)
            val safeInsetLeft = pxToDp(safeInsets.left.toFloat(), displayMetrics)
            val safeAreaJs = """
                        document.documentElement.style.setProperty('--android-safe-area-inset-top', '${safeInsetTop}px');
                        document.documentElement.style.setProperty('--android-safe-area-inset-bottom', '${safeInsetBottom}px');
                        document.documentElement.style.setProperty('--android-safe-area-inset-left', '${safeInsetLeft}px');
                        document.documentElement.style.setProperty('--android-safe-area-inset-right', '${safeInsetRight}px');
            """.trimIndent()
            evaluateJavascript(safeAreaJs, null)
        }

        WindowInsetsCompat.CONSUMED
    }
}
