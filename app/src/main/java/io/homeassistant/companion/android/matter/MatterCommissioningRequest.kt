package io.homeassistant.companion.android.matter

object MatterCommissioningRequest {

    enum class Status {
        NOT_STARTED,
        REQUESTED,
        IN_PROGRESS,
        ERROR
    }
}
