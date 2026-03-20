package io.homeassistant.companion.android.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.push.PushProvider
import io.homeassistant.companion.android.common.push.PushRegistrationResult
import io.homeassistant.companion.android.unifiedpush.UnifiedPushManager
import javax.inject.Inject
import javax.inject.Singleton
import org.unifiedpush.android.connector.UnifiedPush
import timber.log.Timber

/**
 * Push provider implementation backed by UnifiedPush.
 *
 * UnifiedPush allows receiving push notifications via a user-chosen distributor app
 * (e.g. ntfy, NextPush) without relying on Google's FCM infrastructure.
 *
 * Priority 10 (preferred over FCM) because users who install a UnifiedPush distributor
 * explicitly want to avoid FCM.
 */
@Singleton
class UnifiedPushProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepository: PrefsRepository,
    private val unifiedPushManager: UnifiedPushManager
) : PushProvider {

    override val name: String = NAME

    override val priority: Int = 10

    override suspend fun isAvailable(): Boolean {
        val distributors = UnifiedPush.getDistributors(context)
        return distributors.isNotEmpty()
    }

    override suspend fun isActive(): Boolean =
        prefsRepository.isUnifiedPushEnabled()

    override suspend fun register(): PushRegistrationResult? {
        val distributor = UnifiedPush.getAckDistributor(context)
        if (distributor == null) {
            Timber.d("No UnifiedPush distributor acknowledged")
            return null
        }
        // Registration happens asynchronously via UnifiedPushReceiver.
        // The actual PushRegistrationResult will be created when onNewEndpoint is called.
        UnifiedPushManager.register(context)
        return null // Async - result delivered via UnifiedPushReceiver.onNewEndpoint
    }

    override suspend fun unregister() {
        UnifiedPushManager.unregister(context)
        prefsRepository.setUnifiedPushEnabled(false)
    }

    companion object {
        const val NAME = "UnifiedPush"
    }
}
