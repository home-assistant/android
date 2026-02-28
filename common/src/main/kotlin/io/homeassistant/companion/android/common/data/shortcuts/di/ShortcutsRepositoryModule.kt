package io.homeassistant.companion.android.common.data.shortcuts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutIntentCodec
import io.homeassistant.companion.android.common.data.shortcuts.impl.ShortcutIntentCodecImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ShortcutsRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindShortcutIntentCodec(impl: ShortcutIntentCodecImpl): ShortcutIntentCodec
}
