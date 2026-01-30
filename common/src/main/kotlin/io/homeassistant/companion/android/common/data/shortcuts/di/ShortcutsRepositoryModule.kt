package io.homeassistant.companion.android.common.data.shortcuts.di

import android.content.Context
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutFactory
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutIntentCodec
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.ShortcutIntentCodecImpl
import io.homeassistant.companion.android.common.data.shortcuts.impl.ShortcutsRepositoryImpl
import java.util.Optional
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ShortcutsRepositoryModule {
    @BindsOptionalOf
    abstract fun bindShortcutFactory(): ShortcutFactory
}

@Module
@InstallIn(SingletonComponent::class)
internal object ShortcutsRepositoryProvidesModule {
    @Provides
    @Singleton
    fun provideShortcutIntentCodec(defaultCodec: ShortcutIntentCodecImpl): ShortcutIntentCodec = defaultCodec

    @Provides
    @Singleton
    fun providesShortcutsRepository(
        @ApplicationContext app: Context,
        serverManager: ServerManager,
        shortcutIntentCodec: ShortcutIntentCodec,
        shortcutFactory: Optional<ShortcutFactory>,
    ): ShortcutsRepository {
        val factory = requireNotNull(shortcutFactory.orElse(null)) {
            "ShortcutFactory not bound; cannot create ShortcutsRepositoryImpl"
        }
        return ShortcutsRepositoryImpl(
            app = app,
            serverManager = serverManager,
            shortcutFactory = factory,
            shortcutIntentCodec = shortcutIntentCodec,
        )
    }
}
