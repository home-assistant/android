package io.homeassistant.companion.android.frontend.filechooser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
 */
internal data class FileChooserInput(val params: FileChooserParams, val pickerMimeTypes: List<String>?)

/**
 * Launches the system file picker for an `<input type="file">` or a File System Access API call and
 * returns the selected URIs, or `null` if the user cancelled.
 *
 * [FileChooserParams.createIntent] and [FileChooserParams.parseResult] are not used: the former
 * only applies the first `accept` entry as MIME type (an extension like `.pdf` then matches
 * nothing) and the latter ignores [Intent.getClipData], dropping multi-selections
 * (https://github.com/home-assistant/android/issues/7548).
 */
internal class ShowWebFileChooser : ActivityResultContract<FileChooserInput, Array<Uri>?>() {

    // Mirrors the intent actions of WebView's own implementation:
    // https://source.chromium.org/chromium/chromium/src/+/main:android_webview/java/src/org/chromium/android_webview/AwContentsClient.java;l=554;drc=b1a4f4802448a816b169dcf03ef43cb8ec849d8e
    override fun createIntent(context: Context, input: FileChooserInput): Intent = when (input.params.mode) {
        FileChooserParams.MODE_OPEN_FOLDER -> Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        FileChooserParams.MODE_SAVE -> input.toDocumentIntent(Intent.ACTION_CREATE_DOCUMENT).apply {
            // The created document needs a concrete type, the first accepted one like WebView.
            type = input.pickerMimeTypes?.firstOrNull() ?: ANY_MIME_TYPE
            input.params.filenameHint?.let { putExtra(Intent.EXTRA_TITLE, it) }
        }
        else -> input.toDocumentIntent(
            if (input.params.opensWritable()) Intent.ACTION_OPEN_DOCUMENT else Intent.ACTION_GET_CONTENT,
        )
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        val clipUris = intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }
        return if (!clipUris.isNullOrEmpty()) clipUris.toTypedArray() else intent.data?.let { arrayOf(it) }
    }
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
 * Returns `null` when the picker should not filter: nothing valid is accepted or any file type is
 * accepted.
 *
 * Runs on [dispatcher], as the system MIME table is read from disk on first use.
 */
internal suspend fun FileChooserParams.acceptedMimeTypes(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): List<String>? = withContext(dispatcher) {
    val entries = acceptTypes.orEmpty().map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
    if (entries.isEmpty() || ANY_MIME_TYPE in entries) return@withContext null
    entries.flatMap { it.toMimeTypes() }.distinct().takeIf { it.isNotEmpty() }
}

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
