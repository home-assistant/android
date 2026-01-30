package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

sealed interface ShortcutRepositoryResult<out T> {
    data class Success<T>(val data: T) : ShortcutRepositoryResult<T>
    data class Error(val error: ShortcutRepositoryError, val throwable: Throwable? = null) :
        ShortcutRepositoryResult<Nothing>
}

enum class ShortcutRepositoryError {
    NoServers,
    SlotsFull,
    InvalidIndex,
    InvalidInput,
    PinnedNotSupported,
    Unknown,
}
