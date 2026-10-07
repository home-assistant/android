package io.homeassistant.companion.android.frontend.filechooser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.result.contract.ActivityResultContract
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
 * Launches the system file picker for an `<input type="file">` and returns the selected URIs,
 * or `null` if the user cancelled.
 *
 * [FileChooserParams.createIntent] and [FileChooserParams.parseResult] are not used: the former
 * only applies the first `accept` entry as MIME type (an extension like `.pdf` then matches
 * nothing) and the latter ignores [Intent.getClipData], dropping multi-selections
 * (https://github.com/home-assistant/android/issues/7548).
 */
internal class ShowWebFileChooser : ActivityResultContract<FileChooserInput, Array<Uri>?>() {

    override fun createIntent(context: Context, input: FileChooserInput): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = ANY_MIME_TYPE
            input.pickerMimeTypes?.let { putExtra(Intent.EXTRA_MIME_TYPES, it.toTypedArray()) }
            if (input.params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        val clipUris = intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }
        return if (!clipUris.isNullOrEmpty()) clipUris.toTypedArray() else intent.data?.let { arrayOf(it) }
    }
}

/**
 * Converts the `accept` attribute values (MIME types or file extensions) into MIME types.
 *
 * | Entry                                            | Example              | Result              |
 * |--------------------------------------------------|----------------------|---------------------|
 * | Extension known to Android                       | `.pdf`               | `application/pdf`   |
 * | Extension unknown to Android                     | `.backup`            | [UNKNOWN_MIME_TYPE] |
 * | MIME type known to Android, or a wildcard type   | `text/plain`         | Kept                |
 * | MIME type unknown to Android                     | `application/custom` | [UNKNOWN_MIME_TYPE] |
 * | Neither (invalid per the HTML spec)              | `png`                | Ignored             |
 *
 * Unknown entries become [UNKNOWN_MIME_TYPE], the type document providers report for such files, so
 * third-party integrations using custom extensions or types can still pick their files.
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
    entries.mapNotNull { it.toMimeType() }.distinct().takeIf { it.isNotEmpty() }
}

/** Returns the MIME type for an `accept` entry, or `null` if it's neither an extension nor a MIME type. */
private fun String.toMimeType(): String? {
    val mimeTypeMap = MimeTypeMap.getSingleton()
    return when {
        startsWith(EXTENSION_PREFIX) ->
            mimeTypeMap.getMimeTypeFromExtension(removePrefix(EXTENSION_PREFIX)) ?: UNKNOWN_MIME_TYPE
        !contains(MIME_TYPE_SEPARATOR) -> null
        endsWith(WILDCARD_SUBTYPE) || mimeTypeMap.hasMimeType(this) -> this
        else -> UNKNOWN_MIME_TYPE
    }
}
