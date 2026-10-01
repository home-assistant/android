package io.homeassistant.companion.android.frontend.filechooser

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.result.contract.ActivityResultContract
import java.util.Locale

private const val ANY_MIME_TYPE = "*/*"
private const val EXTENSION_PREFIX = "."
private const val MIME_TYPE_SEPARATOR = "/"

/**
 * What to launch for an `<input type="file">`.
 *
 * @param params The WebView file chooser parameters
 * @param cameraCapture How the camera is involved, or `null` to only show the file picker
 */
internal data class FileChooserInput(val params: FileChooserParams, val cameraCapture: CameraCapture?)

/** How the camera is involved in a file chooser, writing the photo to [outputUri]. */
internal sealed interface CameraCapture {
    /** Content URI of the file the camera app writes the photo to. */
    val outputUri: Uri

    /** Opens the camera directly, as the page requested with the `capture` attribute. */
    data class Direct(override val outputUri: Uri) : CameraCapture

    /** Offers the camera next to the file picker, as the page also accepts other types than images. */
    data class Offered(override val outputUri: Uri) : CameraCapture
}

/** Outcome of a file chooser launched with [ShowWebFileChooser]. */
internal sealed interface FileChooserResult {
    /** The user selected [uris]. */
    data class Selected(val uris: List<Uri>) : FileChooserResult

    /**
     * Completed without returning any URI, which is how a camera app reports a photo written to
     * [CameraCapture.outputUri].
     */
    data object Captured : FileChooserResult

    /** The user cancelled. */
    data object Cancelled : FileChooserResult
}

/**
 * Launches the system file picker for an `<input type="file">`, optionally with the camera.
 *
 * [FileChooserParams.createIntent] and [FileChooserParams.parseResult] are not used: the former
 * only applies the first `accept` entry as MIME type (an extension like `.pdf` then matches
 * nothing) and the latter ignores [Intent.getClipData], dropping multi-selections
 * (https://github.com/home-assistant/android/issues/7548).
 */
internal class ShowWebFileChooser : ActivityResultContract<FileChooserInput, FileChooserResult>() {

    override fun createIntent(context: Context, input: FileChooserInput): Intent {
        return when (val capture = input.cameraCapture) {
            null -> input.params.toPickerIntent()
            is CameraCapture.Direct -> capture.toCaptureIntent()
            is CameraCapture.Offered -> Intent.createChooser(input.params.toPickerIntent(), input.params.title)
                .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(capture.toCaptureIntent()))
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): FileChooserResult {
        if (resultCode != Activity.RESULT_OK) return FileChooserResult.Cancelled
        val clipUris = intent?.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }
        val uris = if (!clipUris.isNullOrEmpty()) clipUris else listOfNotNull(intent?.data)
        return if (uris.isEmpty()) FileChooserResult.Captured else FileChooserResult.Selected(uris)
    }
}

/**
 * Converts the `accept` attribute values (MIME types or file extensions) into MIME types.
 *
 * Returns `null` when the picker should not filter: nothing is accepted explicitly, everything is
 * accepted, or an entry can't be converted (filtering would hide files the page accepts).
 */
internal fun FileChooserParams.acceptedMimeTypes(): List<String>? {
    val entries = acceptTypes.orEmpty().map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
    if (entries.isEmpty() || ANY_MIME_TYPE in entries) return null
    val mimeTypes = entries.mapNotNull { it.toMimeType() }
    return mimeTypes.takeIf { it.size == entries.size }?.distinct()
}

private fun FileChooserParams.toPickerIntent(): Intent = Intent(Intent.ACTION_GET_CONTENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = ANY_MIME_TYPE
    acceptedMimeTypes()?.let { putExtra(Intent.EXTRA_MIME_TYPES, it.toTypedArray()) }
    if (mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }
}

/**
 * The output URI is also set as clip data, since URI permissions are only granted for the data
 * and clip data of an intent, not for extras.
 */
private fun CameraCapture.toCaptureIntent(): Intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
    putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
    clipData = ClipData.newRawUri(null, outputUri)
    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

private fun String.toMimeType(): String? = when {
    startsWith(EXTENSION_PREFIX) -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(removePrefix(EXTENSION_PREFIX))
    contains(MIME_TYPE_SEPARATOR) -> this
    else -> null
}
