package io.homeassistant.companion.android.common.data.wear.dashboard.model

/**
 * Supported Wear Dashboard schema versions.
 */
object WearDashboardSchemaVersion {
    /** Current schema version written by this client. */
    const val CURRENT_VERSION: Int = 1

    /** Schema versions that this client can read and validate. */
    val SUPPORTED_VERSIONS: List<Int> = listOf(CURRENT_VERSION)

    /**
     * Returns whether [version] is supported by this client.
     */
    fun isSupported(version: Int): Boolean = version in SUPPORTED_VERSIONS
}
