package io.homeassistant.companion.android.frontend.download

import android.net.Uri
import androidx.annotation.StringRes

/**
 * Result of a download operation initiated by the WebView.
 *
 * The ViewModel uses this to decide what UI feedback to show (e.g. snackbar) or
 * what system action to take (e.g. opening a URI with [android.content.Intent.ACTION_VIEW]).
 */
sealed interface DownloadResult {

    /**
     * The download was started or completed successfully.
     *
     * For system [android.app.DownloadManager] downloads this means the request was enqueued;
     * for data URI downloads the file was saved to the Downloads directory.
     */
    data object Success : DownloadResult

    /**
     * The download was dispatched via JavaScript (blob downloads).
     *
     * The actual download result will arrive later via the JS bridge [handleBlob] callback.
     * No immediate UI feedback is needed.
     */
    data object Dispatched : DownloadResult

    /**
     * The URI scheme is not directly supported. The ViewModel should emit a
     * [FrontendEvent.OpenExternalLink][io.homeassistant.companion.android.frontend.navigation.FrontendEvent.OpenExternalLink]
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
