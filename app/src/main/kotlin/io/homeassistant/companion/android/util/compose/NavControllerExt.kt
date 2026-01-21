package io.homeassistant.companion.android.util.compose

import androidx.compose.ui.platform.AndroidUriHandler
import androidx.navigation.NavController
import io.homeassistant.companion.android.common.R as commonR
import timber.log.Timber

fun NavController.navigateToUri(uri: String) {
    AndroidUriHandler(context).openUri(uri)
}

suspend fun NavController.navigateToUriCatching(
    uri: String,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    try {
        AndroidUriHandler(context).openUri(uri)
    } catch (e: IllegalArgumentException) {
        // Don't log e to not leak the URL in the log
        Timber.e("Failed to navigate to uri")
        onShowSnackbar(context.getString(commonR.string.fail_to_navigate_to_uri, uri), null)
    }
}
