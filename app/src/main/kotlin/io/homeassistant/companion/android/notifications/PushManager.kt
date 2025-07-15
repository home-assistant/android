package io.homeassistant.companion.android.notifications

import android.content.Context
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.notifications.PushProvider

/**
 * This interface manages push notification providers.
 *
 * **Key Responsibilities:**
 *   - Provide list of available providers.
 *   - Provide a default provider to use.
 *   - Enable the selected provider.
 *   - Get the push URL and token for the selected provider.
 */
interface PushManager {
    val defaultProvider: PushProvider?

    /**
     * Get the push provider of the given class.
     *
     * @return If a provider of the given class exists, this method returns the [PushProvider],
     * otherwise it returns `null`
     */
    fun getProvider(clazz: Class<*>): PushProvider?

    /**
     * Get the push provider of the given id.
     *
     * @return If a provider of the given id exists, this method returns the [PushProvider],
     * otherwise it returns `null`
     */
    fun getProvider(id: String): PushProvider?

    /**
     * Get the push provider that is enabled for the given server.
     *
     * @return If the server exists and a provider is enabled for it, this method returns
     * the [PushProvider], otherwise it returns `null`
     */
    fun getProviderForServer(context: Context, serverId: Int): PushProvider?

    /**
     * Get the push token for the default push provider.
     *
     * @return The default provider's token, or `null` if there is no default provider.
     */
    suspend fun getToken(): String?

    // Needs to be visible for testing to assert the content of the map.
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    val providers: Map<Class<*>, @JvmSuppressWildcards PushProvider>
}