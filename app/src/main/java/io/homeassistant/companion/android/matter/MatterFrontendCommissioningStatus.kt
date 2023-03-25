package io.homeassistant.companion.android.matter

enum class MatterFrontendCommissioningStatus {
    NOT_STARTED,
    REQUESTED,
    THREAD_EXPORT_TO_SERVER,
    IN_PROGRESS,
    ERROR
}
