package io.homeassistant.companion.android.thread

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ThreadModule {
    @Binds
    @Singleton
    abstract fun bindThreadManager(threadManager: ThreadManagerImpl): ThreadManager
}
