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
     * The download was forwarded, it might have failed but this has been handled properly by the system or the [DataUriDownloadManager].
     *
     * Depending on the URI scheme this means:
     * - system [android.app.DownloadManager] downloads: the request was enqueued
     * - data URI downloads: the file was saved to the Downloads directory
     * - blob URI downloads: JavaScript was injected in the frontend to read the blob; the data then arrives
     *   later via `handleBlob` and is saved to the Downloads directory
     */
    data object Forwarded : DownloadResult

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
