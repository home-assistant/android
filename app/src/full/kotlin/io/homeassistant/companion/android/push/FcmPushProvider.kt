package io.homeassistant.companion.android.push

import io.homeassistant.companion.android.common.push.PushProvider
import io.homeassistant.companion.android.common.push.PushRegistrationResult
import io.homeassistant.companion.android.common.util.MessagingTokenProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Push provider implementation backed by Firebase Cloud Messaging.
 *
 * Only available in the "full" build flavor.
 */
@Singleton
class FcmPushProvider @Inject constructor(private val messagingTokenProvider: MessagingTokenProvider) : PushProvider {

    override val name: String = NAME

    override suspend fun isAvailable(): Boolean {
        return try {
            val token = messagingTokenProvider()
            !token.isBlank()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "FCM is not available")
            false
        }
    }

    override suspend fun isActive(): Boolean {
        return try {
            val token = messagingTokenProvider()
            !token.isBlank()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    override suspend fun register(): PushRegistrationResult? {
        return try {
            val token = messagingTokenProvider()
            if (token.isBlank()) {
                Timber.w("FCM token is blank")
                null
            } else {
                PushRegistrationResult(
                    pushToken = token.value,
                    pushUrl = "", // Empty URL means use built-in push URL
                    encrypt = false,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
