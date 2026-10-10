package io.homeassistant.companion.android.frontend.filechooser

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.WebChromeClient.FileChooserParams
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
        assertArrayEquals(
            arrayOf("image/*", "application/pdf"),
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES),
        )
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
    fun `Given several files in clip data when parsing result then all uris are returned`() {
        val first = Uri.parse("content://provider/first")
        val second = Uri.parse("content://provider/second")
        val intent = Intent().apply {
            clipData = ClipData.newRawUri(null, first).apply { addItem(ClipData.Item(second)) }
        }

        assertArrayEquals(arrayOf(first, second), contract.parseResult(Activity.RESULT_OK, intent))
    }

    @Test
    fun `Given a single file in clip data without data when parsing result then the uri is returned`() {
        val uri = Uri.parse("content://provider/file")
        val intent = Intent().apply { clipData = ClipData.newRawUri(null, uri) }

        assertArrayEquals(arrayOf(uri), contract.parseResult(Activity.RESULT_OK, intent))
    }

    @Test
    fun `Given only data when parsing result then the uri is returned`() {
        val uri = Uri.parse("content://provider/file")

        assertArrayEquals(arrayOf(uri), contract.parseResult(Activity.RESULT_OK, Intent().setData(uri)))
    }

    @Test
    fun `Given cancelled or empty result when parsing result then null is returned`() {
        val uri = Uri.parse("content://provider/file")

        assertNull(contract.parseResult(Activity.RESULT_CANCELED, Intent().setData(uri)))
        assertNull(contract.parseResult(Activity.RESULT_OK, null))
        assertNull(contract.parseResult(Activity.RESULT_OK, Intent()))
    }

    private suspend fun TestScope.createIntent(params: FileChooserParams): Intent {
        val input = FileChooserInput(params, params.acceptedMimeTypes(StandardTestDispatcher(testScheduler)))
        return contract.createIntent(context, input)
    }
}

private class FakeFileChooserParams(
    private val acceptTypes: Array<String> = emptyArray(),
    private val mode: Int = MODE_OPEN,
    private val filenameHint: String? = null,
    private val permissionMode: Int = PERMISSION_MODE_READ,
) : FileChooserParams() {
    override fun getMode(): Int = mode
    override fun getAcceptTypes(): Array<String> = acceptTypes
    override fun isCaptureEnabled(): Boolean = false
    override fun getTitle(): CharSequence? = null
    override fun getFilenameHint(): String? = filenameHint
    override fun getPermissionMode(): Int = permissionMode
    override fun createIntent(): Intent = error("Not used by ShowWebFileChooser")
}
