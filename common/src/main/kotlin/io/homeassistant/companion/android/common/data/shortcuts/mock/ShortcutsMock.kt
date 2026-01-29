package io.homeassistant.companion.android.common.data.shortcuts.mock

import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import java.time.LocalDateTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

internal object ShortcutsMock {
    private const val DEFAULT_SERVER_ID = 1
    private const val SECONDARY_SERVER_ID = 2

    val defaultServerId: Int get() = DEFAULT_SERVER_ID

    val servers: ImmutableList<Server> by lazy {
        persistentListOf(
            server(
                id = DEFAULT_SERVER_ID,
                name = "Mock Home",
                url = "https://home.example.local",
            ),
            server(
                id = SECONDARY_SERVER_ID,
                name = "Mock Office",
                url = "https://office.example.local",
            ),
        )
    }

    val entitiesByServer: ImmutableMap<Int, ImmutableList<Entity>> by lazy {
        persistentMapOf(
            DEFAULT_SERVER_ID to listOf(
                entity("light.living_room", state = "on", friendlyName = "Living Room"),
                entity("switch.coffee_maker", state = "off", friendlyName = "Coffee Maker"),
            ).toImmutableList(),
            SECONDARY_SERVER_ID to listOf(
                entity("light.conference_room", state = "on", friendlyName = "Conference Room"),
            ).toImmutableList(),
        )
    }

    val entityRegistryByServer: ImmutableMap<Int, ImmutableList<EntityRegistryResponse>> by lazy {
        persistentMapOf(
            DEFAULT_SERVER_ID to listOf(
                entityRegistry(entityId = "light.living_room", areaId = "living_room", deviceId = "device_living_room"),
                entityRegistry(entityId = "switch.coffee_maker", areaId = "kitchen", deviceId = "device_kitchen"),
            ).toImmutableList(),
            SECONDARY_SERVER_ID to listOf(
                entityRegistry(entityId = "light.conference_room", areaId = "conference_room", deviceId = "device_conference"),
            ).toImmutableList(),
        )
    }

    val deviceRegistryByServer: ImmutableMap<Int, ImmutableList<DeviceRegistryResponse>> by lazy {
        persistentMapOf(
            DEFAULT_SERVER_ID to listOf(
                deviceRegistry(id = "device_living_room", name = "Ceiling Lights"),
                deviceRegistry(id = "device_kitchen", name = "Coffee Maker"),
            ).toImmutableList(),
            SECONDARY_SERVER_ID to listOf(
                deviceRegistry(id = "device_conference", name = "Conference Lights"),
            ).toImmutableList(),
        )
    }

    val areaRegistryByServer: ImmutableMap<Int, ImmutableList<AreaRegistryResponse>> by lazy {
        persistentMapOf(
            DEFAULT_SERVER_ID to listOf(
                areaRegistry(areaId = "living_room", name = "Living Room"),
                areaRegistry(areaId = "kitchen", name = "Kitchen"),
            ).toImmutableList(),
            SECONDARY_SERVER_ID to listOf(
                areaRegistry(areaId = "conference_room", name = "Conference Room"),
            ).toImmutableList(),
        )
    }

    private val dynamicIcons: List<IIcon> by lazy {
        runCatching {
            listOf(
                CommunityMaterial.Icon2.cmd_lightbulb,
                CommunityMaterial.Icon3.cmd_view_dashboard,
                CommunityMaterial.Icon2.cmd_flash,
                CommunityMaterial.Icon.cmd_camera_image,
                CommunityMaterial.Icon3.cmd_map_marker,
            )
        }.getOrElse { emptyList() }
    }

    private val dynamicShortcutsStore: MutableList<ShortcutDraft> by lazy {
        mutableListOf(
            buildDynamicDraft(index = 0, type = ShortcutType.LOVELACE, serverId = DEFAULT_SERVER_ID),
            buildDynamicDraft(index = 1, type = ShortcutType.ENTITY_ID, serverId = DEFAULT_SERVER_ID),
        )
    }

    private val pinnedShortcutsStore: MutableList<ShortcutDraft> by lazy {
        MutableList(20) { i ->
            val number = i + 1
            ShortcutDraft(
                id = "pinned_$number",
                serverId = DEFAULT_SERVER_ID,
                selectedIcon = null,
                label = "Pinned $number",
                description = "Pinned shortcut $number",
                target = ShortcutTargetValue.Lovelace("/lovelace/pinned_$number"),
                isDirty = false,
            )
        }
    }

    fun dynamicShortcuts(): List<ShortcutDraft> = dynamicShortcutsStore.toList()
    fun pinnedShortcuts(): List<ShortcutDraft> = pinnedShortcutsStore.toList()

    fun upsertDynamic(index: Int, draft: ShortcutDraft) {
        val id = MockDynamicShortcutId.build(index)
        val normalized = draft.normalized(
            id = id,
            defaultServerId = DEFAULT_SERVER_ID,
        )

        dynamicShortcutsStore.replaceOrAdd(
            predicate = { it.id == id },
            value = normalized,
        )
    }

    fun removeDynamic(index: Int) {
        val id = MockDynamicShortcutId.build(index)
        dynamicShortcutsStore.removeAll { it.id == id }
    }

    fun removePinned(id: String) {
        if (id.isBlank()) return
        pinnedShortcutsStore.removeAll { it.id == id }
    }

    fun upsertPinned(draft: ShortcutDraft) {
        if (draft.id.isBlank()) return

        val normalized = draft.normalized(
            defaultServerId = DEFAULT_SERVER_ID,
        )

        pinnedShortcutsStore.replaceOrAdd(
            predicate = { it.id == normalized.id },
            value = normalized,
        )
    }

    private fun buildDynamicDraft(index: Int, type: ShortcutType, serverId: Int): ShortcutDraft {
        val number = index + 1
        val isEntity = type == ShortcutType.ENTITY_ID

        return ShortcutDraft(
            id = MockDynamicShortcutId.build(index),
            serverId = serverId,
            selectedIcon = dynamicIcons.getOrNull(index),
            label = if (isEntity) "Lights" else "Shortcut $number",
            description = if (isEntity) "Toggle living room lights" else "Description $number",
            target = if (isEntity) {
                ShortcutTargetValue.Entity("light.living_room")
            } else {
                ShortcutTargetValue.Lovelace("/lovelace/shortcut$number")
            },
            isDirty = false,
        )
    }

    private fun server(id: Int, name: String, url: String): Server = Server(
        id = id,
        _name = name,
        connection = ServerConnectionInfo(
            externalUrl = url,
            allowInsecureConnection = true,
        ),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    private fun entity(entityId: String, state: String, friendlyName: String): Entity {
        val now = LocalDateTime.now()
        return Entity(
            entityId = entityId,
            state = state,
            attributes = mapOf("friendly_name" to friendlyName),
            lastChanged = now,
            lastUpdated = now,
        )
    }

    private fun entityRegistry(entityId: String, areaId: String, deviceId: String) =
        EntityRegistryResponse(entityId = entityId, areaId = areaId, deviceId = deviceId)

    private fun deviceRegistry(id: String, name: String) =
        DeviceRegistryResponse(id = id, name = name)

    private fun areaRegistry(areaId: String, name: String) =
        AreaRegistryResponse(areaId = areaId, name = name)

    private fun ShortcutDraft.normalized(
        id: String = this.id,
        defaultServerId: Int,
    ): ShortcutDraft = copy(
        id = id,
        serverId = serverId.takeIf { it != 0 } ?: defaultServerId,
        isDirty = false,
    )

    private fun <T> MutableList<T>.replaceOrAdd(
        predicate: (T) -> Boolean,
        value: T,
    ) {
        val idx = indexOfFirst(predicate)
        if (idx >= 0) this[idx] = value else add(value)
    }
}

private const val MOCK_DYNAMIC_SHORTCUT_PREFIX = "shortcut"

internal object MockDynamicShortcutId {
    fun build(index: Int): String = "${MOCK_DYNAMIC_SHORTCUT_PREFIX}_${index + 1}"

    fun parse(shortcutId: String): Int? {
        if (!shortcutId.startsWith("${MOCK_DYNAMIC_SHORTCUT_PREFIX}_")) return null
        return shortcutId.substringAfterLast("_", missingDelimiterValue = "")
            .toIntOrNull()
            ?.minus(1)
            ?.takeIf { it >= 0 }
    }
}
