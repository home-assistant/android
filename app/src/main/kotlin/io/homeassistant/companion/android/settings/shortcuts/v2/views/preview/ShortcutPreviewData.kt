package io.homeassistant.companion.android.settings.shortcuts.v2.views.preview

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutSummary
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.settings.shortcuts.v2.AppShortcutItem
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutEditorUiState
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutsListState
import io.homeassistant.companion.android.settings.shortcuts.v2.views.screens.ShortcutEditorScreenState
import java.time.LocalDateTime

private const val PREVIEW_APP_SHORTCUT_PREFIX = "shortcut"
private const val PREVIEW_APP_DRAFT_PREFIX = "app_draft"

internal object ShortcutPreviewData {
    internal fun appShortcutId(index: Int): String {
        return "${PREVIEW_APP_SHORTCUT_PREFIX}_${index + 1}"
    }

    internal fun appDraftSeedId(index: Int): String {
        return "${PREVIEW_APP_DRAFT_PREFIX}_${index + 1}"
    }

    fun buildAppEditorState(
        selectedIndex: Int = 0,
        draftSeed: ShortcutDraft = buildDraft(id = appDraftSeedId(selectedIndex)),
        isEditing: Boolean = true,
    ): ShortcutEditorUiState.EditorState.App {
        return if (isEditing) {
            ShortcutEditorUiState.EditorState.AppEdit(
                index = selectedIndex,
                draftSeed = draftSeed,
            )
        } else {
            ShortcutEditorUiState.EditorState.AppCreate(
                index = selectedIndex,
                draftSeed = draftSeed,
            )
        }
    }

    fun buildHomeEditorState(
        homeDraft: ShortcutDraft = buildHomeDraft(),
        isEditing: Boolean = true,
    ): ShortcutEditorUiState.EditorState.Home {
        return if (isEditing) {
            ShortcutEditorUiState.EditorState.HomeEdit(
                draftSeed = homeDraft,
            )
        } else {
            ShortcutEditorUiState.EditorState.HomeCreate(
                draftSeed = homeDraft,
            )
        }
    }

    fun buildScreenState(
        isLoading: Boolean = false,
        isSaving: Boolean = false,
        servers: List<Server> = previewServers,
        entities: Map<Int, List<Entity>> = emptyMap(),
        entityRegistry: Map<Int, List<EntityRegistryResponse>> = emptyMap(),
        deviceRegistry: Map<Int, List<DeviceRegistryResponse>> = emptyMap(),
        areaRegistry: Map<Int, List<AreaRegistryResponse>> = emptyMap(),
    ): ShortcutEditorScreenState {
        return ShortcutEditorScreenState(
            isLoading = isLoading,
            isSaving = isSaving,
            servers = servers,
            entities = entities,
            entityRegistry = entityRegistry,
            deviceRegistry = deviceRegistry,
            areaRegistry = areaRegistry,
        )
    }

    fun buildDraft(
        type: ShortcutType = ShortcutType.LOVELACE,
        id: String = appDraftSeedId(0),
        serverId: Int = 1,
    ): ShortcutDraft {
        return ShortcutDraft(
            id = id,
            serverId = serverId,
            selectedIconName = null,
            label = if (type == ShortcutType.ENTITY_ID) "Lights" else "Shortcut",
            description = if (type == ShortcutType.ENTITY_ID) "Toggle living room lights" else "Description",
            target = if (type == ShortcutType.ENTITY_ID) {
                ShortcutTargetValue.Entity("light.living_room")
            } else {
                ShortcutTargetValue.Lovelace("/lovelace/shortcut")
            },
        )
    }

    fun buildAppDrafts(count: Int, type: ShortcutType): List<ShortcutDraft> {
        return List(count) { index ->
            val number = index + 1
            ShortcutDraft(
                id = appShortcutId(index),
                serverId = 1,
                selectedIconName = null,
                label = if (type == ShortcutType.ENTITY_ID) "Lights" else "Shortcut $number",
                description = if (type == ShortcutType.ENTITY_ID) {
                    "Toggle living room lights"
                } else {
                    "Description $number"
                },
                target = if (type == ShortcutType.ENTITY_ID) {
                    ShortcutTargetValue.Entity("light.living_room")
                } else {
                    ShortcutTargetValue.Lovelace("/lovelace/shortcut$number")
                },
            )
        }
    }

    fun buildAppSummaries(count: Int, type: ShortcutType): List<ShortcutSummary> {
        return buildAppDrafts(count = count, type = type).map { draft ->
            ShortcutSummary(
                id = draft.id,
                selectedIconName = draft.selectedIconName,
                label = draft.label,
            )
        }
    }

    fun buildHomeDraft(): ShortcutDraft {
        return ShortcutDraft(
            id = "pinned_1",
            serverId = 1,
            selectedIconName = null,
            label = "Home",
            description = "Home shortcut",
            target = ShortcutTargetValue.Lovelace("/lovelace/home"),
        )
    }

    fun buildHomeSummaries(): List<ShortcutSummary> {
        return listOf(
            ShortcutSummary(
                id = "pinned_1",
                selectedIconName = null,
                label = "Home",
            ),
        )
    }

    fun buildListState(
        isLoading: Boolean = false,
        error: ShortcutError? = null,
        maxAppShortcuts: Int = 5,
        appSummaries: List<ShortcutSummary> = buildAppSummaries(
            count = 2,
            type = ShortcutType.LOVELACE,
        ),
        homeSummaries: List<ShortcutSummary> = buildHomeSummaries(),
        isHomeSupported: Boolean = true,
    ): ShortcutsListState {
        val appItems = appSummaries.mapIndexed { index, summary ->
            AppShortcutItem(index, summary)
        }
        val homeItems = if (isHomeSupported) homeSummaries else emptyList()
        return ShortcutsListState(
            isLoading = isLoading,
            error = error,
            homeShortcutError = if (isHomeSupported) null else ShortcutError.HomeShortcutNotSupported,
            maxAppShortcuts = maxAppShortcuts,
            appShortcutItems = appItems,
            homeShortcutItems = homeItems,
        )
    }

    val previewServers = listOf(
        Server(
            id = 1,
            _name = "Home",
            connection = ServerConnectionInfo(externalUrl = "https://home.example.com"),
            session = ServerSessionInfo(),
            user = ServerUserInfo(),
        ),
        Server(
            id = 2,
            _name = "Office",
            connection = ServerConnectionInfo(externalUrl = "https://office.example.com"),
            session = ServerSessionInfo(),
            user = ServerUserInfo(),
        ),
    )

    val previewEntitiesByServer = mapOf(
        1 to listOf(
            Entity(
                entityId = "light.living_room",
                state = "on",
                attributes = mapOf("friendly_name" to "Living Room"),
                lastChanged = LocalDateTime.now(),
                lastUpdated = LocalDateTime.now(),
            ),
        ),
    )

    val previewEntityRegistryByServer = mapOf(
        1 to listOf(
            EntityRegistryResponse(
                entityId = "light.living_room",
                areaId = "living_room",
                deviceId = "device_1",
            ),
        ),
    )

    val previewDeviceRegistryByServer = mapOf(
        1 to listOf(
            DeviceRegistryResponse(
                id = "device_1",
                name = "Ceiling Lights",
            ),
        ),
    )

    val previewAreaRegistryByServer = mapOf(
        1 to listOf(
            AreaRegistryResponse(
                areaId = "living_room",
                name = "Living Room",
            ),
        ),
    )
}
