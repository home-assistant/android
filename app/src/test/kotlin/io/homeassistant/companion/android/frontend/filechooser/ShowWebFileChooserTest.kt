package io.homeassistant.companion.android.frontend.filechooser

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.WebChromeClient.FileChooserParams
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.util.SdkVersion
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ShowWebFileChooserTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val contract = ShowWebFileChooser()
    private val photoOutput = CaptureOutput(CaptureKind.Photo, Uri.parse("content://provider/capture.jpg"))
    private val videoOutput = CaptureOutput(CaptureKind.Video, Uri.parse("content://provider/capture.mp4"))

    @Test
    fun `Given no accept types when creating intent then any openable file can be picked`() = runTest {
        val intent = createIntent(FakeFileChooserParams(acceptTypes = arrayOf("")))

        assertEquals(Intent.ACTION_GET_CONTENT, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertEquals("*/*", intent.type)
        assertFalse(intent.hasExtra(Intent.EXTRA_MIME_TYPES))
        assertFalse(intent.hasExtra(Intent.EXTRA_ALLOW_MULTIPLE))
    }

    @Test
    fun `Given MIME types and extensions when creating intent then they are passed as MIME types`() = runTest {
        val intent = createIntent(FakeFileChooserParams(acceptTypes = arrayOf("image/*", " .PDF ", "application/pdf")))

        assertEquals("*/*", intent.type)
        assertArrayEquals(arrayOf("image/*", "application/pdf"), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
    }

    @Test
    fun `Given extensions and MIME types unknown to Android when creating intent then octet-stream is accepted and custom types kept`() = runTest {
        val intent = createIntent(
            FakeFileChooserParams(acceptTypes = arrayOf(".tar", ".backup", "application/integration.custom", "video/*", "text/plain")),
        )

        assertArrayEquals(
            arrayOf("application/x-tar", "application/octet-stream", "application/integration.custom", "video/*", "text/plain"),
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES),
        )
    }

    @Test
    fun `Given entries that are neither extension nor MIME type when creating intent then no filter is applied`() = runTest {
        val intent = createIntent(FakeFileChooserParams(acceptTypes = arrayOf("png", "image")))

        assertFalse(intent.hasExtra(Intent.EXTRA_MIME_TYPES))
    }

    @Test
    fun `Given a wildcard accept type when creating intent then no MIME type filter is applied`() = runTest {
        val intent = createIntent(FakeFileChooserParams(acceptTypes = arrayOf("image/*", "*/*")))

        assertFalse(intent.hasExtra(Intent.EXTRA_MIME_TYPES))
    }

    @Test
    fun `Given accept entries when converting each then every valid entry is kept as a MIME type`() = runTest {
        val params = FakeFileChooserParams(acceptTypes = arrayOf("image/*", " .JPG ", ".unknownextension", "*/*", "png", ""))

        assertEquals(
            listOf("image/*", "image/jpeg", "application/octet-stream", "*/*"),
            params.acceptedMimeTypes(StandardTestDispatcher(testScheduler)),
        )
    }

    @Test
    fun `Given multiple mode when creating intent then multiple selection is allowed`() = runTest {
        val intent = createIntent(FakeFileChooserParams(mode = FileChooserParams.MODE_OPEN_MULTIPLE))

        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
    }

    @Test
    fun `Given folder mode when creating intent then a document tree is opened without extras`() = runTest {
        val intent = createIntent(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), mode = FileChooserParams.MODE_OPEN_FOLDER))

        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertNull(intent.type)
        assertNull(intent.categories)
        assertNull(intent.extras)
    }

    @Test
    fun `Given save mode when creating intent then a document is created with the first type and suggested name`() = runTest {
        val intent = createIntent(
            FakeFileChooserParams(
                acceptTypes = arrayOf("text/plain", ".csv"),
                mode = FileChooserParams.MODE_SAVE,
                filenameHint = "export.txt",
            ),
        )

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertEquals("text/plain", intent.type)
        assertEquals("export.txt", intent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun `Given save mode without accept types or name when creating intent then any type is created`() = runTest {
        val intent = createIntent(FakeFileChooserParams(mode = FileChooserParams.MODE_SAVE))

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("*/*", intent.type)
        assertFalse(intent.hasExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun `Given read write permission on API 37 when creating intent then the document is opened`() = runTest {
        SdkVersion.sdkInt = Build.VERSION_CODES.CINNAMON_BUN
        val intent = createIntent(
            FakeFileChooserParams(
                mode = FileChooserParams.MODE_OPEN_MULTIPLE,
                permissionMode = FileChooserParams.PERMISSION_MODE_READ_WRITE,
            ),
        )

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
    }

    @Test
    fun `Given read write permission before API 37 when creating intent then content is picked`() = runTest {
        SdkVersion.sdkInt = Build.VERSION_CODES.BAKLAVA
        val intent = createIntent(FakeFileChooserParams(permissionMode = FileChooserParams.PERMISSION_MODE_READ_WRITE))

        assertEquals(Intent.ACTION_GET_CONTENT, intent.action)
    }

    @Test
    fun `Given direct camera capture when creating intent then the camera app writes to the output uri`() = runTest {
        assertCaptureIntent(createIntent(FakeFileChooserParams(), CameraCapture.Direct(photoOutput)), photoOutput)
        assertCaptureIntent(createIntent(FakeFileChooserParams(), CameraCapture.Direct(videoOutput)), videoOutput)
    }

    @Test
    fun `Given offered camera capture when creating intent then a chooser shows the picker and each camera app`() = runTest {
        val intent = createIntent(
            FakeFileChooserParams(acceptTypes = arrayOf("image/*")),
            CameraCapture.Offered(listOf(photoOutput, videoOutput)),
        )

        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        val pickerIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals(Intent.ACTION_GET_CONTENT, pickerIntent?.action)
        assertArrayEquals(arrayOf("image/*"), pickerIntent?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        val initialIntents = IntentCompat.getParcelableArrayExtra(intent, Intent.EXTRA_INITIAL_INTENTS, Intent::class.java)
        assertEquals(2, initialIntents?.size)
        assertCaptureIntent(initialIntents!![0] as Intent, photoOutput)
        assertCaptureIntent(initialIntents[1] as Intent, videoOutput)
    }

    @Test
    fun `Given several files in clip data when parsing result then all uris are selected`() {
        val first = Uri.parse("content://provider/first")
        val second = Uri.parse("content://provider/second")
        val intent = Intent().apply {
            clipData = ClipData.newRawUri(null, first).apply { addItem(ClipData.Item(second)) }
        }

        assertEquals(FileChooserResult.Selected(listOf(first, second)), contract.parseResult(Activity.RESULT_OK, intent))
    }

    @Test
    fun `Given a single file in clip data without data when parsing result then the uri is selected`() {
        val uri = Uri.parse("content://provider/file")
        val intent = Intent().apply { clipData = ClipData.newRawUri(null, uri) }

        assertEquals(FileChooserResult.Selected(listOf(uri)), contract.parseResult(Activity.RESULT_OK, intent))
    }

    @Test
    fun `Given only data when parsing result then the uri is selected`() {
        val uri = Uri.parse("content://provider/file")

        assertEquals(FileChooserResult.Selected(listOf(uri)), contract.parseResult(Activity.RESULT_OK, Intent().setData(uri)))
    }

    @Test
    fun `Given success without uri when parsing result then it is captured`() {
        assertEquals(FileChooserResult.Captured, contract.parseResult(Activity.RESULT_OK, null))
        assertEquals(FileChooserResult.Captured, contract.parseResult(Activity.RESULT_OK, Intent()))
    }

    @Test
    fun `Given cancelled result when parsing result then it is cancelled`() {
        val intent = Intent().setData(Uri.parse("content://provider/file"))

        assertEquals(FileChooserResult.Cancelled, contract.parseResult(Activity.RESULT_CANCELED, intent))
    }

    private suspend fun TestScope.createIntent(params: FileChooserParams, cameraCapture: CameraCapture? = null): Intent {
        val mimeTypes = params.acceptedMimeTypes(StandardTestDispatcher(testScheduler))
        return contract.createIntent(context, FileChooserInput(params, mimeTypes.toPickerMimeTypes(), cameraCapture))
    }

    private fun assertCaptureIntent(intent: Intent, output: CaptureOutput) {
        val expectedAction = when (output.kind) {
            CaptureKind.Photo -> MediaStore.ACTION_IMAGE_CAPTURE
            CaptureKind.Video -> MediaStore.ACTION_VIDEO_CAPTURE
        }
        assertEquals(expectedAction, intent.action)
        assertEquals(output.uri, IntentCompat.getParcelableExtra(intent, MediaStore.EXTRA_OUTPUT, Uri::class.java))
        assertEquals(output.uri, intent.clipData?.getItemAt(0)?.uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
