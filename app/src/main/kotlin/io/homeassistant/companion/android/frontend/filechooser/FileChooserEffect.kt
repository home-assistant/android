package io.homeassistant.companion.android.frontend.filechooser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.homeassistant.companion.android.webview.ShowWebFileChooser

/**
 * Composable effect that handles file uploads from the WebView.
 *
 * Registers an activity result launcher for the system file picker and automatically
 * launches it when a [FileChooserRequest] is pending. The selected URIs (or `null` if
 * cancelled) are delivered to [FileChooserRequest.onResult].
 *
 * @param pendingRequest The current file chooser request, or null if none
 */
@Composable
internal fun FileChooserEffect(pendingRequest: FileChooserRequest?) {
    var currentRequest by remember { mutableStateOf<FileChooserRequest?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ShowWebFileChooser(),
        onResult = { uris ->
            currentRequest?.onResult(uris)
            currentRequest = null
        },
    )

    if (pendingRequest != null) {
        LaunchedEffect(pendingRequest) {
            currentRequest = pendingRequest
            launcher.launch(pendingRequest.fileChooserParams)
        }
    }
}
