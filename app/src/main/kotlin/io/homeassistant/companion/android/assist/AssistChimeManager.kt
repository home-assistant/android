package io.homeassistant.companion.android.assist

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import androidx.annotation.RawRes
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.R
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
 * Plays the chime telling the user that Assist is ready to listen
 */
internal class AssistChimeManager @VisibleForTesting constructor(
    private val prefsRepository: PrefsRepository,
    private val playChime: suspend () -> Unit,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        prefsRepository: PrefsRepository,
    ) : this(prefsRepository, { playRawResource(context, R.raw.assist_listening_chime) })

    /**
     * Plays the listening chime if it is enabled in the Assist settings, and suspends until it has
     * finished. Callers open the microphone afterwards, so the chime is a reliable "speak now" signal
     * and is not recorded as part of the user's request.
     *
     * Playback problems are logged and otherwise ignored: a missing chime must never stop Assist
     * from listening.
     */
    suspend fun playListeningChimeIfEnabled() {
        if (!prefsRepository.isAssistListeningChimeEnabled()) return
        withTimeoutOrNull(CHIME_TIMEOUT) { playChime() }
            ?: Timber.w("Listening chime did not finish within $CHIME_TIMEOUT")
    }
}

private suspend fun playRawResource(
    context: Context,
    @RawRes resId: Int,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // Preparing reads the file, so keep it off the main thread
    val player = withContext(ioDispatcher) { createPreparedPlayer(context, resId) } ?: return
    try {
        suspendCancellableCoroutine { continuation ->
            player.setOnCompletionListener { continuation.resume(Unit) }
            player.setOnErrorListener { _, what, extra ->
                Timber.w("Failed to play the listening chime (what=$what, extra=$extra)")
                continuation.resume(Unit)
                true
            }
            player.start()
        }
    } finally {
        player.release()
    }
}

private fun createPreparedPlayer(context: Context, @RawRes resId: Int): MediaPlayer? {
    val player = MediaPlayer()
    return try {
        player.setAudioAttributes(listeningChimeAudioAttributes())
        context.resources.openRawResourceFd(resId).use { file ->
            player.setDataSource(file.fileDescriptor, file.startOffset, file.length)
        }
        player.prepare()
        player
    } catch (e: IOException) {
        Timber.w(e, "Failed to load the listening chime")
        player.release()
        null
    } catch (e: IllegalStateException) {
        Timber.w(e, "Failed to prepare the listening chime")
        player.release()
        null
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
