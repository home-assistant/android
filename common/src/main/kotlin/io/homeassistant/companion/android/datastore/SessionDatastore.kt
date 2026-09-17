package io.homeassistant.companion.android.datastore

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * Name of the file holding the sessions, under `files/datastore/`. The backup rules cover only the
 * `database` and `sharedpref` domains, so nothing here is ever part of an Android backup.
 */
@VisibleForTesting
internal const val SESSION_FILE_NAME = "session.datastore"

/**
 * These three values identify the keyset on disk. Changing any of them makes Tink generate a fresh
 * keyset, which permanently orphans every session encrypted with the previous one.
 */
private const val KEYSET_NAME = "keyset"
private const val KEYSET_PREF_FILE = "keyset_prefs"
private const val MASTER_KEY_URI = "android-keystore://session_encryption_key"

@Serializable
data class ServerSessions @VisibleForTesting constructor(
    // map serverId to session
    val sessions: Map<Int, ServerSession>,
    // default value that can be use later for migration purpose
    val version: Int = 1,
)

/** Writes an [Instant] as epoch seconds, the precision Home Assistant expresses expiry in. */
private object InstantParceler : Parceler<Instant> {
    override fun create(parcel: Parcel): Instant = Instant.fromEpochSeconds(parcel.readLong())

    override fun Instant.write(parcel: Parcel, flags: Int) = parcel.writeLong(epochSeconds)
}

/** Parcelable because onboarding hands a [TemporaryServer] between activities on Wear OS. */
@Serializable
@Parcelize
@TypeParceler<Instant, InstantParceler>
data class ServerSession(
    val accessToken: String,
    val refreshToken: String,
    val tokenExpiration: Instant,
    val tokenType: String,
) : Parcelable

private class ServerSessionsSerializer : Serializer<ServerSessions> {
    override val defaultValue: ServerSessions = ServerSessions(emptyMap())

    // DataStore already calls the serializer from its own IO-dispatched scope, so neither of
    // these needs to switch context.
    override suspend fun readFrom(input: InputStream): ServerSessions {
        return try {
            kotlinJsonMapper.decodeFromString<ServerSessions>(input.readBytes().decodeToString())
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read sessions", serialization)
        }
    }

    override suspend fun writeTo(t: ServerSessions, output: OutputStream) {
        output.write(kotlinJsonMapper.encodeToString(t).encodeToByteArray())
    }
}

/**
 * Authentication tokens for every server, encrypted with a Tink AEAD whose key is wrapped by
 * AndroidKeyStore.
 *
 * Tokens live here rather than in the database because the database is part of the Android backup.
 * A restored install therefore finds no session and re-authenticates, instead of adopting the
 * tokens and webhook of the install the backup came from.
 */
@Singleton
class SessionDatastore @VisibleForTesting internal constructor(
    private val context: Context,
    private val produceFile: () -> File,
) {

    /**
     * DataStore refuses two instances over one file, so the single production instance is the Hilt
     * singleton. Tests use the other constructor to point at a file of their own.
     */
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context = context,
        produceFile = { context.dataStoreFile(SESSION_FILE_NAME) },
    )

    private val aead: Aead by lazy {
        // Registers the AES-GCM key creator. Without it KeysetHandle.generateNew has no creator
        // registered for AesGcmParameters and key generation fails.
        AeadConfig.register()

        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_FILE)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private val dataStore by lazy {
        DataStoreFactory.create(
            serializer = AeadSerializer(
                aead = aead,
                wrappedSerializer = ServerSessionsSerializer(),
                // Unique per store, so ciphertext cannot be swapped in from another one.
                associatedData = SESSION_FILE_NAME.encodeToByteArray(),
            ),
            corruptionHandler = ReplaceFileCorruptionHandler {
                // Reachable when the Tink keyset and the stored sessions disagree, for example
                // after a partial restore or Keystore corruption. Not reachable through a screen
                // lock change: the master key is not bound to user authentication, so re-enrolling
                // or removing the lock leaves it usable. Dropping the sessions sends the user
                // through re-authentication rather than crashing.
                Timber.w(it, "Sessions unreadable, every server has to authenticate again")
                ServerSessions(emptyMap())
            },
            produceFile = produceFile,
        )
    }

    /** Sessions for every server, keyed by server ID. */
    suspend fun getAll(): ServerSessions = dataStore.data.first()

    /** Session for [serverId], or `null` when that server has none. */
    suspend fun getSession(serverId: Int): ServerSession? = getAll().sessions[serverId]

    /** Replaces the session stored for [serverId]. */
    suspend fun updateSession(serverId: Int, session: ServerSession) {
        dataStore.updateData { current ->
            current.copy(sessions = current.sessions + (serverId to session))
        }
    }

    /** Drops the session stored for [serverId], if any. */
    suspend fun removeSession(serverId: Int) {
        dataStore.updateData { current ->
            current.copy(sessions = current.sessions - serverId)
        }
    }
}
