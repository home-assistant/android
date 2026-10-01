package io.homeassistant.companion.android.frontend.filechooser

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.util.fileProviderAuthority
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class CameraCaptureRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `Given capture file created when deleting it then the file is removed`() = runTest {
        val providerInfo = context.packageManager.resolveContentProvider(context.fileProviderAuthority, PackageManager.GET_META_DATA)
        Robolectric.buildContentProvider(FileProvider::class.java).create(providerInfo)
        val repository = CameraCaptureRepository(context, StandardTestDispatcher(testScheduler))

        val uri = repository.createImageFile()
        val file = File(File(context.cacheDir, CAPTURE_DIRECTORY), uri.lastPathSegment!!)

        assertEquals(context.fileProviderAuthority, uri.authority)
        assertTrue(file.exists())

        repository.delete(uri)

        assertFalse(file.exists())
    }

    @Test
    fun `Given camera feature when checking camera then it is reported`() {
        val repository = CameraCaptureRepository(context)

        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, false)
        assertFalse(repository.hasCamera)

        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
        assertTrue(repository.hasCamera)
    }
}
