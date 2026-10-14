package io.homeassistant.companion.android.frontend.filechooser

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.result.contract.ActivityResultContract
import io.homeassistant.companion.android.common.util.SdkVersion
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ANY_MIME_TYPE = "*/*"
private const val EXTENSION_PREFIX = "."
private const val MIME_TYPE_SEPARATOR = "/"
private const val WILDCARD_SUBTYPE = "/*"

/** Type document providers report for files whose extension Android doesn't know. */
private const val UNKNOWN_MIME_TYPE = "application/octet-stream"

/**
 * What to launch for an `<input type="file">`.
 *
 * @param params The WebView file chooser parameters
 * @param pickerMimeTypes MIME types to filter the file picker with, or `null` to show any file
 * @param cameraCapture How the camera is involved, or `null` to only show the file picker
 */
internal data class FileChooserInput(
    val params: FileChooserParams,
    val pickerMimeTypes: List<String>?,
    val cameraCapture: CameraCapture?,
)

/** How the camera is involved in a file chooser, writing the photo to [outputUri]. */
internal sealed interface CameraCapture {
    /** Content URI of the file the camera app writes the photo to. */
    val outputUri: Uri

    /** Opens the camera directly, as the page requested with the `capture` attribute. */
    data class Direct(override val outputUri: Uri) : CameraCapture

    /** Offers the camera next to the file picker. */
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
 * Launches the system file picker for an `<input type="file">` or a File System Access API call,
 * optionally with the camera.
 *
 * [FileChooserParams.createIntent] and [FileChooserParams.parseResult] are not used: the former
 * only applies the first `accept` entry as MIME type (an extension like `.pdf` then matches
 * nothing) and the latter ignores [Intent.getClipData], dropping multi-selections
 * (https://github.com/home-assistant/android/issues/7548).
 */
internal class ShowWebFileChooser : ActivityResultContract<FileChooserInput, FileChooserResult>() {

    override fun createIntent(context: Context, input: FileChooserInput): Intent {
        return when (val capture = input.cameraCapture) {
            null -> input.toPickerIntent()
            is CameraCapture.Direct -> capture.toCaptureIntent()
            is CameraCapture.Offered -> Intent.createChooser(input.toPickerIntent(), input.params.title)
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

// Mirrors the intent actions of WebView's own implementation:
// https://source.chromium.org/chromium/chromium/src/+/main:android_webview/java/src/org/chromium/android_webview/AwContentsClient.java;l=554;drc=b1a4f4802448a816b169dcf03ef43cb8ec849d8e
private fun FileChooserInput.toPickerIntent(): Intent = when (params.mode) {
    FileChooserParams.MODE_OPEN_FOLDER -> Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    FileChooserParams.MODE_SAVE -> toDocumentIntent(Intent.ACTION_CREATE_DOCUMENT).apply {
        // The created document needs a concrete type, the first accepted one like WebView.
        type = pickerMimeTypes?.firstOrNull() ?: ANY_MIME_TYPE
        params.filenameHint?.let { putExtra(Intent.EXTRA_TITLE, it) }
    }
    else -> toDocumentIntent(if (params.opensWritable()) Intent.ACTION_OPEN_DOCUMENT else Intent.ACTION_GET_CONTENT)
}

private fun FileChooserInput.toDocumentIntent(action: String): Intent = Intent(action).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = ANY_MIME_TYPE
    pickerMimeTypes?.let { putExtra(Intent.EXTRA_MIME_TYPES, it.toTypedArray()) }
    if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }
}

/** `true` when the page asked to write to the opened file, which WebView only reports since API 37. */
private fun FileChooserParams.opensWritable(): Boolean = SdkVersion.isAtLeast(Build.VERSION_CODES.CINNAMON_BUN) &&
    permissionMode == FileChooserParams.PERMISSION_MODE_READ_WRITE

/**
 * The output URI is also set as clip data, since URI permissions are only granted for the data
 * and clip data of an intent, not for extras.
 */
private fun CameraCapture.toCaptureIntent(): Intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
    putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
    clipData = ClipData.newRawUri(null, outputUri)
    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/**
 * Converts the `accept` attribute values (MIME types or file extensions) into MIME types.
 *
 * | Entry                                          | Example              | Result                        |
 * |------------------------------------------------|----------------------|-------------------------------|
 * | Extension known to Android                     | `.pdf`               | `application/pdf`             |
 * | Extension unknown to Android                   | `.backup`            | [UNKNOWN_MIME_TYPE]           |
 * | MIME type known to Android, or a wildcard type | `text/plain`         | Kept                          |
 * | MIME type unknown to Android                   | `application/custom` | Kept, and [UNKNOWN_MIME_TYPE] |
 * | Neither (invalid per the HTML spec)            | `png`                | Ignored                       |
 *
 * [UNKNOWN_MIME_TYPE] is what Android's local document providers report for files of a type they don't
 * know, and custom MIME types are kept for providers reporting them, so third-party integrations using
 * custom extensions or types can still pick their files.
 *
 * Runs on [dispatcher], as the system MIME table is read from disk on first use.
 */
internal suspend fun FileChooserParams.acceptedMimeTypes(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): List<String> = withContext(dispatcher) {
    acceptTypes.orEmpty()
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }
        .flatMap { it.toMimeTypes() }
}

/**
 * Returns the MIME types to filter the picker with, or `null` when it should not filter: nothing valid
 * is accepted or any file type is accepted.
 */
internal fun List<String>.toPickerMimeTypes(): List<String>? =
    distinct().takeIf { it.isNotEmpty() && ANY_MIME_TYPE !in it }

/** Returns the MIME types for an `accept` entry, none if it's neither an extension nor a MIME type. */
private fun String.toMimeTypes(): List<String> {
    val mimeTypeMap = MimeTypeMap.getSingleton()
    return when {
        startsWith(EXTENSION_PREFIX) ->
            listOf(mimeTypeMap.getMimeTypeFromExtension(removePrefix(EXTENSION_PREFIX)) ?: UNKNOWN_MIME_TYPE)
        !contains(MIME_TYPE_SEPARATOR) -> emptyList()
        endsWith(WILDCARD_SUBTYPE) || mimeTypeMap.hasMimeType(this) -> listOf(this)
        else -> listOf(this, UNKNOWN_MIME_TYPE)
    }
}
