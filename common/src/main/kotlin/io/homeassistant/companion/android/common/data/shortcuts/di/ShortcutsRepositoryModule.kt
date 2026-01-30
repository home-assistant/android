package io.homeassistant.companion.android.common.data.shortcuts.di

import android.content.Context
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutFactory
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutIntentCodec
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.ShortcutIntentCodecImpl
import io.homeassistant.companion.android.common.data.shortcuts.impl.ShortcutsRepositoryImpl
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServerData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServersResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.mock.ShortcutsMockRepositoryImpl
import java.util.Optional
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ShortcutsRepositoryModule {
    @BindsOptionalOf
    abstract fun bindShortcutFactory(): ShortcutFactory

    companion object {
        @Provides
        @Singleton
        fun provideShortcutIntentCodec(defaultCodec: ShortcutIntentCodecImpl): ShortcutIntentCodec = defaultCodec

        // ⚠️ TEMPORARY: Mock switching logic for Shortcuts V2
        // This mock mechanism is intended ONLY for development/testing.
        // It MUST be removed before merging.
        @Provides
        @Singleton
        fun providesShortcutsRepository(
            @ApplicationContext app: Context,
            serverManager: ServerManager,
            shortcutIntentCodec: ShortcutIntentCodec,
            prefsRepository: PrefsRepository,
            mockRepository: ShortcutsMockRepositoryImpl,
            shortcutFactory: Optional<ShortcutFactory>,
        ): ShortcutsRepository {
            val defaultRepository = shortcutFactory.orElse(null)?.let {
                ShortcutsRepositoryImpl(
                    app = app,
                    serverManager = serverManager,
                    shortcutFactory = it,
                    shortcutIntentCodec = shortcutIntentCodec,
                )
            }

            val fallbackRepository = object : ShortcutsRepository {
                override val maxDynamicShortcuts: Int = 0
                override val canPinShortcuts: Boolean = false

                override suspend fun currentServerId(): Int = 0
                override suspend fun getServers(): ServersResult = ServersResult.NoServers
                override suspend fun loadServerData(serverId: Int): ServerData = ServerData()
                override suspend fun loadDynamicShortcuts(): Map<Int, ShortcutDraft> = emptyMap()
                override suspend fun loadPinnedShortcuts(): List<ShortcutDraft> = emptyList()

                override suspend fun upsertDynamicShortcut(index: Int, shortcut: ShortcutDraft) = Unit
                override suspend fun deleteDynamicShortcut(index: Int) = Unit
                override suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): PinResult = PinResult.NotSupported

                override suspend fun deletePinnedShortcut(shortcutId: String) = Unit
            }

            suspend fun activeRepository(): ShortcutsRepository {
                return if (prefsRepository.isShortcutsV2MockEnabled()) {
                    mockRepository
                } else {
                    defaultRepository ?: fallbackRepository
                }
            }

            fun activeRepositoryBlocking(): ShortcutsRepository = runBlocking { activeRepository() }

            return object : ShortcutsRepository {
                override val maxDynamicShortcuts: Int
                    get() = activeRepositoryBlocking().maxDynamicShortcuts

                override val canPinShortcuts: Boolean
                    get() = activeRepositoryBlocking().canPinShortcuts

                override suspend fun currentServerId(): Int = activeRepository().currentServerId()

                override suspend fun getServers() = activeRepository().getServers()

                override suspend fun loadServerData(serverId: Int): ServerData =
                    activeRepository().loadServerData(serverId)

                override suspend fun loadDynamicShortcuts(): Map<Int, ShortcutDraft> =
                    activeRepository().loadDynamicShortcuts()

                override suspend fun loadPinnedShortcuts(): List<ShortcutDraft> =
                    activeRepository().loadPinnedShortcuts()

                override suspend fun upsertDynamicShortcut(index: Int, shortcut: ShortcutDraft) {
                    activeRepository().upsertDynamicShortcut(index, shortcut)
                }

                override suspend fun deleteDynamicShortcut(index: Int) {
                    activeRepository().deleteDynamicShortcut(index)
                }

                override suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): PinResult {
                    return activeRepository().upsertPinnedShortcut(shortcut)
                }

                override suspend fun deletePinnedShortcut(shortcutId: String) {
                    activeRepository().deletePinnedShortcut(shortcutId)
                }
            }
        }
    }
}
