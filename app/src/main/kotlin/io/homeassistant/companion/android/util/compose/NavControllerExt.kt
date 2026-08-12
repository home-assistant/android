package io.homeassistant.companion.android.util.compose

import androidx.navigation.NavController
import io.homeassistant.companion.android.common.util.launchAppOrStore
import io.homeassistant.companion.android.common.util.launchIntentUri
import io.homeassistant.companion.android.common.util.openSecuritySettings
import io.homeassistant.companion.android.common.util.openUri
import io.homeassistant.companion.android.util.updateSystemWebView

suspend fun NavController.navigateToUri(
    uri: String,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    context.openUri(uri, onShowSnackbar)
}

suspend fun NavController.launchAppOrStore(
    packageName: String,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    context.launchAppOrStore(packageName, onShowSnackbar)
}

suspend fun NavController.launchIntentUri(
    intentUri: String,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    context.launchIntentUri(intentUri, onShowSnackbar)
}

suspend fun NavController.openSecuritySettings(onShowSnackbar: suspend (message: String, action: String?) -> Boolean) {
    context.openSecuritySettings(onShowSnackbar)
}

suspend fun NavController.updateSystemWebView(onShowSnackbar: suspend (message: String, action: String?) -> Boolean) {
    context.updateSystemWebView(onShowSnackbar)
}
