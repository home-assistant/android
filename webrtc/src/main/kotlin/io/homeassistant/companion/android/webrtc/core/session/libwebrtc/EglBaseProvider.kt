package io.homeassistant.companion.android.webrtc.core.session.libwebrtc

import io.homeassistant.companion.android.webrtc.core.SuspendLazy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import livekit.org.webrtc.EglBase

/**
 * Provides the single shared EGL context used by both the hardware codecs and the renderers.
 *
 * `EglBase.create()` is a slow native call, so it runs on [dispatcher],
 * once, via [SuspendLazy] (callers suspend instead of blocking). Keep a single instance per process.
 *
 * @param dispatcher the dispatcher the native creation runs on; defaults to [Dispatchers.Default].
 */
internal class EglBaseProvider(dispatcher: CoroutineDispatcher = Dispatchers.Default) {

    private val eglBase = SuspendLazy { withContext(dispatcher) { EglBase.create() } }

    /** The shared EGL context; created off the caller's thread, once, on first use. */
    suspend fun eglBaseContext(): EglBase.Context = eglBase.get().eglBaseContext
}
