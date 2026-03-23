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
 * Providers are injected via Dagger multibinding. The user chooses which provider
 * to use via the app settings — there is no automatic priority-based selection.
 */
@Singleton
class PushProviderManager @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards PushProvider>,
    private val serverManager: ServerManager,
) {

    /**
     * Get the currently active push provider, if any.
     */
    suspend fun getActiveProvider(): PushProvider? = providers.firstOrNull { it.isActive() }

    /**
     * Get all registered providers.
     */
    fun getAllProviders(): List<PushProvider> = providers.toList()

    /**
     * Get a provider by name.
     */
    fun getProvider(name: String): PushProvider? = providers.firstOrNull { it.name == name }

    /**
     * Register the user-selected push provider by name.
     * If a different provider was previously active, it will be unregistered first.
     *
     * @param providerName The name of the provider the user selected.
     * @return the [PushRegistrationResult] from the newly active provider, or null if registration failed.
     */
    suspend fun selectAndRegister(providerName: String): PushRegistrationResult? {
        val target = getProvider(providerName)
        if (target == null) {
            Timber.w("Provider '$providerName' not found")
            return null
        }
        if (!target.isAvailable()) {
            Timber.w("Provider '$providerName' is not available")
            return null
        }

        val currentActive = getActiveProvider()

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
