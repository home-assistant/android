package io.homeassistant.companion.android.changelog

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.LocalStorage
import javax.inject.Inject

/**
 * Name of the SharedPreferences file previously written by the `info.hannes.changelog` library,
 * reused so existing installs keep their "last seen changelog" state.
 */
internal const val CHANGELOG_PREFERENCES_NAME = "changelog"

/** Key previously written by the `info.hannes.changelog` library, see [CHANGELOG_PREFERENCES_NAME]. */
private const val KEY_LAST_SEEN_VERSION_CODE = "ChangeLog_last_version_code"

/**
 * Tracks the app version whose changelog the user last saw.
 */
class ChangelogRepository @VisibleForTesting constructor(
    private val localStorage: LocalStorage,
    private val currentVersionCode: Int,
) {

    @Inject
    constructor(@NamedChangelogStorage localStorage: LocalStorage) : this(localStorage, BuildConfig.VERSION_CODE)

    /**
     * Returns `true` when the app was updated since the changelog was last marked seen.
     *
     * A fresh install is not an update: the current version is stored as the baseline to compare
     * future updates against, and `false` is returned.
     */
    suspend fun wasAppUpdatedSinceChangelogSeen(): Boolean {
        val lastSeenVersionCode = localStorage.getInt(KEY_LAST_SEEN_VERSION_CODE) ?: run {
            markChangelogSeen()
            return false
        }
        return lastSeenVersionCode < currentVersionCode
    }

    /** Stores the current app version as the last one whose changelog was seen. */
    suspend fun markChangelogSeen() {
        localStorage.putInt(KEY_LAST_SEEN_VERSION_CODE, currentVersionCode)
    }
}
