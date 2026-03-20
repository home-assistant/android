package io.homeassistant.companion.android.common.push

/**
 * Abstraction for push notification providers (FCM, UnifiedPush, WebSocket).
 *
 * Each provider is responsible for:
 * - Managing its own registration lifecycle
 * - Providing the token/endpoint used to receive notifications
 * - Indicating whether it requires a persistent connection (e.g. WebSocket)
 *
 * Implementations should be registered via Dagger multibinding so that
 * [PushProviderManager] can select the best available provider at runtime.
 */
interface PushProvider {

    /** Human-readable name used for logging and notification source attribution. */
    val name: String

    /**
     * Priority for provider selection. Lower values indicate higher priority.
     * When multiple providers are available the one with the lowest priority value is preferred.
     *
     * Suggested values:
     * - UnifiedPush: 10
     * - FCM: 20
     * - WebSocket: 30
     */
    val priority: Int

    /** Whether this provider is currently available on this device/build. */
    suspend fun isAvailable(): Boolean

    /**
     * Whether this provider is currently the active push provider.
     * An active provider is one that has been successfully registered and is delivering messages.
     */
    suspend fun isActive(): Boolean

    /**
     * Attempt to register this provider.
     *
     * @return a [PushRegistrationResult] describing the token/endpoint to send to the server,
     *         or `null` if registration failed.
     */
    suspend fun register(): PushRegistrationResult?

    /**
     * Unregister this provider. Called when switching to a different provider
     * or when the user disables push notifications.
     */
    suspend fun unregister()

    /**
     * Whether this provider uses a persistent connection (like WebSocket)
     * rather than a push endpoint.
     */
    val requiresPersistentConnection: Boolean get() = false
}

/**
 * Result of a successful push provider registration.
 *
 * @property pushToken The token or key used to identify this device.
 *                     For FCM this is the FCM token; for UnifiedPush this is the public key pair.
 * @property pushUrl   The URL where the server should send push notifications.
 *                     Empty or null for FCM (uses built-in URL); non-empty for UnifiedPush.
 * @property encrypt   Whether the server should encrypt notifications before sending.
 */
data class PushRegistrationResult(val pushToken: String, val pushUrl: String? = null, val encrypt: Boolean = false)
