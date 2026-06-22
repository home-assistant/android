package io.homeassistant.companion.android.settings.server

import android.graphics.Bitmap
import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.util.ServerUserAvatarUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerChooserItemsUseCaseTest {

    private val serverManager: ServerManager = mockk()
    private val serverUserAvatarUseCase: ServerUserAvatarUseCase = mockk()

    private val chooserItemsUseCase = ServerChooserItemsUseCase(
        serverManager = serverManager,
        serverUserAvatarUseCase = serverUserAvatarUseCase,
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
        coEvery { serverUserAvatarUseCase.getUserAvatar(any()) } returns null

        chooserItemsUseCase(listOf(server(1, "Home", "Alice"), server(2, "Office", "Bob"))).test {
            assertEquals(
                listOf(
                    ServerChooserItem(serverId = 1, userName = "Alice", serverName = "Home", userAvatar = null),
                    ServerChooserItem(serverId = 2, userName = "Bob", serverName = "Office", userAvatar = null),
                ),
                awaitItem(),
            )
            awaitComplete()
        }
    }

    @Test
    fun `Given a user without a name when invoked then the user name falls back to the server name`() = runTest {
        coEvery { serverManager.getServer() } returns null
        coEvery { serverUserAvatarUseCase.getUserAvatar(any()) } returns null

        chooserItemsUseCase(listOf(server(1, "Home", userName = null))).test {
            assertEquals("Home", awaitItem().single().userName)
            awaitComplete()
        }
    }

    @Test
    fun `Given the avatar manager resolves a picture when invoked then a later emission carries it`() = runTest {
        val avatar = mockk<Bitmap>()
        coEvery { serverManager.getServer() } returns null
        coEvery { serverUserAvatarUseCase.getUserAvatar(1) } returns avatar

        chooserItemsUseCase(listOf(server(1, "Home", "Alice"))).test {
            // First emission renders immediately without the avatar (the view shows initials).
            assertNull(awaitItem().single().userAvatar)
            // Once the avatar resolves, the list is re-emitted with it populated.
            assertSame(avatar, awaitItem().single().userAvatar)
            awaitComplete()
        }
    }

    @Test
    fun `Given an active server when invoked then only that item is marked active`() = runTest {
        coEvery { serverManager.getServer() } returns server(2, "Office", "Bob")
        coEvery { serverUserAvatarUseCase.getUserAvatar(any()) } returns null

        chooserItemsUseCase(listOf(server(1, "Home", "Alice"), server(2, "Office", "Bob"))).test {
            val items = awaitItem()
            assertFalse(items.single { it.serverId == 1 }.isActive)
            assertTrue(items.single { it.serverId == 2 }.isActive)
            awaitComplete()
        }
    }

    @Test
    fun `Given multiple servers when invoked then first emission has no avatars and avatars load concurrently`() = runTest {
        coEvery { serverManager.getServer() } returns null
        val avatar1 = mockk<Bitmap>()
        val avatar2 = mockk<Bitmap>()
        val release1 = CompletableDeferred<Bitmap>()
        val release2 = CompletableDeferred<Bitmap>()
        val started1 = CompletableDeferred<Unit>()
        val started2 = CompletableDeferred<Unit>()
        coEvery { serverUserAvatarUseCase.getUserAvatar(1) } coAnswers {
            started1.complete(Unit)
            release1.await()
        }
        coEvery { serverUserAvatarUseCase.getUserAvatar(2) } coAnswers {
            started2.complete(Unit)
            release2.await()
        }

        chooserItemsUseCase(listOf(server(1, "Home", "Alice"), server(2, "Office", "Bob"))).test {
            // The first emission renders before any avatar has loaded.
            val initial = awaitItem()
            assertNull(initial.single { it.serverId == 1 }.userAvatar)
            assertNull(initial.single { it.serverId == 2 }.userAvatar)

            // Both loads are in flight before either resolves: they run concurrently, not
            // sequentially. If loading were sequential, getUserAvatar(2) would never start (and this
            // await would hang the test) until avatar 1 had resolved.
            started1.await()
            started2.await()
            expectNoEvents()

            // Emissions follow completion order, not server order: resolve server 2 first.
            release2.complete(avatar2)
            assertSame(avatar2, awaitItem().single { it.serverId == 2 }.userAvatar)

            release1.complete(avatar1)
            assertSame(avatar1, awaitItem().single { it.serverId == 1 }.userAvatar)

            awaitComplete()
        }
    }
}
