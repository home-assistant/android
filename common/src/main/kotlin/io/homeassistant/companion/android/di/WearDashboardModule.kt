package io.homeassistant.companion.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardRepository
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WearDashboardModule {

    @Binds
    @Singleton
    internal abstract fun bindWearDashboardRepository(
        impl: WearDashboardRepositoryImpl,
    ): WearDashboardRepository
}
