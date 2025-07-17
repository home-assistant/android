package io.homeassistant.companion.android.webview

enum class MatterThreadStep {
    NOT_STARTED,
    REQUESTED,
    THREAD_EXPORT_TO_SERVER_MATTER,
    THREAD_EXPORT_TO_SERVER_ONLY,
    MATTER_IN_PROGRESS,
    THREAD_SENT,
    THREAD_NONE,
    ERROR_MATTER,
    ERROR_THREAD_LOCAL_NETWORK,
    ERROR_THREAD_OTHER,
}
