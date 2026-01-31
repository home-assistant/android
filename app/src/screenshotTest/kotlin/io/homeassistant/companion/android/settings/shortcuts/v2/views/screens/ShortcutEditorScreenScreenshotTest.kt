package io.homeassistant.companion.android.settings.shortcuts.v2.views.screens

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutEditorUiState
import io.homeassistant.companion.android.util.compose.HAPreviews
import java.time.LocalDateTime
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

private const val DEFAULT_SERVER_ID = 1
private val fixedTimestamp: LocalDateTime = LocalDateTime.of(2024, 1, 1, 12, 0)
private val mockServers = listOf(
    Server(
        id = DEFAULT_SERVER_ID,
        _name = "Home",
        connection = ServerConnectionInfo(externalUrl = "https://home.example.com"),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    ),
).toImmutableList()
private val mockLovelaceDraft = ShortcutDraft(
    id = "dynamic_draft_1",
    serverId = DEFAULT_SERVER_ID,
    selectedIconName = null,
    label = "Shortcut",
    description = "Description",
    target = ShortcutTargetValue.Lovelace("/lovelace/shortcut"),
)
private val mockEntityDraft = ShortcutDraft(
    id = "dynamic_draft_1",
    serverId = DEFAULT_SERVER_ID,
    selectedIconName = null,
    label = "Lights",
    description = "Toggle living room lights",
    target = ShortcutTargetValue.Entity("light.living_room"),
)
private val mockPinnedDraft = ShortcutDraft(
    id = "pinned_1",
    serverId = DEFAULT_SERVER_ID,
    selectedIconName = null,
    label = "Pinned",
    description = "Pinned shortcut",
    target = ShortcutTargetValue.Lovelace("/lovelace/pinned"),
)
private val mockEntitiesByServer = persistentMapOf(
    DEFAULT_SERVER_ID to listOf(
        Entity(
            entityId = "light.living_room",
            state = "on",
            attributes = mapOf("friendly_name" to "Living Room"),
            lastChanged = fixedTimestamp,
            lastUpdated = fixedTimestamp,
        ),
    ).toImmutableList(),
)
private val mockEntityRegistryByServer = persistentMapOf(
    DEFAULT_SERVER_ID to listOf(
        EntityRegistryResponse(
            entityId = "light.living_room",
            areaId = "living_room",
            deviceId = "device_1",
        ),
    ).toImmutableList(),
)
private val mockDeviceRegistryByServer = persistentMapOf(
    DEFAULT_SERVER_ID to listOf(
        DeviceRegistryResponse(
            id = "device_1",
            name = "Ceiling Lights",
        ),
    ).toImmutableList(),
)
private val mockAreaRegistryByServer = persistentMapOf(
    DEFAULT_SERVER_ID to listOf(
        AreaRegistryResponse(
            areaId = "living_room",
            name = "Living Room",
        ),
    ).toImmutableList(),
)

class ShortcutEditorScreenScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutEditorScreen loading`() {
        val servers = mockServers
        val draft = mockLovelaceDraft
        HAThemeForPreview {
            ShortcutEditorScreen(
                state = ShortcutEditorUiState(
                    screen = ShortcutEditorScreenState(
                        isLoading = true,
                        servers = servers,
                    ),
                    editor = ShortcutEditorUiState.EditorState.DynamicCreate(
                        index = 0,
                        draftSeed = draft,
                    ),
                ),
                dispatch = { _: ShortcutEditAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutEditorScreen no servers`() {
        val draft = mockLovelaceDraft
        HAThemeForPreview {
            ShortcutEditorScreen(
                state = ShortcutEditorUiState(
                    screen = ShortcutEditorScreenState(
                        isLoading = false,
                        servers = emptyList<Server>().toImmutableList(),
                    ),
                    editor = ShortcutEditorUiState.EditorState.DynamicCreate(
                        index = 0,
                        draftSeed = draft,
                    ),
                ),
                dispatch = { _: ShortcutEditAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutEditorScreen slots full`() {
        val servers = mockServers
        val draft = mockLovelaceDraft
        HAThemeForPreview {
            ShortcutEditorScreen(
                state = ShortcutEditorUiState(
                    screen = ShortcutEditorScreenState(
                        isLoading = false,
                        servers = servers,
                        error = ShortcutError.SlotsFull,
                    ),
                    editor = ShortcutEditorUiState.EditorState.DynamicCreate(
                        index = 0,
                        draftSeed = draft,
                    ),
                ),
                dispatch = { _: ShortcutEditAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutEditorScreen dynamic lovelace create`() {
        val servers = mockServers
        val draft = mockLovelaceDraft
        HAThemeForPreview {
            ShortcutEditorScreen(
                state = ShortcutEditorUiState(
                    screen = ShortcutEditorScreenState(
                        isLoading = false,
                        servers = servers,
                    ),
                    editor = ShortcutEditorUiState.EditorState.DynamicCreate(
                        index = 0,
                        draftSeed = draft,
                    ),
                ),
                dispatch = { _: ShortcutEditAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutEditorScreen dynamic entity target`() {
        val servers = mockServers
        val entities = mockEntitiesByServer
        val entityRegistry = mockEntityRegistryByServer
        val deviceRegistry = mockDeviceRegistryByServer
        val areaRegistry = mockAreaRegistryByServer
        val draft = mockEntityDraft
        HAThemeForPreview {
            ShortcutEditorScreen(
                state = ShortcutEditorUiState(
                    screen = ShortcutEditorScreenState(
                        isLoading = false,
                        servers = servers,
                        entities = entities,
                        entityRegistry = entityRegistry,
                        deviceRegistry = deviceRegistry,
                        areaRegistry = areaRegistry,
                    ),
                    editor = ShortcutEditorUiState.EditorState.DynamicEdit(
                        index = 0,
                        draftSeed = draft,
                    ),
                ),
                dispatch = { _: ShortcutEditAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutEditorScreen pinned create`() {
        val servers = mockServers
        val draft = mockPinnedDraft
        HAThemeForPreview {
            ShortcutEditorScreen(
                state = ShortcutEditorUiState(
                    screen = ShortcutEditorScreenState(
                        isLoading = false,
                        servers = servers,
                    ),
                    editor = ShortcutEditorUiState.EditorState.PinnedCreate(
                        draftSeed = draft,
                    ),
                ),
                dispatch = { _: ShortcutEditAction -> },
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ShortcutEditorScreen pinned default`() {
        val servers = mockServers
        val draft = mockPinnedDraft
        HAThemeForPreview {
            ShortcutEditorScreen(
                state = ShortcutEditorUiState(
                    screen = ShortcutEditorScreenState(
                        isLoading = false,
                        servers = servers,
                    ),
                    editor = ShortcutEditorUiState.EditorState.PinnedEdit(
                        draftSeed = draft,
                    ),
                ),
                dispatch = { _: ShortcutEditAction -> },
            )
        }
    }
}
