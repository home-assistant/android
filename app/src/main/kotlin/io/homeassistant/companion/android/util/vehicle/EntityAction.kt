package io.homeassistant.companion.android.util.vehicle

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@VisibleForTesting
internal const val EVENT_ANDROID_NAVIGATION_STARTED = "android.navigation_started"

/**
 * Try to fire an `android.navigation_started` event for this entity. Failures are silently ignored.
 * Use when starting navigation to this entity, which should return `true` for [canNavigate].
 */
suspend fun EntityDisplay.tryFireNavigationEvent(integrationRepository: IntegrationRepository) {
    try {
        integrationRepository.fireEvent(
            eventType = EVENT_ANDROID_NAVIGATION_STARTED,
            eventData = mapOf(
                "entity_id" to entityId,
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Unable to send '$EVENT_ANDROID_NAVIGATION_STARTED' event")
    }
}
