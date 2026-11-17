package io.homeassistant.companion.android.webrtc.compose

/** How the incoming video is scaled inside its container. */
enum class VideoScaling {
    /** Fit the whole frame inside the container, letterboxing the empty space. */
    FIT,

    /** Fill the container, cropping the parts of the frame that do not fit. */
    FILL,

    /** Balance between [FIT] and [FILL], cropping only a little to reduce letterboxing. */
    BALANCED,
}
