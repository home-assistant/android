package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

sealed interface ShortcutResult<out T> {
    data class Success<T>(val data: T) : ShortcutResult<T>
    data class Error(val error: ShortcutError, val throwable: Throwable? = null) : ShortcutResult<Nothing>
}

enum class PinResult {
    Requested,
    Updated,
}

enum class ShortcutError {
    NoServers,
    SlotsFull,
    InvalidIndex,
    InvalidInput,
    PinnedNotSupported,
    Unknown,
}
