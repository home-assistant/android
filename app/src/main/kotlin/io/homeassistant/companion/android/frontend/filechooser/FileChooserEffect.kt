package io.homeassistant.companion.android.frontend.filechooser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private class ShowWebFileChooser : ActivityResultContract<WebChromeClient.FileChooserParams, Array<Uri>?>() {

    override fun createIntent(context: Context, input: WebChromeClient.FileChooserParams): Intent {
        return input.createIntent().apply {
            type = "*/*"
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Array<Uri>? {
        return WebChromeClient.FileChooserParams.parseResult(resultCode, intent)
    }
}

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
