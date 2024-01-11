package io.homeassistant.companion.android.matter

enum class MatterFrontendCommissioningStatus {
    NOT_STARTED,
    REQUESTED,
    THREAD_EXPORT_TO_SERVER_MATTER,
    THREAD_EXPORT_TO_SERVER_ONLY,
    IN_PROGRESS,
    THREAD_SENT,
    THREAD_NONE,
    ERROR_MATTER,
    ERROR_THREAD_LOCAL_NETWORK,
    ERROR_THREAD_OTHER
}
