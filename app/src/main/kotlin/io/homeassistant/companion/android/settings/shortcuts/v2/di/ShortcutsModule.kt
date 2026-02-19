package io.homeassistant.companion.android.settings.shortcuts.v2.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutFactory
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.ShortcutsRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ShortcutsModule {
    @Binds
    @Singleton
    abstract fun bindShortcutFactory(impl: WebViewShortcutFactory): ShortcutFactory

    @Binds
    @Singleton
    abstract fun bindShortcutsRepository(impl: ShortcutsRepositoryImpl): ShortcutsRepository
}
