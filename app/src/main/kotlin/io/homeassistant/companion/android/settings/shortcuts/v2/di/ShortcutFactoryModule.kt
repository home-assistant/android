package io.homeassistant.companion.android.settings.shortcuts.v2.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutFactory

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ShortcutFactoryModule {
    @Binds
    abstract fun bindShortcutFactory(impl: WebViewShortcutFactory): ShortcutFactory
}
