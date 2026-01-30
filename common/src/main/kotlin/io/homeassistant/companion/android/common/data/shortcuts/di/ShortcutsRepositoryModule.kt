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
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.DynamicEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.DynamicShortcutsData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinnedEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServersData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutsListData
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
                override suspend fun getServers(): ShortcutResult<ServersData> =
                    ShortcutResult.Error(ShortcutError.NoServers)
                override suspend fun loadShortcutsList(): ShortcutResult<ShortcutsListData> = ShortcutResult.Success(
                    ShortcutsListData(
                        dynamic = DynamicShortcutsData(maxDynamicShortcuts = 0, shortcuts = emptyMap()),
                        pinned = emptyList(),
                        pinnedError = ShortcutError.PinnedNotSupported,
                    ),
                )

                override suspend fun loadEditorData(): ShortcutResult<ShortcutEditorData> =
                    ShortcutResult.Error(ShortcutError.NoServers)

                override suspend fun loadDynamicEditor(index: Int): ShortcutResult<DynamicEditorData> =
                    ShortcutResult.Error(ShortcutError.InvalidIndex)

                override suspend fun loadDynamicEditorFirstAvailable(): ShortcutResult<DynamicEditorData> =
                    ShortcutResult.Error(ShortcutError.NoServers)

                override suspend fun loadPinnedEditor(shortcutId: String): ShortcutResult<PinnedEditorData> =
                    ShortcutResult.Error(ShortcutError.PinnedNotSupported)

                override suspend fun loadPinnedEditorForCreate(): ShortcutResult<PinnedEditorData> =
                    ShortcutResult.Error(ShortcutError.PinnedNotSupported)

                override suspend fun upsertDynamicShortcut(
                    index: Int,
                    shortcut: ShortcutDraft,
                    isEditing: Boolean,
                ): ShortcutResult<DynamicEditorData> = ShortcutResult.Error(ShortcutError.InvalidIndex)
                override suspend fun deleteDynamicShortcut(index: Int): ShortcutResult<Unit> =
                    ShortcutResult.Error(ShortcutError.InvalidIndex)
                override suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): ShortcutResult<PinResult> =
                    ShortcutResult.Error(ShortcutError.PinnedNotSupported)

                override suspend fun deletePinnedShortcut(shortcutId: String): ShortcutResult<Unit> =
                    ShortcutResult.Error(ShortcutError.PinnedNotSupported)
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
                override suspend fun getServers(): ShortcutResult<ServersData> = activeRepository().getServers()

                override suspend fun loadShortcutsList(): ShortcutResult<ShortcutsListData> =
                    activeRepository().loadShortcutsList()

                override suspend fun loadEditorData(): ShortcutResult<ShortcutEditorData> =
                    activeRepository().loadEditorData()

                override suspend fun loadDynamicEditor(index: Int): ShortcutResult<DynamicEditorData> =
                    activeRepository().loadDynamicEditor(index)

                override suspend fun loadDynamicEditorFirstAvailable(): ShortcutResult<DynamicEditorData> =
                    activeRepository().loadDynamicEditorFirstAvailable()

                override suspend fun loadPinnedEditor(shortcutId: String): ShortcutResult<PinnedEditorData> =
                    activeRepository().loadPinnedEditor(shortcutId)

                override suspend fun loadPinnedEditorForCreate(): ShortcutResult<PinnedEditorData> =
                    activeRepository().loadPinnedEditorForCreate()

                override suspend fun upsertDynamicShortcut(
                    index: Int,
                    shortcut: ShortcutDraft,
                    isEditing: Boolean,
                ): ShortcutResult<DynamicEditorData> {
                    return activeRepository().upsertDynamicShortcut(index, shortcut, isEditing)
                }

                override suspend fun deleteDynamicShortcut(index: Int): ShortcutResult<Unit> =
                    activeRepository().deleteDynamicShortcut(index)

                override suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): ShortcutResult<PinResult> =
                    activeRepository().upsertPinnedShortcut(shortcut)

                override suspend fun deletePinnedShortcut(shortcutId: String): ShortcutResult<Unit> =
                    activeRepository().deletePinnedShortcut(shortcutId)
            }
        }
    }
}
