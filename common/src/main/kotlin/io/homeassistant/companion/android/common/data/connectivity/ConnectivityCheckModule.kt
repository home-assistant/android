package io.homeassistant.companion.android.common.data.connectivity

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityCheckModule {

    @Binds
    abstract fun bindConnectivityCheckRepository(impl: ConnectivityCheckRepositoryImpl): ConnectivityCheckRepository

    @Binds
    abstract fun bindConnectivityChecker(impl: DefaultConnectivityChecker): ConnectivityChecker
}
