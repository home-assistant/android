package io.homeassistant.companion.android.common.sensors

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {
    @Binds
    internal abstract fun bindSensorRepository(impl: SensorRepositoryImpl): SensorRepository
}
