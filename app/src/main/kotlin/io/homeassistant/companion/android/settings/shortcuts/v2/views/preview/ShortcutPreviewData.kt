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
import io.homeassistant.companion.android.settings.shortcuts.v2.DynamicShortcutItem
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutEditorUiState
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutsListState
import io.homeassistant.companion.android.settings.shortcuts.v2.views.screens.ShortcutEditorScreenState
import java.time.LocalDateTime

private const val PREVIEW_DYNAMIC_SHORTCUT_PREFIX = "shortcut"
private const val PREVIEW_DYNAMIC_DRAFT_PREFIX = "dynamic_draft"

internal object ShortcutPreviewData {
    internal fun dynamicShortcutId(index: Int): String {
        return "${PREVIEW_DYNAMIC_SHORTCUT_PREFIX}_${index + 1}"
    }

    internal fun dynamicDraftSeedId(index: Int): String {
        return "${PREVIEW_DYNAMIC_DRAFT_PREFIX}_${index + 1}"
    }

    fun buildDynamicEditorState(
        selectedIndex: Int = 0,
        draftSeed: ShortcutDraft = buildDraft(id = dynamicDraftSeedId(selectedIndex)),
        isEditing: Boolean = true,
    ): ShortcutEditorUiState.EditorState.Dynamic {
        return if (isEditing) {
            ShortcutEditorUiState.EditorState.DynamicEdit(
                index = selectedIndex,
                draftSeed = draftSeed,
            )
        } else {
            ShortcutEditorUiState.EditorState.DynamicCreate(
                index = selectedIndex,
                draftSeed = draftSeed,
            )
        }
    }

    fun buildPinnedEditorState(
        pinnedDraft: ShortcutDraft = buildPinnedDraft(),
        isEditing: Boolean = true,
    ): ShortcutEditorUiState.EditorState.Pinned {
        return if (isEditing) {
            ShortcutEditorUiState.EditorState.PinnedEdit(
                draftSeed = pinnedDraft,
            )
        } else {
            ShortcutEditorUiState.EditorState.PinnedCreate(
                draftSeed = pinnedDraft,
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
        id: String = dynamicDraftSeedId(0),
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

    fun buildDynamicDrafts(count: Int, type: ShortcutType): List<ShortcutDraft> {
        return List(count) { index ->
            val number = index + 1
            ShortcutDraft(
                id = dynamicShortcutId(index),
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

    fun buildDynamicSummaries(count: Int, type: ShortcutType): List<ShortcutSummary> {
        return buildDynamicDrafts(count = count, type = type).map { draft ->
            ShortcutSummary(
                id = draft.id,
                selectedIconName = draft.selectedIconName,
                label = draft.label,
            )
        }
    }

    fun buildPinnedDraft(): ShortcutDraft {
        return ShortcutDraft(
            id = "pinned_1",
            serverId = 1,
            selectedIconName = null,
            label = "Pinned",
            description = "Pinned shortcut",
            target = ShortcutTargetValue.Lovelace("/lovelace/pinned"),
        )
    }

    fun buildPinnedSummaries(): List<ShortcutSummary> {
        return listOf(
            ShortcutSummary(
                id = "pinned_1",
                selectedIconName = null,
                label = "Pinned",
            ),
        )
    }

    fun buildListState(
        isLoading: Boolean = false,
        error: ShortcutError? = null,
        maxDynamicShortcuts: Int = 5,
        dynamicSummaries: List<ShortcutSummary> = buildDynamicSummaries(
            count = 2,
            type = ShortcutType.LOVELACE,
        ),
        pinnedSummaries: List<ShortcutSummary> = buildPinnedSummaries(),
        canPinShortcuts: Boolean = true,
    ): ShortcutsListState {
        val dynamicItems = dynamicSummaries.mapIndexed { index, summary ->
            DynamicShortcutItem(index, summary)
        }
        val pinnedItems = if (canPinShortcuts) pinnedSummaries else emptyList()
        return ShortcutsListState(
            isLoading = isLoading,
            error = error,
            pinnedError = if (canPinShortcuts) null else ShortcutError.PinnedNotSupported,
            maxDynamicShortcuts = maxDynamicShortcuts,
            dynamicItems = dynamicItems,
            pinnedItems = pinnedItems,
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
