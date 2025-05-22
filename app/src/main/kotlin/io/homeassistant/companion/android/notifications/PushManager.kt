package io.homeassistant.companion.android.notifications

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.notifications.PushProvider

interface PushManager {
    fun getProvider(clazz: Class<*>): PushProvider?

    fun getProvider(id: String): PushProvider?

    suspend fun getToken(): String?

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    val providers: Map<Class<*>, @JvmSuppressWildcards PushProvider>
}