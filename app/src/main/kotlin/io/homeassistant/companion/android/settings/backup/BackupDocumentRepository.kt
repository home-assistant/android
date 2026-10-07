package io.homeassistant.companion.android.settings.backup

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.backup.SETTINGS_BACKUP_MAX_BYTES
import io.homeassistant.companion.android.common.data.backup.SettingsBackupCodec
import io.homeassistant.companion.android.common.data.backup.SettingsBackupData
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads and writes only documents explicitly selected through Android's document picker. */
internal class BackupDocumentRepository @VisibleForTesting constructor(
    private val context: Context,
    private val codec: SettingsBackupCodec,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(@ApplicationContext context: Context, codec: SettingsBackupCodec) : this(context, codec, Dispatchers.IO)

    suspend fun read(document: String): SettingsBackupData = withContext(ioDispatcher) {
        val bytes = context.contentResolver.openInputStream(document.toUri())?.use { input ->
            val buffer = ByteArray(SETTINGS_BACKUP_MAX_BYTES + 1)
            var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            buffer.copyOf(count)
        } ?: throw IOException("Cannot open the settings backup")
        codec.decode(bytes)
    }

    suspend fun write(document: String, backup: SettingsBackupData) = withContext(ioDispatcher) {
        val bytes = codec.encode(backup)
        context.contentResolver.openOutputStream(document.toUri(), "wt")?.use { output ->
            output.write(bytes)
        } ?: throw IOException("Cannot create the settings backup")
    }
}
