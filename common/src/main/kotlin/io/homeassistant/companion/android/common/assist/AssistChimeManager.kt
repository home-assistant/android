package io.homeassistant.companion.android.common.assist

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.util.SdkVersion
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Upper bound on how long the chime may delay listening. The bundled chime lasts about one second,
 * so this only guards against a player that never reports completion.
 */
@VisibleForTesting
internal val CHIME_TIMEOUT = 3.seconds

/**
 * Plays the chime telling the user that Assist is ready to listen.
 *
 * @param createPlayer builds the [MediaPlayer] to play the chime with; a seam so tests can supply a
 * player they control instead of a real one.
 */
class AssistChimeManager @VisibleForTesting constructor(
    private val prefsRepository: PrefsRepository,
    private val context: Context,
    private val createPlayer: () -> MediaPlayer,
    private val backgroundDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        prefsRepository: PrefsRepository,
    ) : this(
        prefsRepository = prefsRepository,
        context = context,
        createPlayer = { MediaPlayer().apply { setAudioAttributes(listeningChimeAudioAttributes()) } },
        backgroundDispatcher = Dispatchers.IO,
    )

    /**
     * Plays the listening chime if it is enabled in the Assist settings and suspends until it has finished.
     */
    suspend fun playListeningChimeIfEnabled() {
        if (!prefsRepository.isAssistListeningChimeEnabled()) return
        withTimeoutOrNull(CHIME_TIMEOUT) { playChime() }
            ?: Timber.w("Listening chime did not finish within $CHIME_TIMEOUT")
    }

    private suspend fun playChime() {
        val player = createPlayer()
        try {
            if (player.prepareChime()) player.awaitPlayback()
        } finally {
            player.release()
        }
    }

    private suspend fun MediaPlayer.prepareChime(): Boolean = withContext(backgroundDispatcher) {
        try {
            context.resources.openRawResourceFd(R.raw.assist_listening_chime).use { file ->
                setDataSource(file.fileDescriptor, file.startOffset, file.length)
            }
            prepare()
            true
        } catch (e: IOException) {
            Timber.w(e, "Failed to load the listening chime")
            false
        } catch (e: IllegalStateException) {
            Timber.w(e, "Failed to prepare the listening chime")
            false
        }
    }

    private suspend fun MediaPlayer.awaitPlayback() {
        suspendCancellableCoroutine { continuation ->
            fun resumeOnce() {
                if (continuation.isActive) continuation.resume(Unit)
            }
            setOnCompletionListener { resumeOnce() }
            setOnErrorListener { _, what, extra ->
                Timber.w("Failed to play the listening chime (what=$what, extra=$extra)")
                resumeOnce()
                true
            }
            try {
                start()
            } catch (e: IllegalStateException) {
                Timber.w(e, "Failed to start the listening chime")
                resumeOnce()
            }
        }
    }
}

/**
 * Same usage as the Assist voice responses (see AudioUrlPlayer), so the chime follows the assistant
 * volume. [AudioAttributes.USAGE_ASSISTANT] requires API 26, older versions fall back to a sonification usage.
 */
private fun listeningChimeAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            AudioAttributes.USAGE_ASSISTANT
        } else {
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
        },
    )
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()
