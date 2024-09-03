package io.homeassistant.companion.android.common.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.widgets.GraphWidgetRepository
import io.homeassistant.companion.android.common.data.widgets.GraphWidgetRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindGraphWidgetRepository(
        impl: GraphWidgetRepositoryImpl
    ): GraphWidgetRepository
}
