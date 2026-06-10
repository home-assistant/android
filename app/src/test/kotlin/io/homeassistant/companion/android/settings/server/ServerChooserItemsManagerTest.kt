package io.homeassistant.companion.android.settings.server

import android.graphics.Bitmap
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.util.ServerUserAvatarRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerChooserItemsManagerTest {

    private val serverManager: ServerManager = mockk()
    private val serverUserAvatarRepository: ServerUserAvatarRepository = mockk()

    private val chooserItemsManager = ServerChooserItemsManager(
        serverManager = serverManager,
        serverUserAvatarRepository = serverUserAvatarRepository,
    )

    private fun server(id: Int, name: String, userName: String?): Server = Server(
        id = id,
        _name = name,
        connection = ServerConnectionInfo(externalUrl = ""),
        session = ServerSessionInfo(),
        user = ServerUserInfo(id = "uid-$id", name = userName),
    )

    @Test
    fun `Given servers with named users when invoked then maps the user and server names`() = runTest {
        coEvery { serverManager.getServer() } returns null
        coEvery { serverUserAvatarRepository.getUserAvatar(any()) } returns null

        val items = chooserItemsManager(listOf(server(1, "Home", "Alice"), server(2, "Office", "Bob")))

        assertEquals(
            listOf(
                ServerChooserItem(serverId = 1, userName = "Alice", serverName = "Home", userAvatar = null),
                ServerChooserItem(serverId = 2, userName = "Bob", serverName = "Office", userAvatar = null),
            ),
            items,
        )
    }

    @Test
    fun `Given a user without a name when invoked then the user name falls back to the server name`() = runTest {
        coEvery { serverManager.getServer() } returns null
        coEvery { serverUserAvatarRepository.getUserAvatar(any()) } returns null

        val items = chooserItemsManager(listOf(server(1, "Home", userName = null)))

        assertEquals("Home", items.single().userName)
    }

    @Test
    fun `Given the avatar manager resolves a picture when invoked then it is carried in the item`() = runTest {
        val avatar = mockk<Bitmap>()
        coEvery { serverManager.getServer() } returns null
        coEvery { serverUserAvatarRepository.getUserAvatar(1) } returns avatar

        val items = chooserItemsManager(listOf(server(1, "Home", "Alice")))

        assertSame(avatar, items.single().userAvatar)
    }

    @Test
    fun `Given an active server when invoked then only that item is marked active`() = runTest {
        coEvery { serverManager.getServer() } returns server(2, "Office", "Bob")
        coEvery { serverUserAvatarRepository.getUserAvatar(any()) } returns null

        val items = chooserItemsManager(listOf(server(1, "Home", "Alice"), server(2, "Office", "Bob")))

        assertFalse(items.single { it.serverId == 1 }.isActive)
        assertTrue(items.single { it.serverId == 2 }.isActive)
    }
}
