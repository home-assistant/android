package io.homeassistant.companion.android.frontend.filechooser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.result.contract.ActivityResultContract
import java.util.Locale

private const val ANY_MIME_TYPE = "*/*"
private const val EXTENSION_PREFIX = "."
private const val MIME_TYPE_SEPARATOR = "/"

/**
 * Launches the system file picker for an `<input type="file">` and returns the selected URIs,
 * or `null` if the user cancelled.
 *
 * [FileChooserParams.createIntent] and [FileChooserParams.parseResult] are not used: the former
 * only applies the first `accept` entry as MIME type (an extension like `.pdf` then matches
 * nothing) and the latter ignores [Intent.getClipData], dropping multi-selections
 * (https://github.com/home-assistant/android/issues/7548).
 */
internal class ShowWebFileChooser : ActivityResultContract<FileChooserParams, Array<Uri>?>() {

    override fun createIntent(context: Context, input: FileChooserParams): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = ANY_MIME_TYPE
            input.acceptTypes.toMimeTypes()?.let { putExtra(Intent.EXTRA_MIME_TYPES, it) }
            if (input.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
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
 */
private fun Array<String>?.toMimeTypes(): Array<String>? {
    val entries = orEmpty().map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
    if (entries.isEmpty() || ANY_MIME_TYPE in entries) return null
    val mimeTypes = entries.mapNotNull { it.toMimeType() }
    return mimeTypes.takeIf { it.size == entries.size }?.distinct()?.toTypedArray()
}

private fun String.toMimeType(): String? = when {
    startsWith(EXTENSION_PREFIX) -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(removePrefix(EXTENSION_PREFIX))
    contains(MIME_TYPE_SEPARATOR) -> this
    else -> null
}
