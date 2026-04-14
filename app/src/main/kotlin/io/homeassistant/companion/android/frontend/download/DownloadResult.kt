package io.homeassistant.companion.android.frontend.download

import android.net.Uri
import androidx.annotation.StringRes
import io.homeassistant.companion.android.util.DataUriDownloadManager

/**
 * Result of a download operation initiated by the WebView.
 *
 * The ViewModel uses this to decide what UI feedback to show (e.g. snackbar) or
 * what system action to take (e.g. opening a URI with [android.content.Intent.ACTION_VIEW]).
 */
sealed interface DownloadResult {

    /**
     * The download was handled, it might have failed but this has been handle properly by the system or the [DataUriDownloadManager].
     *
     * For system [android.app.DownloadManager] downloads this means the request was enqueued;
     * for data URI downloads the file was saved to the Downloads directory.
     */
    data object Handled : DownloadResult

    /**
     * The download was dispatched via JavaScript (blob downloads).
     *
     * The actual download result will arrive later as a [io.homeassistant.companion.android.frontend.externalbus.incoming.HandleBlobMessage]
     * through the external bus. No immediate UI feedback is needed.
     */
    data object Dispatched : DownloadResult

    /**
     * The URI scheme is not directly supported. The ViewModel should emit a [io.homeassistant.companion.android.frontend.navigation.FrontendEvent.OpenExternalLink]
     * event to open this URI externally.
     */
    data class OpenWithSystem(val uri: Uri) : DownloadResult

    /**
     * The download failed. The ViewModel should show an error message to the user.
     *
     * @param messageResId String resource ID for the error message
     */
    data class Error(@StringRes val messageResId: Int) : DownloadResult
}
