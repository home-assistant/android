package io.homeassistant.companion.android.sensors.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timber.log.Timber

/**
 * Hilt bindings for Health Connect.
 *
 * Health Connect availability is dynamic: the SDK status can be unavailable, available, or
 * "update required" depending on the device, the user's account, and whether the Health
 * Connect APK is installed and current. The provider therefore returns a nullable client
 * — callers must null-check before use rather than crashing on devices where Health
 * Connect is not present.
 *
 * The provider is intentionally **not** `@Singleton`: callers receive `Provider<HealthConnectClient?>`
 * and treat each `provider.get()` as a fresh availability check. Caching as a singleton would
 * pin the result of the first SDK-status query to the lifetime of the app process, so a user
 * who installs or updates the Health Connect APK while the app is running would keep seeing
 * `null` until the next cold start. `HealthConnectClient.getOrCreate` is itself idempotent
 * and cheap on subsequent calls, so re-querying per `get()` is fine.
 */
@Module
@InstallIn(SingletonComponent::class)
object HealthConnectModule {
    @Provides
    fun providesHealthConnectClient(@ApplicationContext context: Context): HealthConnectClient? {
        return runCatching {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        }.onFailure {
            Timber.w(it, "Failed to obtain HealthConnectClient — feature will be disabled")
        }.getOrNull()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthConnectBindingsModule {
    @Binds
    @Singleton
    abstract fun bindHealthConnectWriteRepository(impl: HealthConnectWriteRepositoryImpl): HealthConnectWriteRepository
}
