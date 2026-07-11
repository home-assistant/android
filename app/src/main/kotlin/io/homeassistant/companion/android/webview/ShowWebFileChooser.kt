package io.homeassistant.companion.android.webview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebChromeClient
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import java.io.File
import timber.log.Timber

/**
 * Bridges Chromium's `<input type="file">` chooser request into an Android
 * activity result.
 *
 * Issue [#6055](https://github.com/home-assistant/android/issues/6055):
 * the previous implementation forced `type = "*/*"` on the framework-provided
 * intent and did not expose any camera capture entry point, so a page using
 * `<input type="file" accept="image/*" capture="environment">` would only
 * ever see the system file picker (no Camera tile). On Android 14+ this
 * defaulted to the Photo Picker, which intentionally never offers a Camera
 * shortcut for capture.
 *
 * The new behaviour:
 *
 * * The intent built by [WebChromeClient.FileChooserParams.createIntent] is
 *   used verbatim — it already encodes the page's `accept` filter and the
 *   `multiple` flag, so the system can route to the Photo Picker /
 *   Documents UI as appropriate.
 * * When the page's accept filter allows images and/or video the chooser is
 *   wrapped with `EXTRA_INITIAL_INTENTS` containing
 *   [MediaStore.ACTION_IMAGE_CAPTURE] and/or
 *   [MediaStore.ACTION_VIDEO_CAPTURE] so the user sees a Camera / Camcorder
 *   tile alongside the file picker. The captured media is written to a
 *   FileProvider URI under `getExternalFilesDir(DIRECTORY_PICTURES)` —
 *   no extra runtime permissions are required (the directory belongs to
 *   this app).
 * * If the user takes a photo or video instead of picking a file the result
 *   Intent is `null` (camera apps deliver the data via the `EXTRA_OUTPUT`
 *   URI we passed in), so [parseResult] falls back to the recorded capture
 *   URI rather than treating it as a cancelled chooser.
 */
class ShowWebFileChooser : ActivityResultContract<WebChromeClient.FileChooserParams, Array<Uri>?>() {

    private var captureOutputUri: Uri? = null

    override fun createIntent(context: Context, input: WebChromeClient.FileChooserParams): Intent {
        // Reset before each launch so a stale URI from a previous chooser cannot
        // leak into the next result.
        captureOutputUri = null

        val contentIntent = input.createIntent()

        val acceptTypes = input.acceptTypes
            ?.filter { it.isNotBlank() }
            ?.map { it.lowercase() }
            .orEmpty()
        val acceptsAny = acceptTypes.isEmpty() || acceptTypes.any { it == "*/*" }
        val acceptsImage = acceptsAny ||
            acceptTypes.any { it.startsWith("image/") || it == "image" }
        val acceptsVideo = acceptsAny ||
            acceptTypes.any { it.startsWith("video/") || it == "video" }

        val initialIntents = mutableListOf<Intent>()
        if (acceptsImage) {
            createCaptureIntent(context, MediaStore.ACTION_IMAGE_CAPTURE, "jpg")?.let {
                initialIntents.add(it)
            }
        }
        if (acceptsVideo) {
            // Video captures are typically large and the camera app handles the
            // output container itself, so we let it write wherever it likes and
            // read the URI back from the result Intent like the file picker.
            initialIntents.add(Intent(MediaStore.ACTION_VIDEO_CAPTURE))
        }

        if (initialIntents.isEmpty()) return contentIntent

        return Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentIntent)
            putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toTypedArray())
        }
    }

    private fun createCaptureIntent(context: Context, action: String, extension: String): Intent? {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
            if (!dir.exists() && !dir.mkdirs()) return null
            val file = File.createTempFile("webview_capture_", ".$extension", dir)
            val authority = context.applicationContext.packageName + ".provider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            captureOutputUri = uri
            Intent(action).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Throwable) {
            // Failing to set up a capture target must not block the file
            // picker — degrade gracefully to the previous behaviour.
            Timber.w(e, "Failed to prepare camera capture intent for WebView file chooser")
            captureOutputUri = null
            null
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Array<Uri>? {
        val parsed = WebChromeClient.FileChooserParams.parseResult(resultCode, intent)
        if (parsed != null) return parsed
        val captured = captureOutputUri
        if (resultCode == Activity.RESULT_OK && captured != null) {
            return arrayOf(captured)
        }
        return null
    }
}
