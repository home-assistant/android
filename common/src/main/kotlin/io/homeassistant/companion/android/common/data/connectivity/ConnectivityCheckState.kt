package io.homeassistant.companion.android.common.data.connectivity

import androidx.annotation.StringRes

/**
 * Result of a single connectivity check.
 */
sealed interface ConnectivityCheckResult {
    data object Pending : ConnectivityCheckResult
    data object InProgress : ConnectivityCheckResult
    data class Success(@StringRes val messageResId: Int, val details: String? = null) : ConnectivityCheckResult
    data class Failure(@StringRes val messageResId: Int) : ConnectivityCheckResult
    data class NotApplicable(@StringRes val messageResId: Int) : ConnectivityCheckResult
}

/**
 * State holding all connectivity check results for a URL.
 */
data class ConnectivityCheckState(
    val dnsResolution: ConnectivityCheckResult = ConnectivityCheckResult.Pending,
    val portReachability: ConnectivityCheckResult = ConnectivityCheckResult.Pending,
    val tlsCertificate: ConnectivityCheckResult = ConnectivityCheckResult.Pending,
    val serverConnection: ConnectivityCheckResult = ConnectivityCheckResult.Pending,
    val homeAssistantVerification: ConnectivityCheckResult = ConnectivityCheckResult.Pending,
) {
    private val allChecks: List<ConnectivityCheckResult> =
        listOf(dnsResolution, portReachability, tlsCertificate, serverConnection, homeAssistantVerification)

    val isComplete: Boolean =
        allChecks.none { it is ConnectivityCheckResult.Pending || it is ConnectivityCheckResult.InProgress }

    val hasFailure: Boolean =
        allChecks.any { it is ConnectivityCheckResult.Failure }
}
