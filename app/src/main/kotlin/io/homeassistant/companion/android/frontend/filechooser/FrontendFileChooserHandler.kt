package io.homeassistant.companion.android.frontend.filechooser

import android.net.Uri
import android.webkit.WebChromeClient.FileChooserParams
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.util.SingleSlotQueue
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

private const val IMAGE_MIME_TYPE_PREFIX = "image/"

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
 * When the page sets the `capture` attribute and accepts images, the camera is opened directly, or
 * offered next to the file picker if other types are accepted too. Without the camera permission
 * only the file picker is shown.
 */
@ViewModelScoped
internal class FrontendFileChooserHandler @Inject constructor(
    private val cameraCaptureRepository: CameraCaptureRepository,
    private val permissionManager: PermissionManager,
) {

    private val queue = SingleSlotQueue<FileChooserRequest>()

    /** The current pending file chooser request, or `null` if none. */
    val pendingFileChooser: StateFlow<FileChooserRequest?> = queue

    /**
     * Launches a file chooser for the given [params] and suspends until the user responds.
     *
     * Returns the selected URIs, or `null` if the user cancelled. The slot is
     * freed before returning, including on cancellation of the calling coroutine.
     */
    suspend fun pickFiles(params: FileChooserParams): Array<Uri>? {
        val cameraCapture = createCameraCaptureIfRequested(params)
        val result = queue.awaitResult { onResult ->
            FileChooserRequest(FileChooserInput(params, cameraCapture), onResult)
        }
        val uris = when (result) {
            is FileChooserResult.Selected -> result.uris.toTypedArray()
            FileChooserResult.Captured -> cameraCapture?.let { arrayOf(it.outputUri) }
            FileChooserResult.Cancelled -> null
        }
        if (cameraCapture != null && uris?.contains(cameraCapture.outputUri) != true) {
            cameraCaptureRepository.delete(cameraCapture.outputUri)
        }
        return uris
    }

    /**
     * Returns the [CameraCapture] to use when the page requested the camera with `capture` and the
     * device can take the photo, or `null` to only show the file picker.
     */
    private suspend fun createCameraCaptureIfRequested(params: FileChooserParams): CameraCapture? {
        val toCameraCapture = params.requestedCameraCapture()
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
     * Returns how the page requested the camera, as the [CameraCapture] constructor to apply to the
     * output file, or `null` if it didn't request it (no `capture` or no image accepted) or the
     * device has no camera.
     */
    private fun FileChooserParams.requestedCameraCapture(): ((Uri) -> CameraCapture)? {
        val canCapture = isCaptureEnabled && cameraCaptureRepository.hasCamera
        val mimeTypes = acceptedMimeTypes()?.takeIf { canCapture } ?: return null
        return when (mimeTypes.count { it.startsWith(IMAGE_MIME_TYPE_PREFIX) }) {
            0 -> null
            mimeTypes.size -> CameraCapture::Direct
            else -> CameraCapture::Offered
        }
    }
}
