package io.homeassistant.companion.android.util

import android.content.Context
import androidx.webkit.WebViewCompat
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.launchAppOrStore
import timber.log.Timber

/**
 * Opens the store/app page for the device's current WebView provider so the user can update it.
 *
 * When the provider package cannot be resolved (e.g. no updatable provider), [onShowSnackbar] surfaces the
 * failure to the user. Otherwise the provider is opened via [launchAppOrStore], which itself falls back to a
 * snackbar when nothing can handle the launch.
 */
suspend fun Context.updateSystemWebView(onShowSnackbar: suspend (message: String, action: String?) -> Boolean) {
    val webViewPackage = WebViewCompat.getCurrentWebViewPackage(this)?.packageName
    if (webViewPackage == null) {
        Timber.w("No current WebView package, cannot open update page")
        onShowSnackbar(
            getString(commonR.string.fail_to_navigate_to_uri, getString(commonR.string.system_webview)),
            null,
        )
        return
    }
    launchAppOrStore(webViewPackage, onShowSnackbar)
}
