package io.homeassistant.companion.android.sensors.healthconnect

/**
 * Outcome of a Health Connect write attempt.
 *
 * Modeled as a sealed class (rather than throwing) so callers — primarily
 * [io.homeassistant.companion.android.sensors.healthconnect.command.HealthConnectWriteCommandHandler] —
 * can branch on the failure mode and surface a user-meaningful notification without
 * unwrapping exceptions across coroutine boundaries.
 */
sealed class HealthConnectWriteResult {
    data class Success(val insertedIds: List<String>) : HealthConnectWriteResult()

    /** Health Connect is not installed or the SDK reports unavailable on this device. */
    object Unavailable : HealthConnectWriteResult()

    /** The user has not granted the WRITE permission for this data type. */
    data class MissingPermission(val permission: String) : HealthConnectWriteResult()

    /** The payload was rejected before any HC client call was attempted. */
    data class InvalidPayload(val reason: String) : HealthConnectWriteResult()

    /** The HC client itself rejected or failed the call. */
    data class Failure(val cause: Throwable) : HealthConnectWriteResult()
}
