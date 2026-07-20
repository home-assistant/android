package io.homeassistant.companion.android.settings.shortcuts.data.entities

internal sealed interface ShortcutResult<out T> {
    data class Success<T>(val data: T) : ShortcutResult<T>
    data class Error(val error: ShortcutError) : ShortcutResult<Nothing>
}

internal sealed interface ShortcutError {
    data object AndroidVersionNotSupported : ShortcutError
    data object NoServersConfigured : ShortcutError
    data object AppShortcutSlotsFull : ShortcutError
    data object ShortcutNotFound : ShortcutError
    data object HomeShortcutPinningNotSupported : ShortcutError
    data object Unknown : ShortcutError
}
