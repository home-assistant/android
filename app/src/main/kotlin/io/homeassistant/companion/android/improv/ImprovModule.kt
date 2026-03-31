package io.homeassistant.companion.android.improv

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImprovModule {

    @Binds
    @Singleton
    abstract fun improvRepository(improvRepositoryImpl: ImprovRepositoryImpl): ImprovRepository
}
