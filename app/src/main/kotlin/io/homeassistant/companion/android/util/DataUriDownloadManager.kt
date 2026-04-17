package io.homeassistant.companion.android.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.CHANNEL_DOWNLOADS
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class DataUriDownloadManager @Inject constructor(@param:ApplicationContext private val context: Context) {

    /**
     * Decodes a data URI, writes the content to the Downloads directory, and shows a notification
     * indicating success or failure.
     *
     * @param url The data URI to save
     * @param mimetype The MIME type of the content (falls back to extracting from the URI if blank)
     * @param filename Optional filename; a timestamped name is generated if null or blank
     */
    suspend fun saveDataUri(url: String, mimetype: String, filename: String? = null) {
        val mime = mimetype.ifBlank {
            url.removePrefix("data:").split(";")[0].ifBlank {
                "text/plain"
            }
        }
        val result = writeDataUriToFile(url, mime, filename)

        createNotificationChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setContentTitle(filename ?: context.getString(commonR.string.downloads_unnamed_file))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
        if (result != null) {
            notification.setContentText(context.getString(commonR.string.downloads_complete))

            val sendIntent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setDataAndType(result, mime)
            }
            notification.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    sendIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            notification.setContentText(context.getString(commonR.string.downloads_failed))
        }

        NotificationManagerCompat.from(context)
            .notify(url.hashCode(), notification.build())
    }

    private suspend fun writeDataUriToFile(url: String, mimetype: String, filename: String?): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val decodedBytes = if (url.split(",")[0].endsWith("base64")) {
                    val base64EncodedString = url.substring(url.indexOf(",") + 1)
                    Base64.decode(base64EncodedString, Base64.DEFAULT)
                } else {
                    Uri.decode(url.substring(url.indexOf(",") + 1)).toByteArray()
                }

                val fileName = if (!filename.isNullOrBlank()) {
                    filename
                } else {
                    // URLUtil doesn't handle data URIs correctly, so we have to use a generic filename
                    var generated = "Home Assistant ${SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault(),
                    ).format(Date())}"
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype)?.let { extension ->
                        generated += ".$extension"
                    }
                    generated
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, mimetype)
                        put(MediaStore.Downloads.IS_PENDING, true)
                    }
                    val dataFile = context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        contentValues,
                    )
                    dataFile?.let {
                        context.contentResolver.openOutputStream(it)?.use { output ->
                            output.write(decodedBytes)
                        }?.also {
                            contentValues.put(MediaStore.Downloads.IS_PENDING, false)
                            context.contentResolver.update(dataFile, contentValues, null, null)
                        }
                    }
                    return@withContext dataFile
                } else {
                    val downloads =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            ?: return@withContext null
                    val dataFile = File("${downloads.absolutePath}/$fileName")
                    if (!dataFile.exists()) {
                        dataFile.parentFile?.mkdirs()
                        dataFile.createNewFile()
                    }

                    FileOutputStream(dataFile).use { output ->
                        output.write(decodedBytes)
                    }

                    return@withContext scanAndGetDownload(dataFile.absolutePath, mimetype)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Exception while writing file from data URI")
                return@withContext null
            }
        }

    private suspend fun scanAndGetDownload(path: String, mimetype: String) = suspendCoroutine<Uri?> { cont ->
        MediaScannerConnection.scanFile(
            context,
            arrayOf(path),
            arrayOf(mimetype),
        ) { _, uri ->
            Timber.d("Received uri from media scanner for file: $uri")
            cont.resume(uri)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = NotificationManagerCompat.from(context)
            val channel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                context.getString(commonR.string.downloads),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}
