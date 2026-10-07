package io.homeassistant.companion.android.settings.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.backup.InvalidSettingsBackupException
import io.homeassistant.companion.android.common.data.backup.SETTINGS_BACKUP_MAX_BYTES
import io.homeassistant.companion.android.common.data.backup.SettingsBackupCodec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class BackupDocumentRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val document = "content://test/settings-backup"
    private val codec = SettingsBackupCodec()

    @Test
    fun `Given a selected document when writing and reading then the same portable settings are recovered`() = runTest {
        val repository = BackupDocumentRepository(context, codec, StandardTestDispatcher(testScheduler))
        val output = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(document.toUri(), output)
        repository.write(document, backupFixture())
        shadowOf(context.contentResolver).registerInputStream(document.toUri(), ByteArrayInputStream(output.toByteArray()))
        assertEquals(backupFixture(), repository.read(document))
    }

    @Test
    fun `Given an oversized document when reading then only the size limit plus one byte is consumed`() = runTest {
        val repository = BackupDocumentRepository(context, codec, StandardTestDispatcher(testScheduler))
        val input = ByteArrayInputStream(ByteArray(SETTINGS_BACKUP_MAX_BYTES * 2))
        shadowOf(context.contentResolver).registerInputStream(document.toUri(), input)
        try {
            repository.read(document)
            throw AssertionError("Oversized document should be rejected")
        } catch (expected: InvalidSettingsBackupException) {
            assertEquals(SETTINGS_BACKUP_MAX_BYTES - 1, input.available())
        }
    }
}
