package io.homeassistant.companion.android.frontend.filechooser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.homeassistant.companion.android.common.util.launchCatching

/**
 * Composable effect that handles file uploads from the WebView.
 *
 * Registers an activity result launcher for the file chooser and automatically launches it when a
 * [FileChooserRequest] is pending. The outcome is delivered to [FileChooserRequest.onResult].
 *
 * @param pendingRequest The current file chooser request, or null if none
 */
@Composable
internal fun FileChooserEffect(pendingRequest: FileChooserRequest?) {
    var currentRequest by remember { mutableStateOf<FileChooserRequest?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ShowWebFileChooser(),
        onResult = { result ->
            currentRequest?.onResult(result)
            currentRequest = null
        },
    )

    if (pendingRequest != null) {
        LaunchedEffect(pendingRequest) {
            currentRequest = pendingRequest
            val input = pendingRequest.input
            // Without a usable camera app, fall back to the picker so the page can still get a file.
            val launched = launcher.launchCatching(input) ||
                (input.cameraCapture != null && launcher.launchCatching(input.withoutCamera()))
            if (!launched) {
                currentRequest = null
                pendingRequest.onResult(FileChooserResult.Cancelled)
            }
        }
    }
}

private fun FileChooserInput.withoutCamera(): FileChooserInput = copy(cameraCapture = null)
