package io.homeassistant.companion.android.webrtc.fakes

import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionController
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig

internal class FakePeerConnectionControllerFactory : PeerConnectionController.Factory {

    val controllers = mutableListOf<FakePeerConnectionController>()
    var createException: Exception? = null

    val lastController: FakePeerConnectionController
        get() = controllers.last()

    override suspend fun create(config: RtcClientConfig, mediaOptions: MediaOptions): PeerConnectionController {
        createException?.let { throw it }
        return FakePeerConnectionController(
            offerSdp = "v=0 fake-offer-${controllers.size}",
            mediaOptions = mediaOptions,
        ).also {
            controllers += it
        }
    }
}
