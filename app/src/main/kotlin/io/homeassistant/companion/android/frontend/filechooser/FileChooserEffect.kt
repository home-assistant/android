package io.homeassistant.companion.android.frontend.filechooser

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.homeassistant.companion.android.webview.ShowWebFileChooser

/**
 * Pending file chooser request from the WebView.
 *
 * @param filePathCallback The WebView's callback to receive selected file URIs
 * @param fileChooserParams Parameters for configuring the file chooser intent
 */
internal data class FileChooserRequest(
    val filePathCallback: ValueCallback<Array<Uri>>,
    val fileChooserParams: FileChooserParams,
)

/**
 * Composable effect that handles file uploads from the WebView.
 *
 * Registers an activity result launcher for the system file picker and automatically
 * launches it when a [FileChooserRequest] is pending. The selected URIs (or null if
 * cancelled) are delivered back to the WebView via [ValueCallback.onReceiveValue].
 *
 * @param pendingRequest The current file chooser request, or null if none
 * @param onRequestHandled Called after the result is delivered to clear the pending request
 */
@Composable
internal fun FileChooserEffect(pendingRequest: FileChooserRequest?, onRequestHandled: () -> Unit) {
    var currentCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ShowWebFileChooser(),
        onResult = { uris ->
            currentCallback?.onReceiveValue(uris)
            currentCallback = null
            onRequestHandled()
        },
    )

    if (pendingRequest != null) {
        LaunchedEffect(pendingRequest) {
            currentCallback = pendingRequest.filePathCallback
            launcher.launch(pendingRequest.fileChooserParams)
        }
    }
}
