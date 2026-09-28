package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.imageloader.HAImageCachePolicy
import io.homeassistant.companion.android.imageloader.HAImageLoader
import io.homeassistant.companion.android.imageloader.HAImageRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.net.URL
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerUserAvatarUseCaseTest {

    private val imageLoader: HAImageLoader = mockk()
    private val serverManager: ServerManager = mockk()

    private val useCase = ServerUserAvatarUseCase(
        imageLoader = imageLoader,
        serverManager = serverManager,
    )

    private fun server(id: Int, userId: String?): Server = Server(
        id = id,
        _name = "Home",
        connection = ServerConnectionInfo(externalUrl = ""),
        session = ServerSessionInfo(),
        user = ServerUserInfo(id = userId, name = "Alice"),
    )

    private fun personEntity(userId: String, picture: String?): Entity = Entity(
        entityId = "person.someone",
        state = "home",
        attributes = buildMap {
            put("user_id", userId)
            if (picture != null) put("entity_picture", picture)
        },
        lastChanged = LocalDateTime.now(),
        lastUpdated = LocalDateTime.now(),
    )

    /** Stubs [serverManager] so the user on [serverId] resolves to a person with [picture]. */
    private fun givenServerWithPersonPicture(serverId: Int, picture: String) {
        coEvery { serverManager.getServer(serverId) } returns server(id = serverId, userId = "uid-$serverId")
        coEvery { serverManager.integrationRepository(serverId) } returns mockk<IntegrationRepository> {
            coEvery { getEntities() } returns listOf(personEntity(userId = "uid-$serverId", picture = picture))
        }
        coEvery { serverManager.authenticationRepository(serverId) } returns mockk {
            coEvery { buildBearerToken() } returns "Bearer token"
        }
        coEvery { serverManager.connectionStateProvider(serverId) } returns mockk {
            coEvery { canSafelySendCredentials(any()) } returns true
        }
    }

    @Test
    fun `Given an unknown server when getUserAvatar then returns null`() = runTest {
        coEvery { serverManager.getServer(1) } returns null

        assertNull(useCase.getUserAvatar(1))
    }

    @Test
    fun `Given a user without an id when getUserAvatar then returns null and entities are not queried`() = runTest {
        coEvery { serverManager.getServer(1) } returns server(id = 1, userId = null)

        assertNull(useCase.getUserAvatar(1))
        coVerify(exactly = 0) { serverManager.integrationRepository(any()) }
    }

    @Test
    fun `Given no person entity matches the user when getUserAvatar then returns null`() = runTest {
        coEvery { serverManager.getServer(1) } returns server(id = 1, userId = "uid-1")
        coEvery { serverManager.integrationRepository(1) } returns mockk<IntegrationRepository> {
            coEvery { getEntities() } returns listOf(personEntity(userId = "someone-else", picture = "/picture.png"))
        }

        assertNull(useCase.getUserAvatar(1))
    }

    @Test
    fun `Given a resolvable picture when getUserAvatar then the avatar is downloaded`() = runTest {
        givenServerWithPersonPicture(serverId = 1, picture = "http://homeassistant.local:8123/api/image/serve/abc")
        coEvery { imageLoader.loadBitmap(any()) } returns null

        useCase.getUserAvatar(1)

        coVerify { imageLoader.loadBitmap(any()) }
    }

    @Test
    fun `Given a download when getUserAvatar then the request is keyed by the base-URL-agnostic cache key`() = runTest {
        val picture = "http://homeassistant.local:8123/api/image/serve/abc"
        givenServerWithPersonPicture(serverId = 1, picture = picture)
        val request = slot<HAImageRequest>()
        coEvery { imageLoader.loadBitmap(capture(request)) } returns null

        useCase.getUserAvatar(1)

        assertEquals(
            HAImageCachePolicy.Enabled(key = avatarCacheKey(serverId = 1, picturePath = picture)),
            request.captured.cachePolicy,
        )
    }

    @Test
    fun `Given a download when getUserAvatar then the request carries the bearer token`() = runTest {
        givenServerWithPersonPicture(serverId = 1, picture = "http://homeassistant.local:8123/api/image/serve/abc")
        val request = slot<HAImageRequest>()
        coEvery { imageLoader.loadBitmap(capture(request)) } returns null

        useCase.getUserAvatar(1)

        assertEquals(mapOf("Authorization" to "Bearer token"), request.captured.headers)
    }

    @Test
    fun `Given a relative picture path when getUserAvatar then it is resolved against the server base URL`() = runTest {
        givenServerWithPersonPicture(serverId = 1, picture = "/api/image/serve/abc")
        coEvery { serverManager.connectionStateProvider(1) } returns mockk {
            every { urlFlow() } returns flowOf(UrlState.HasUrl(URL("http://homeassistant.local:8123/")))
            coEvery { canSafelySendCredentials(any()) } returns true
        }
        val request = slot<HAImageRequest>()
        coEvery { imageLoader.loadBitmap(capture(request)) } returns null

        useCase.getUserAvatar(1)

        assertEquals("http://homeassistant.local:8123/api/image/serve/abc", request.captured.url)
    }

    @Test
    fun `Given credentials cannot be safely sent when getUserAvatar then it does not download and falls back to the cache`() = runTest {
        val picture = "http://homeassistant.local:8123/api/image/serve/abc"
        givenServerWithPersonPicture(serverId = 1, picture = picture)
        coEvery { serverManager.connectionStateProvider(1) } returns mockk {
            coEvery { canSafelySendCredentials(any()) } returns false
        }
        coEvery { imageLoader.getCachedBitmap(any()) } returns null

        assertNull(useCase.getUserAvatar(1))
        coVerify(exactly = 0) { imageLoader.loadBitmap(any()) }
        coVerify { imageLoader.getCachedBitmap(avatarCacheKey(serverId = 1, picturePath = picture)) }
    }

    @Test
    fun `Given the download yields no image when getUserAvatar then returns null`() = runTest {
        givenServerWithPersonPicture(serverId = 1, picture = "http://homeassistant.local:8123/api/image/serve/abc")
        coEvery { imageLoader.loadBitmap(any()) } returns null

        assertNull(useCase.getUserAvatar(1))
    }

    @Test
    fun `Given the server is unreachable when getUserAvatar then the cache is queried by the cache key`() = runTest {
        val picture = "/api/image/serve/abc"
        givenServerWithPersonPicture(serverId = 1, picture = picture)
        // No base URL available, so the avatar can only come from the cache.
        coEvery { serverManager.connectionStateProvider(1) } returns mockk {
            every { urlFlow() } returns flowOf(UrlState.HasUrl(null))
        }
        coEvery { imageLoader.getCachedBitmap(any()) } returns null

        assertNull(useCase.getUserAvatar(1))
        coVerify { imageLoader.getCachedBitmap(avatarCacheKey(serverId = 1, picturePath = picture)) }
        coVerify(exactly = 0) { imageLoader.loadBitmap(any()) }
    }

    @Test
    fun `Given a serverId and picture path then the cache key is base-URL agnostic`() {
        // The same server-relative picture path must map to the same key regardless of which base
        // URL (internal or external) ends up resolving it at download time.
        assertEquals(
            "server-1-/api/image/serve/abc/512x512",
            avatarCacheKey(serverId = 1, picturePath = "/api/image/serve/abc/512x512"),
        )
    }
}
