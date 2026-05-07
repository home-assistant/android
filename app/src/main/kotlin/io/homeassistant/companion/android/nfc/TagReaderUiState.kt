package io.homeassistant.companion.android.nfc

import androidx.annotation.StringRes

/**
 * UI states surfaced by [TagReaderViewModel] and consumed by the tag reader screen.
 */
sealed interface TagReaderUiState {
    /** Initial state. No UI is rendered. */
    data object Initial : TagReaderUiState

    /**
     * The scanned tag id need approving from the user before any
     * server action is taken.
     */
    data class ApprovingTag(val tagId: String) : TagReaderUiState

    /**
     * The tag is being scanned to all registered servers.
     */
    data object Scanning : TagReaderUiState

    /**
     * A user-facing error occurred.
     */
    data class Error(@StringRes val messageRes: Int) : TagReaderUiState

    /** Terminal state: the activity should call `finish()`. */
    data object Done : TagReaderUiState
}
