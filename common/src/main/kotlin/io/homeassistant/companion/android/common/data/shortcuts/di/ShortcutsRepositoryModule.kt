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
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutRepositoryError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutRepositoryResult
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
                override suspend fun getServers(): ShortcutRepositoryResult<ServersData> =
                    ShortcutRepositoryResult.Error(ShortcutRepositoryError.NoServers)
                override suspend fun loadShortcutsList(): ShortcutRepositoryResult<ShortcutsListData> =
                    ShortcutRepositoryResult.Success(
                        ShortcutsListData(
                            dynamic = DynamicShortcutsData(maxDynamicShortcuts = 0, shortcuts = emptyMap()),
                            pinned = emptyList(),
                            pinnedError = ShortcutRepositoryError.PinnedNotSupported,
                        ),
                    )

                override suspend fun loadEditorData(): ShortcutRepositoryResult<ShortcutEditorData> =
                    ShortcutRepositoryResult.Error(ShortcutRepositoryError.NoServers)

                override suspend fun loadDynamicEditor(index: Int): ShortcutRepositoryResult<DynamicEditorData> =
                    ShortcutRepositoryResult.Error(ShortcutRepositoryError.InvalidIndex)

                override suspend fun loadDynamicEditorFirstAvailable(): ShortcutRepositoryResult<DynamicEditorData> =
                    ShortcutRepositoryResult.Error(ShortcutRepositoryError.NoServers)

                override suspend fun loadPinnedEditor(shortcutId: String): ShortcutRepositoryResult<PinnedEditorData> =
                    ShortcutRepositoryResult.Error(ShortcutRepositoryError.PinnedNotSupported)

                override suspend fun loadPinnedEditorForCreate(): ShortcutRepositoryResult<PinnedEditorData> =
                    ShortcutRepositoryResult.Error(ShortcutRepositoryError.PinnedNotSupported)

                override suspend fun upsertDynamicShortcut(
                    index: Int,
                    shortcut: ShortcutDraft,
                    isEditing: Boolean,
                ): ShortcutRepositoryResult<DynamicEditorData> =
                    ShortcutRepositoryResult.Error(ShortcutRepositoryError.InvalidIndex)
                override suspend fun deleteDynamicShortcut(index: Int): ShortcutRepositoryResult<Unit> =
                    ShortcutRepositoryResult.Error(ShortcutRepositoryError.InvalidIndex)
                override suspend fun upsertPinnedShortcut(
                    shortcut: ShortcutDraft,
                ): ShortcutRepositoryResult<PinResult> =
                    ShortcutRepositoryResult.Error(ShortcutRepositoryError.PinnedNotSupported)

                override suspend fun deletePinnedShortcut(shortcutId: String): ShortcutRepositoryResult<Unit> =
                    ShortcutRepositoryResult.Error(ShortcutRepositoryError.PinnedNotSupported)
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
                override suspend fun getServers(): ShortcutRepositoryResult<ServersData> =
                    activeRepository().getServers()

                override suspend fun loadShortcutsList(): ShortcutRepositoryResult<ShortcutsListData> =
                    activeRepository().loadShortcutsList()

                override suspend fun loadEditorData(): ShortcutRepositoryResult<ShortcutEditorData> =
                    activeRepository().loadEditorData()

                override suspend fun loadDynamicEditor(index: Int): ShortcutRepositoryResult<DynamicEditorData> =
                    activeRepository().loadDynamicEditor(index)

                override suspend fun loadDynamicEditorFirstAvailable(): ShortcutRepositoryResult<DynamicEditorData> =
                    activeRepository().loadDynamicEditorFirstAvailable()

                override suspend fun loadPinnedEditor(shortcutId: String): ShortcutRepositoryResult<PinnedEditorData> =
                    activeRepository().loadPinnedEditor(shortcutId)

                override suspend fun loadPinnedEditorForCreate(): ShortcutRepositoryResult<PinnedEditorData> =
                    activeRepository().loadPinnedEditorForCreate()

                override suspend fun upsertDynamicShortcut(
                    index: Int,
                    shortcut: ShortcutDraft,
                    isEditing: Boolean,
                ): ShortcutRepositoryResult<DynamicEditorData> {
                    return activeRepository().upsertDynamicShortcut(index, shortcut, isEditing)
                }

                override suspend fun deleteDynamicShortcut(index: Int): ShortcutRepositoryResult<Unit> =
                    activeRepository().deleteDynamicShortcut(index)

                override suspend fun upsertPinnedShortcut(
                    shortcut: ShortcutDraft,
                ): ShortcutRepositoryResult<PinResult> = activeRepository().upsertPinnedShortcut(shortcut)

                override suspend fun deletePinnedShortcut(shortcutId: String): ShortcutRepositoryResult<Unit> =
                    activeRepository().deletePinnedShortcut(shortcutId)
            }
        }
    }
}
