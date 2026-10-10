package io.homeassistant.companion.android.frontend.filechooser

import android.net.Uri
import android.webkit.WebChromeClient.FileChooserParams
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.util.SingleSlotQueue
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Modes where the page opens existing files, the only ones where capturing new media makes sense. */
private val OPEN_MODES = setOf(FileChooserParams.MODE_OPEN, FileChooserParams.MODE_OPEN_MULTIPLE)

/**
 * Pending file chooser request from the WebView.
 *
 * @param input What to launch
 * @param onResult Delivers the outcome. This callback should be called at most once. The
 *        [FrontendFileChooserHandler] uses the first invocation to complete its suspend `pickFiles` call
 *        and free the slot automatically. Any subsequent invocations are ignored by the queue.
 */
internal data class FileChooserRequest(val input: FileChooserInput, val onResult: (FileChooserResult) -> Unit)

/**
 * Owns the lifetime of WebView file-chooser requests.
 *
 * Only one chooser can be pending at a time. A second [pickFiles] call suspends until the user
 * has responded to the first, so callers can dispatch a request without first checking whether
 * one is already in flight and the previous request's result delivery is never silently dropped.
 *
 * When the page accepts images or videos, taking a photo or recording a video is offered next to
 * the file picker, or opened directly when the page also sets the `capture` attribute and only
 * accepts that one kind of media. Without the camera permission only the file picker is shown.
 */
@ViewModelScoped
internal class FrontendFileChooserHandler @VisibleForTesting constructor(
    private val cameraCaptureRepository: CameraCaptureRepository,
    private val permissionManager: PermissionManager,
    private val backgroundDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(cameraCaptureRepository: CameraCaptureRepository, permissionManager: PermissionManager) :
        this(cameraCaptureRepository, permissionManager, Dispatchers.IO)

    private val queue = SingleSlotQueue<FileChooserRequest>()

    /** The current pending file chooser request, or `null` if none. */
    val pendingFileChooser: StateFlow<FileChooserRequest?> = queue

    /**
     * Launches a file chooser for the given [params] and suspends until the user responds.
     *
     * Returns the selected URIs, or `null` if the user cancelled. The slot is freed and unused
     * capture files deleted before returning, including on cancellation of the calling coroutine.
     */
    suspend fun pickFiles(params: FileChooserParams): Array<Uri>? {
        cameraCaptureRepository.deleteStale()
        val acceptedMimeTypes = params.acceptedMimeTypes(backgroundDispatcher)
        val cameraCapture = createCameraCaptureIfMediaAccepted(params, acceptedMimeTypes)
        val outputUris = cameraCapture?.outputs.orEmpty().map { it.uri }
        var uris: Array<Uri>? = null
        try {
            val input = FileChooserInput(params, acceptedMimeTypes.toPickerMimeTypes(), cameraCapture)
            val result = queue.awaitResult { onResult -> FileChooserRequest(input, onResult) }
            uris = when (result) {
                is FileChooserResult.Selected -> result.uris.toTypedArray()
                FileChooserResult.Captured -> outputUris.filter { cameraCaptureRepository.hasContent(it) }
                    .takeIf { it.isNotEmpty() }
                    ?.toTypedArray()
                FileChooserResult.Cancelled -> null
            }
            return uris
        } finally {
            val unusedUris = outputUris.filterNot { uris?.contains(it) == true }
            // NonCancellable so the deletion also runs when the calling coroutine is cancelled.
            withContext(NonCancellable) { unusedUris.forEach { cameraCaptureRepository.delete(it) } }
        }
    }

    /**
     * Returns the [CameraCapture] to use when the page opens files accepting images or videos and the
     * device can capture them, or `null` to only show the file picker.
     */
    private suspend fun createCameraCaptureIfMediaAccepted(
        params: FileChooserParams,
        acceptedMimeTypes: List<String>,
    ): CameraCapture? {
        val kinds = CaptureKind.entries.filter { kind -> acceptedMimeTypes.any(kind::matches) }
            .takeIf {
                it.isNotEmpty() &&
                    params.mode in OPEN_MODES &&
                    cameraCaptureRepository.hasCamera &&
                    permissionManager.checkCameraPermission()
            }
            ?: return null
        return createOutputs(kinds)?.let { outputs ->
            val directOutput = outputs.singleOrNull()?.takeIf { output ->
                params.isCaptureEnabled && acceptedMimeTypes.all(output.kind::matches)
            }
            if (directOutput != null) CameraCapture.Direct(directOutput) else CameraCapture.Offered(outputs)
        }
    }

    /** Creates one output per kind, or returns `null` (deleting the ones created) if any fails. */
    private suspend fun createOutputs(kinds: List<CaptureKind>): List<CaptureOutput>? {
        val outputs = mutableListOf<CaptureOutput>()
        return try {
            kinds.forEach { kind -> outputs += CaptureOutput(kind, cameraCaptureRepository.createFile(kind)) }
            outputs
        } catch (e: IOException) {
            Timber.e(e, "Failed to create camera capture file, only showing the file picker")
            outputs.forEach { cameraCaptureRepository.delete(it.uri) }
            null
        }
    }
}
