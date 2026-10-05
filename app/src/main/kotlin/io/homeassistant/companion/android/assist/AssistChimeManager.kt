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
     * Plays the listening chime if it is enabled in the Assist settings and suspends until it has finished.
     * Failures are logged and never stop Assist from listening.
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
    // Assigned as soon as it exists so it is released even if cancelled while preparing
    var player: MediaPlayer? = null
    try {
        // Preparing reads the file, so keep it off the main thread
        val prepared = withContext(ioDispatcher) {
            MediaPlayer().also { player = it }.prepareChime(context, resId)
        }
        if (prepared) player?.let { awaitPlayback(it) }
    } finally {
        player?.release()
    }
}

private fun MediaPlayer.prepareChime(context: Context, @RawRes resId: Int): Boolean = try {
    setAudioAttributes(listeningChimeAudioAttributes())
    context.resources.openRawResourceFd(resId).use { file ->
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

private suspend fun awaitPlayback(player: MediaPlayer) {
    suspendCancellableCoroutine { continuation ->
        player.setOnCompletionListener { continuation.resume(Unit) }
        player.setOnErrorListener { _, what, extra ->
            Timber.w("Failed to play the listening chime (what=$what, extra=$extra)")
            continuation.resume(Unit)
            true
        }
        try {
            player.start()
        } catch (e: IllegalStateException) {
            Timber.w(e, "Failed to start the listening chime")
            continuation.resume(Unit)
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
