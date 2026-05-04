package io.homeassistant.companion.android.util

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.ContentUris
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.CHANNEL_DOWNLOADS
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [Build.VERSION_CODES.Q])
class DataUriDownloadManagerTest {

    @get:Rule
    var consoleLog = ConsoleLogRule()

    private lateinit var app: Application
    private lateinit var manager: DataUriDownloadManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var contentResolver: ContentResolver

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        manager = DataUriDownloadManager(app)
        notificationManager = app.getSystemService(NotificationManager::class.java)
        contentResolver = app.contentResolver

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        // Clean up any files created during pre-Q tests
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloads?.listFiles()?.forEach { it.delete() }
    }

    // region MIME type resolution

    @Test
    fun `Given provided mimetype when saving data URI then uses provided mimetype`() = runTest {
        val data = Base64.encodeToString("hello".toByteArray(), Base64.DEFAULT)
        val url = "data:application/octet-stream;base64,$data"

        manager.saveDataUri(url = url, mimetype = "text/plain", filename = "test.txt")

        assertEquals("text/plain", getContentIntentMimeType(url))
    }

    @Test
    fun `Given blank mimetype when saving data URI then extracts mimetype from URI`() = runTest {
        val data = Base64.encodeToString("content".toByteArray(), Base64.DEFAULT)
        val url = "data:image/png;base64,$data"

        manager.saveDataUri(url = url, mimetype = "", filename = "image.png")

        assertEquals("image/png", getContentIntentMimeType(url))
    }

    @Test
    fun `Given blank mimetype and no mimetype in URI when saving data URI then defaults to text plain`() = runTest {
        val data = Base64.encodeToString("fallback".toByteArray(), Base64.DEFAULT)
        val url = "data:;base64,$data"

        manager.saveDataUri(url = url, mimetype = "", filename = "fallback.txt")

        assertEquals("text/plain", getContentIntentMimeType(url))
    }

    // endregion

    // region Data decoding

    @Test
    fun `Given base64 data URI when saving then decodes base64 content correctly`() = runTest {
        val originalContent = "Hello, World! 🏠"
        val data = Base64.encodeToString(originalContent.toByteArray(), Base64.DEFAULT)
        val url = "data:text/plain;base64,$data"

        manager.saveDataUri(url = url, mimetype = "text/plain", filename = "hello.txt")

        val writtenBytes = readWrittenContentQ()
        assertNotNull("Expected content to be written via ContentResolver", writtenBytes)
        assertEquals(originalContent, String(writtenBytes!!))
    }

    @Test
    fun `Given non-base64 data URI when saving then url-decodes content correctly`() = runTest {
        val url = "data:text/plain,Hello%20World"

        manager.saveDataUri(url = url, mimetype = "text/plain", filename = "plain.txt")

        val writtenBytes = readWrittenContentQ()
        assertNotNull("Expected content to be written via ContentResolver", writtenBytes)
        assertEquals("Hello World", String(writtenBytes!!))
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `Given binary base64 data URI when saving then writes binary content`() = runTest {
        // Use pre-Q path because Robolectric's FakeMediaProvider rejects application/octet-stream
        mockkStatic(MediaScannerConnection::class)
        every {
            MediaScannerConnection.scanFile(any(), any(), any(), any())
        } answers {
            val callback = arg<MediaScannerConnection.OnScanCompletedListener>(3)
            val paths = arg<Array<String>>(1)
            callback.onScanCompleted(paths.first(), Uri.parse("content://media/external/downloads/1"))
        }

        try {
            val binaryContent = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte(), 0xFE.toByte())
            val data = Base64.encodeToString(binaryContent, Base64.DEFAULT)
            val url = "data:application/octet-stream;base64,$data"
            val filename = "binary.bin"

            manager.saveDataUri(url = url, mimetype = "application/octet-stream", filename = filename)

            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloads, filename)
            assertTrue("Expected file to exist at ${file.absolutePath}", file.exists())
            assertArrayEquals(binaryContent, file.readBytes())
        } finally {
            unmockkStatic(MediaScannerConnection::class)
        }
    }

    // endregion

    // region Filename handling

    @Test
    fun `Given provided filename when saving then notification title uses filename`() = runTest {
        val data = Base64.encodeToString("test".toByteArray(), Base64.DEFAULT)
        val url = "data:text/plain;base64,$data"
        val filename = "my_document.txt"

        manager.saveDataUri(url = url, mimetype = "text/plain", filename = filename)

        val notification = getLatestNotification(url)
        assertNotNull("Expected a notification to be posted", notification)
        val title = shadowOf(notification).contentTitle.toString()
        assertEquals(filename, title)
    }

    @Test
    fun `Given null filename when saving then notification title uses unnamed file label`() = runTest {
        val data = Base64.encodeToString("test".toByteArray(), Base64.DEFAULT)
        val url = "data:text/plain;base64,$data"

        manager.saveDataUri(url = url, mimetype = "text/plain", filename = null)

        val notification = getLatestNotification(url)
        assertNotNull("Expected a notification to be posted", notification)
        val title = shadowOf(notification).contentTitle.toString()
        assertEquals(app.getString(commonR.string.downloads_unnamed_file), title)
    }

    @Test
    fun `Given blank filename when saving then notification title is blank`() = runTest {
        val data = Base64.encodeToString("test".toByteArray(), Base64.DEFAULT)
        val url = "data:text/plain;base64,$data"

        manager.saveDataUri(url = url, mimetype = "text/plain", filename = "")

        val notification = getLatestNotification(url)
        assertNotNull("Expected a notification to be posted", notification)
        // Blank filename is non-null, so it is used as-is for the notification title
        val title = shadowOf(notification).contentTitle.toString()
        assertEquals("", title)
    }

    // endregion

    // region Notification content

    @Test
    fun `Given successful save when notification is posted then shows download complete`() = runTest {
        val data = Base64.encodeToString("success".toByteArray(), Base64.DEFAULT)
        val url = "data:text/plain;base64,$data"

        manager.saveDataUri(url = url, mimetype = "text/plain", filename = "success.txt")

        val notification = getLatestNotification(url)
        assertNotNull("Expected a notification to be posted", notification)
        val contentText = shadowOf(notification).contentText.toString()
        assertEquals(
            app.getString(commonR.string.downloads_complete),
            contentText,
        )
    }

    @Test
    fun `Given successful save when notification is posted then has ACTION_VIEW content intent`() = runTest {
        val data = Base64.encodeToString("view".toByteArray(), Base64.DEFAULT)
        val url = "data:text/plain;base64,$data"

        manager.saveDataUri(url = url, mimetype = "text/plain", filename = "viewable.txt")

        val notification = getLatestNotification(url)
        assertNotNull("Expected a notification to be posted", notification)
        assertNotNull("Expected notification to have a content intent", notification!!.contentIntent)
    }

    // endregion

    // region Pre-Q file path

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `Given pre-Q device when saving then writes file to external downloads directory`() = runTest {
        // MediaScannerConnection.scanFile callback is never invoked by Robolectric,
        // so we mock it to immediately call back with a fake URI.
        mockkStatic(MediaScannerConnection::class)
        every {
            MediaScannerConnection.scanFile(any(), any(), any(), any())
        } answers {
            val callback = arg<MediaScannerConnection.OnScanCompletedListener>(3)
            val paths = arg<Array<String>>(1)
            callback.onScanCompleted(paths.first(), Uri.parse("content://media/external/downloads/1"))
        }

        try {
            val content = "pre-q content"
            val data = Base64.encodeToString(content.toByteArray(), Base64.DEFAULT)
            val url = "data:text/plain;base64,$data"
            val filename = "preq_file.txt"

            manager.saveDataUri(url = url, mimetype = "text/plain", filename = filename)

            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloads, filename)
            assertTrue("Expected file to exist at ${file.absolutePath}", file.exists())
            assertEquals(content, file.readText())
        } finally {
            unmockkStatic(MediaScannerConnection::class)
        }
    }

    // endregion

    // region Error handling

    @Test
    fun `Given unsupported mimetype when saving on Q+ then shows failure notification`() = runTest {
        // Robolectric's FakeMediaProvider rejects unsupported MIME types,
        // which triggers the exception catch path and results in a failed download notification
        val data = Base64.encodeToString("content".toByteArray(), Base64.DEFAULT)
        val url = "data:application/octet-stream;base64,$data"

        manager.saveDataUri(url = url, mimetype = "application/octet-stream", filename = "bad.bin")

        val notification = getLatestNotification(url)
        assertNotNull("Expected a notification to be posted", notification)
        val contentText = shadowOf(notification).contentText.toString()
        assertEquals(app.getString(commonR.string.downloads_failed), contentText)
    }

    // endregion

    // region Notification channel

    @Test
    fun `Given save is called when notification is posted then downloads channel is created`() = runTest {
        val data = Base64.encodeToString("channel".toByteArray(), Base64.DEFAULT)
        val url = "data:text/plain;base64,$data"

        manager.saveDataUri(url = url, mimetype = "text/plain", filename = "channel.txt")

        val channel = notificationManager.getNotificationChannel(CHANNEL_DOWNLOADS)
        assertNotNull("Expected downloads notification channel to be created", channel)
        assertEquals(app.getString(commonR.string.downloads), channel.name.toString())
    }

    // endregion

    // region Helpers

    /**
     * Retrieves the latest notification posted with the given URL's hashCode as the notification ID.
     */
    private fun getLatestNotification(url: String): Notification? {
        val shadow = shadowOf(notificationManager)
        return shadow.allNotifications.firstOrNull { it == shadow.getNotification(url.hashCode()) }
    }

    /**
     * Extracts the MIME type from the notification's content intent (set via [android.content.Intent.setDataAndType]).
     */
    private fun getContentIntentMimeType(url: String): String? {
        val notification = getLatestNotification(url)
        assertNotNull("Expected a notification to be posted", notification)
        val intent = shadowOf(notification!!.contentIntent).savedIntent
        return intent.type
    }

    /**
     * Reads the content written to MediaStore on API Q+ by querying the ContentResolver
     * for the most recently inserted download.
     */
    private fun readWrittenContentQ(): ByteArray? {
        val cursor = contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            null,
            null,
            "${MediaStore.Downloads._ID} DESC",
        ) ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null
            val id = it.getLong(0)
            val uri = ContentUris.withAppendedId(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                id,
            )
            return contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
        }
    }

    // endregion
}
