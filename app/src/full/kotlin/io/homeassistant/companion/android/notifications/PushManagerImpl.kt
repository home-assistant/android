package io.homeassistant.companion.android.notifications

import io.homeassistant.companion.android.common.notifications.PushProvider
import javax.inject.Inject

class PushManagerImpl @Inject constructor(
    providers: Map<Class<*>, @JvmSuppressWildcards PushProvider>
) : PushManagerBase(providers) {
    override val defaultProvider: PushProvider?
        get() = providers[FirebasePushProvider::class.java]!!
}
