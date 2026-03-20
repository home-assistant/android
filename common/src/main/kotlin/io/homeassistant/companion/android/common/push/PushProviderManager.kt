package io.homeassistant.companion.android.common.push

import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.MessagingToken
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Manages the selection and lifecycle of push notification providers.
 *
 * Providers are injected via Dagger multibinding and sorted by [PushProvider.priority].
 * The manager selects the first available provider and handles registration with the
 * Home Assistant server.
 */
@Singleton
class PushProviderManager @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards PushProvider>,
    private val serverManager: ServerManager,
) {
    private val sortedProviders: List<PushProvider> by lazy {
        providers.sortedBy { it.priority }
    }

    /**
     * Get the currently active push provider, if any.
     */
    suspend fun getActiveProvider(): PushProvider? = sortedProviders.firstOrNull { it.isActive() }

    /**
     * Get the best available provider based on priority.
     * Returns the highest-priority provider that reports itself as available.
     */
    suspend fun getBestAvailableProvider(): PushProvider? = sortedProviders.firstOrNull { it.isAvailable() }

    /**
     * Get all registered providers.
     */
    fun getAllProviders(): List<PushProvider> = sortedProviders

    /**
     * Get a provider by name.
     */
    fun getProvider(name: String): PushProvider? = sortedProviders.firstOrNull { it.name == name }

    /**
     * Select and register the best available push provider.
     * If a different provider was previously active, it will be unregistered first.
     *
     * @param preferredName Optional name of a preferred provider. If specified and available,
     *                      this provider will be used instead of the highest-priority one.
     * @return the [PushRegistrationResult] from the newly active provider, or null if none succeeded.
     */
    suspend fun selectAndRegister(preferredName: String? = null): PushRegistrationResult? {
        val currentActive = getActiveProvider()

        val target = if (preferredName != null) {
            val preferred = getProvider(preferredName)
            if (preferred != null && preferred.isAvailable()) {
                preferred
            } else {
                Timber.w("Preferred provider '$preferredName' is not available, falling back to best available")
                getBestAvailableProvider()
            }
        } else {
            getBestAvailableProvider()
        }

        if (target == null) {
            Timber.w("No push provider available")
            return null
        }

        // Unregister current if switching providers
        if (currentActive != null && currentActive.name != target.name) {
            Timber.d("Switching push provider from ${currentActive.name} to ${target.name}")
            currentActive.unregister()
        }

        Timber.d("Registering push provider: ${target.name}")
        return target.register()
    }

    /**
     * Update the server registration with the given push registration result.
     *
     * @param result The registration result from a push provider.
     * @param serverId The specific server ID to update, or null to update all default servers.
     */
    suspend fun updateServerRegistration(result: PushRegistrationResult, serverId: Int? = null) {
        if (!serverManager.isRegistered()) {
            Timber.d("Not updating registration: not authenticated")
            return
        }

        val deviceRegistration = DeviceRegistration(
            pushToken = MessagingToken(result.pushToken),
            pushUrl = result.pushUrl ?: "",
            pushEncrypt = result.encrypt,
        )

        val servers = if (serverId != null) {
            listOfNotNull(serverManager.getServer(serverId))
        } else {
            serverManager.servers()
        }

        servers.forEach { server ->
            try {
                serverManager.integrationRepository(server.id).updateRegistration(
                    deviceRegistration = deviceRegistration,
                    allowReregistration = false,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to update push registration for server ${server.id}")
            }
        }
    }
}
