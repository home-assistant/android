package io.homeassistant.companion.android.push

import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.push.PushProvider
import io.homeassistant.companion.android.common.push.PushRegistrationResult
import io.homeassistant.companion.android.onboarding.getMessagingToken
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Push provider implementation backed by Firebase Cloud Messaging.
 *
 * Only available in the "full" build flavor. Lower priority than UnifiedPush
 * so that users with a UnifiedPush distributor will use that instead.
 */
@Singleton
class FcmPushProvider @Inject constructor(
    private val prefsRepository: PrefsRepository
) : PushProvider {

    override val name: String = NAME

    override val priority: Int = 20

    override suspend fun isAvailable(): Boolean {
        return try {
            val token = getMessagingToken()
            token.isNotBlank()
        } catch (e: Exception) {
            Timber.e(e, "FCM is not available")
            false
        }
    }

    override suspend fun isActive(): Boolean {
        // FCM is active only when UnifiedPush is not enabled and a valid token exists.
        if (prefsRepository.isUnifiedPushEnabled()) return false
        return try {
            val token = getMessagingToken()
            token.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun register(): PushRegistrationResult? {
        return try {
            val token = getMessagingToken()
            if (token.isBlank()) {
                Timber.w("FCM token is blank")
                null
            } else {
                PushRegistrationResult(
                    pushToken = token,
                    pushUrl = "", // Empty URL means use built-in push URL
                    encrypt = false
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to register FCM")
            null
        }
    }

    override suspend fun unregister() {
        // FCM doesn't need explicit unregistration in this context.
        // Token invalidation is handled by Firebase automatically.
        Timber.d("FCM unregister called (no-op)")
    }

    companion object {
        const val NAME = "FCM"
    }
}
