package io.homeassistant.companion.android.changelog

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import javax.inject.Inject

/**
 * Decides whether the changelog screen should be shown.
 */
@HiltViewModel
class ChangelogShowViewModel @Inject constructor(
    private val changelogRepository: ChangelogRepository,
    private val prefsRepository: PrefsRepository,
) : ViewModel() {

    private var consumed = false

    /**
     * Returns `true` when the user has enabled the changelog popup and the changelog of the
     * current app version has not been seen yet.
     *
     * The decision is consumed: it returns `true` at most once per instance so configuration
     * changes or returning to the frontend don't show the changelog again.
     */
    suspend fun consumeShouldShowChangelog(): Boolean {
        if (consumed) return false
        consumed = true
        return prefsRepository.isChangeLogPopupEnabled() && changelogRepository.wasAppUpdatedSinceChangelogSeen()
    }
}
