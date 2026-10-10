package io.homeassistant.companion.android.changelog

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import javax.inject.Inject

/**
 * Decides whether the changelog screen should be shown.
 */
@HiltViewModel
class ChangelogShowViewModel @VisibleForTesting constructor(
    private val prefsRepository: PrefsRepository,
    private val currentVersionCode: Int,
) : ViewModel() {

    @Inject
    constructor(prefsRepository: PrefsRepository) : this(prefsRepository, BuildConfig.VERSION_CODE)

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
        return prefsRepository.isChangeLogPopupEnabled() &&
            prefsRepository.wasAppUpdatedSinceChangelogSeen(currentVersionCode)
    }
}
