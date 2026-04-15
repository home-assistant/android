package io.homeassistant.companion.android.frontend.download

import android.app.DownloadManager
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.net.toUri
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.js.FrontendJsBridge.Companion.externalBusCallback
import io.homeassistant.companion.android.frontend.session.ServerSessionManager
import io.homeassistant.companion.android.util.DataUriDownloadManager
import io.homeassistant.companion.android.util.sensitive
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/**
 * Manages file downloads triggered by the WebView.
 *
 * Handles four URI schemes:
 * - `blob:` — Injects JavaScript that reads the blob and sends it back as a data URI
 *   via the external bus (arriving as a [io.homeassistant.companion.android.frontend.externalbus.incoming.HandleBlobMessage])
 * - `http/https:` — Delegates to the system [DownloadManager] with auth headers and cookies
 * - `data:` — Saves the inline data URI to the Downloads directory via [DataUriDownloadManager]
 * - Other — Returns [DownloadResult.OpenWithSystem] for the ViewModel to handle
 *
 * The [handleBlob] method is called by the [io.homeassistant.companion.android.frontend.handler.FrontendMessageHandler]
 * when blob data arrives through the external bus.
 */
@ViewModelScoped
class FrontendDownloadManager @Inject constructor(
    private val systemDownloadManager: DownloadManager?,
    private val dataUriDownloadManager: DataUriDownloadManager,
    private val sessionManager: ServerSessionManager,
    private val externalBusRepository: FrontendExternalBusRepository,
) {

    /**
     * Dispatches a download request based on the URI scheme.
     *
     * Called by the WebView download listener when the user initiates a download.
     *
     * @param url The URL of the file to download
     * @param contentDisposition The Content-Disposition header value
     * @param mimetype The MIME type of the file
     * @param serverId The current server ID for attaching auth credentials
     * @return The result of the download operation for the ViewModel to act on
     */
    suspend fun downloadFile(url: String, contentDisposition: String, mimetype: String, serverId: Int): DownloadResult {
        Timber.d("WebView requested download of ${sensitive(url)}")
        val uri = url.toUri()
        return when (uri.scheme?.lowercase()) {
            "blob" -> triggerBlobDownload(url = url, contentDisposition = contentDisposition, mimetype = mimetype)
            "http", "https" -> downloadViaDownloadManager(
                url = url,
                contentDisposition = contentDisposition,
                mimetype = mimetype,
                serverId = serverId,
            )

            "data" -> {
                dataUriDownloadManager.saveDataUri(url = url, mimetype = mimetype)
                DownloadResult.Forwarded
            }

            else -> DownloadResult.OpenWithSystem(uri = uri)
        }
    }

    /**
     * Handles a blob download that was read by the JavaScript fetch in [triggerBlobDownload].
     *
     * The fetch reads the blob as a data URL and sends it back through the external bus
     * as a [io.homeassistant.companion.android.frontend.externalbus.incoming.HandleBlobMessage].
     * This method saves that data URL to the Downloads directory.
     *
     * @param data The blob content encoded as a data URI
     * @param filename The filename from the anchor element, or null
     * @return The result of the save operation
     */
    suspend fun handleBlob(data: String, filename: String?): DownloadResult {
        dataUriDownloadManager.saveDataUri(
            url = data,
            mimetype = "",
            filename = filename,
        )
        return DownloadResult.Forwarded
    }

    private suspend fun downloadViaDownloadManager(
        url: String,
        contentDisposition: String,
        mimetype: String,
        serverId: Int,
    ): DownloadResult {
        if (systemDownloadManager == null) {
            Timber.e("Unable to start download, cannot get DownloadManager")
            return DownloadResult.Error(messageResId = commonR.string.downloads_failed)
        }
        withContext(Dispatchers.IO) {
            val uri = url.toUri()
            val request = DownloadManager.Request(uri)
                .setMimeType(mimetype)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimetype),
                )

            try {
                if (sessionManager.canSafelySendCredentials(serverId = serverId, url = url)) {
                    sessionManager.getAuthorizationHeader(serverId)?.let { authHeader ->
                        request.addRequestHeader("Authorization", authHeader)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unable to prepare request")
            }

            try {
                CookieManager.getInstance().getCookie(url)?.let { cookie ->
                    request.addRequestHeader("Cookie", cookie)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Cannot get cookies, probably not relevant
                Timber.w(e, "Cookie header not set for download request")
            }

            systemDownloadManager.enqueue(request)
        }
        return DownloadResult.Forwarded
    }

    /**
     * Triggers a blob download by fetching the blob data via the Fetch API and sending it back
     * through the external bus as a data URI for [handleBlob] to process.
     * Requires the blob URL to still be valid (not yet revoked by the frontend).
     */
    private suspend fun triggerBlobDownload(url: String, contentDisposition: String, mimetype: String): DownloadResult {
        Timber.d("Triggering blob download for ${sensitive(url)}")
        val fallbackFilename = withContext(Dispatchers.IO) {
            URLUtil.guessFileName(url, contentDisposition, mimetype)
        }
        val safeUrl = JSONObject.quote(url)
        val safeFallbackFilename = JSONObject.quote(fallbackFilename)
        val blobCallback = externalBusCallback(
            jsonPayload = "{type:'handleBlob',data:reader.result,filename:$safeFallbackFilename}",
        )
        val jsCode = """
                (async function() {
                    try {
                        const response = await fetch($safeUrl);
                        if (!response.ok) {
                            console.error('Blob download failed: HTTP ' + response.status + ' for ${
            sensitive(safeUrl)
        }');
                            return;
                        }
                        const blob = await response.blob();
                        const reader = new FileReader();
                        reader.onloadend = $blobCallback;
                        reader.readAsDataURL(blob);
                    } catch (e) {
                        console.error('Blob download failed:', e);
                    }
                })();
        """.trimIndent()
        externalBusRepository.evaluateScript(jsCode)
        return DownloadResult.Forwarded
    }
}
