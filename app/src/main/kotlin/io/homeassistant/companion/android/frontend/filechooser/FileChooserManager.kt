package io.homeassistant.companion.android.frontend.filechooser

import android.net.Uri
import android.webkit.WebChromeClient.FileChooserParams
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.util.SingleSlotQueue
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Pending file chooser request from the WebView.
 *
 * @param fileChooserParams Parameters for configuring the file chooser intent
 * @param onResult Delivers the selected URIs (or `null` if the user cancelled). This callback
 *        should be called at most once. The [FileChooserManager] uses the first invocation to
 *        complete its suspend `pickFiles` call and free the slot automatically. Any subsequent
 *        invocations are ignored by the queue.
 */
internal data class FileChooserRequest(val fileChooserParams: FileChooserParams, val onResult: (Array<Uri>?) -> Unit)

/**
 * Owns the lifetime of WebView file-chooser requests.
 *
 * Only one chooser can be pending at a time. A second [pickFiles] call suspends until the user
 * has responded to the first, so callers can dispatch a request without first checking whether
 * one is already in flight and the previous request's result delivery is never silently dropped.
 *
 * [pickFiles] constructs the [FileChooserRequest], emits it to the queue, suspends until the user
 * responds, frees the slot, and returns the selected URIs (or `null` on cancel). Callers therefore
 * never see the queue plumbing.
 */
@ViewModelScoped
internal class FileChooserManager @Inject constructor() {

    private val queue = SingleSlotQueue<FileChooserRequest>()

    /** The current pending file chooser request, or `null` if none. */
    val pendingFileChooser: StateFlow<FileChooserRequest?> = queue

    /**
     * Launches a file chooser for the given [params] and suspends until the user responds.
     *
     * Returns the selected URIs, or `null` if the user cancelled. The slot is freed before
     * returning, including on cancellation of the calling coroutine.
     */
    suspend fun pickFiles(params: FileChooserParams): Array<Uri>? {
        return queue.awaitResult { onResult ->
            FileChooserRequest(
                fileChooserParams = params,
                onResult = onResult,
            )
        }
    }
}
