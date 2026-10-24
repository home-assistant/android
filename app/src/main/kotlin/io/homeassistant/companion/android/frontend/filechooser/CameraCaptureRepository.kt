package io.homeassistant.companion.android.frontend.filechooser

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.util.fileProviderAuthority
import java.io.File
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Must match the `cache-path` entry in `provider_paths.xml`. */
@VisibleForTesting
const val CAPTURE_DIRECTORY = "file_chooser_captures"
private const val CAPTURE_PREFIX = "capture_"

/** Long enough that the page has submitted or dropped a photo or video it was handed. */
private val CAPTURE_MAX_AGE = 1.days

/**
 * Creates the files a camera app writes photos and videos to.
 *
 * Files live in the app cache, so the system can reclaim them, and are shared through the app
 * `FileProvider`.
 */
internal class CameraCaptureRepository @VisibleForTesting constructor(
    private val context: Context,
    private val clock: Clock,
    private val backgroundDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(@ApplicationContext context: Context, clock: Clock) : this(context, clock, Dispatchers.IO)

    /** `true` when the device has a camera to capture with. */
    val hasCamera: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    /** Creates an empty file for the [kind] of media and returns its content URI. */
    suspend fun createFile(kind: CaptureKind): Uri = withContext(backgroundDispatcher) {
        val directory = File(context.cacheDir, CAPTURE_DIRECTORY).apply { mkdirs() }
        val file = File.createTempFile(CAPTURE_PREFIX, kind.suffix, directory)
        FileProvider.getUriForFile(context, context.fileProviderAuthority, file)
    }

    /** `true` when a camera app wrote to the file at [uri], created by [createFile]. */
    suspend fun hasContent(uri: Uri): Boolean = withContext(backgroundDispatcher) {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst() && sizeIndex >= 0 && !cursor.isNull(sizeIndex) && cursor.getLong(sizeIndex) > 0
        } == true
    }

    /** Deletes a file created by [createFile]. */
    suspend fun delete(uri: Uri) {
        withContext(backgroundDispatcher) { context.contentResolver.delete(uri, null, null) }
    }

    /**
     * Deletes files created by [createFile] more than a day ago. Recent files are kept, as a
     * camera may still write to them or the page may still have to upload them.
     */
    suspend fun deleteStale() {
        withContext(backgroundDispatcher) {
            val staleBefore = clock.now() - CAPTURE_MAX_AGE
            File(context.cacheDir, CAPTURE_DIRECTORY).listFiles()
                ?.filter { Instant.fromEpochMilliseconds(it.lastModified()) < staleBefore }
                ?.forEach { it.delete() }
        }
    }
}
