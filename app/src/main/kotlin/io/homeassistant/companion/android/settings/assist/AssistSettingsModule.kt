package io.homeassistant.companion.android.settings.assist

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistSettingsModule {

    @Binds
    @Singleton
    abstract fun assistRepository(assistRepositoryImpl: AssistRepositoryImpl): AssistRepository

    @Binds
    @Singleton
    abstract fun defaultAssistantManager(
        defaultAssistantManagerImpl: DefaultAssistantManagerImpl,
    ): DefaultAssistantManager
}
