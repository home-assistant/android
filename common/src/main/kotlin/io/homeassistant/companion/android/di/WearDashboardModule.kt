package io.homeassistant.companion.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardRepository
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardRepositoryImpl
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardStateCache
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardStateCacheImpl
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardUpdateCoordinator
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardUpdateCoordinatorImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WearDashboardModule {

    @Binds
    @Singleton
    internal abstract fun bindWearDashboardRepository(
        impl: WearDashboardRepositoryImpl,
    ): WearDashboardRepository

    @Binds
    @Singleton
    internal abstract fun bindWearDashboardStateCache(
        impl: WearDashboardStateCacheImpl,
    ): WearDashboardStateCache

    @Binds
    @Singleton
    internal abstract fun bindWearDashboardUpdateCoordinator(
        impl: WearDashboardUpdateCoordinatorImpl,
    ): WearDashboardUpdateCoordinator
}
