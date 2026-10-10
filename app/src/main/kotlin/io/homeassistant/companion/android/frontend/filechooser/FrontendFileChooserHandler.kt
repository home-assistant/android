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

private const val IMAGE_MIME_TYPE_PREFIX = "image/"

/** Modes where the page opens existing files, the only ones where taking a photo makes sense. */
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
 * When the page accepts images, the camera is offered next to the file picker, or opened directly
 * when the page also sets the `capture` attribute and only accepts images. Without the camera
 * permission only the file picker is shown.
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
     * Returns the selected URIs, or `null` if the user cancelled. The slot is freed and an unused
     * capture file deleted before returning, including on cancellation of the calling coroutine.
     */
    suspend fun pickFiles(params: FileChooserParams): Array<Uri>? {
        cameraCaptureRepository.deleteStale()
        val acceptedMimeTypes = params.acceptedMimeTypes(backgroundDispatcher)
        val cameraCapture = createCameraCaptureIfImagesAccepted(params, acceptedMimeTypes)
        var uris: Array<Uri>? = null
        try {
            val input = FileChooserInput(params, acceptedMimeTypes.toPickerMimeTypes(), cameraCapture)
            val result = queue.awaitResult { onResult -> FileChooserRequest(input, onResult) }
            uris = when (result) {
                is FileChooserResult.Selected -> result.uris.toTypedArray()
                FileChooserResult.Captured -> cameraCapture?.let { arrayOf(it.outputUri) }
                FileChooserResult.Cancelled -> null
            }
            return uris
        } finally {
            if (cameraCapture != null && uris?.contains(cameraCapture.outputUri) != true) {
                // NonCancellable so the deletion also runs when the calling coroutine is cancelled.
                withContext(NonCancellable) { cameraCaptureRepository.delete(cameraCapture.outputUri) }
            }
        }
    }

    /**
     * Returns the [CameraCapture] to use when the page accepts images and the device can take the
     * photo, or `null` to only show the file picker.
     */
    private suspend fun createCameraCaptureIfImagesAccepted(
        params: FileChooserParams,
        acceptedMimeTypes: List<String>,
    ): CameraCapture? {
        val toCameraCapture = params.cameraCaptureForAcceptedTypes(acceptedMimeTypes)
            ?.takeIf { permissionManager.checkCameraPermission() }
            ?: return null
        return try {
            toCameraCapture(cameraCaptureRepository.createImageFile())
        } catch (e: IOException) {
            Timber.e(e, "Failed to create camera capture file, only showing the file picker")
            null
        }
    }

    /**
     * Returns how to involve the camera, as the [CameraCapture] constructor to apply to the output
     * file, or `null` if [mimeTypes] contain no image, the page doesn't open files (saving or picking a
     * folder) or the device has no camera.
     */
    private fun FileChooserParams.cameraCaptureForAcceptedTypes(mimeTypes: List<String>): ((Uri) -> CameraCapture)? {
        if (mode !in OPEN_MODES || !cameraCaptureRepository.hasCamera) return null
        val imageTypeCount = mimeTypes.count { it.startsWith(IMAGE_MIME_TYPE_PREFIX) }
        return when {
            imageTypeCount == 0 -> null
            isCaptureEnabled && imageTypeCount == mimeTypes.size -> CameraCapture::Direct
            else -> CameraCapture::Offered
        }
    }
}
