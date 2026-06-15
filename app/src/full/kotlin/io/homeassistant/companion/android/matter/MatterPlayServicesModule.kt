package io.homeassistant.companion.android.matter

import android.content.ComponentName
import android.content.Context
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Play-Services-backed providers for [MatterManagerImpl]. Lives in the `full` flavor only —
 * the `minimal` flavor's [MatterManagerImpl] short-circuits without touching these types.
 *
 * Keeping these as injected dependencies (rather than constructed on demand from a `Context`)
 * lets [MatterManagerImpl] expose context-free public methods and makes the class fully
 * unit-testable with a mocked [CommissioningClient].
 */
@Module
@InstallIn(SingletonComponent::class)
object MatterPlayServicesModule {

    @Provides
    @Singleton
    fun provideCommissioningClient(@ApplicationContext context: Context): CommissioningClient =
        Matter.getCommissioningClient(context)

    @Provides
    @Singleton
    @MatterCommissioningServiceComponent
    fun provideMatterCommissioningServiceComponent(@ApplicationContext context: Context): ComponentName =
        ComponentName(context, MatterCommissioningService::class.java)
}
