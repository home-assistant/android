package io.homeassistant.companion.android.matter

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MatterModule {

    @Binds
    @Singleton
    abstract fun bindMatterManager(matterManager: MatterManagerImpl): MatterManager
}
