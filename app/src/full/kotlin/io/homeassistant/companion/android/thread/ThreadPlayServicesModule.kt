package io.homeassistant.companion.android.thread

import android.content.Context
import com.google.android.gms.threadnetwork.ThreadNetwork
import com.google.android.gms.threadnetwork.ThreadNetworkClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Play-Services-backed providers for [ThreadManagerImpl]. Lives in the `full` flavor only —
 * the `minimal` flavor's [ThreadManagerImpl] short-circuits without touching these types.
 *
 * Keeping the [ThreadNetworkClient] as an injected dependency lets [ThreadManagerImpl] expose
 * context-free public methods and makes the class fully unit-testable with a mocked client.
 */
@Module
@InstallIn(SingletonComponent::class)
object ThreadPlayServicesModule {

    @Provides
    @Singleton
    fun provideThreadNetworkClient(@ApplicationContext context: Context): ThreadNetworkClient =
        ThreadNetwork.getNetworkClient(context)
}
