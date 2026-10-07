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
 * Returns `null` when the picker should not filter: nothing is accepted explicitly, everything is
 * accepted, or an entry can't be converted (filtering would hide files the page accepts).
 *
 * Runs on [dispatcher], as the system MIME table is read from disk on first use.
 */
internal suspend fun FileChooserParams.acceptedMimeTypes(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): List<String>? = withContext(dispatcher) {
    val entries = acceptTypes.orEmpty().map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
    if (entries.isEmpty() || ANY_MIME_TYPE in entries) return@withContext null
    val mimeTypes = entries.mapNotNull { it.toMimeType() }
    mimeTypes.takeIf { it.size == entries.size }?.distinct()
}

private fun String.toMimeType(): String? = when {
    startsWith(EXTENSION_PREFIX) -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(removePrefix(EXTENSION_PREFIX))
    contains(MIME_TYPE_SEPARATOR) -> this
    else -> null
}
