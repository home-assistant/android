package io.homeassistant.companion.android.util

import coil3.Image
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.allowHardware
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.net.URL
import java.time.LocalDateTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

class ServerUserAvatarUseCaseTest {

    // The context is only stored on the ImageRequest (its methods are never called at build time),
    // so a relaxed mock is enough and we avoid needing Robolectric.
    private val imageLoader: ImageLoader = mockk()
    private val serverManager: ServerManager = mockk()

    private val useCase = ServerUserAvatarUseCase(
        context = mockk(relaxed = true),
        imageLoader = imageLoader,
        serverManager = serverManager,
    )

    private fun server(id: Int, userId: String?, userPicturePath: String?): Server = Server(
        id = id,
        _name = "Home",
        connection = ServerConnectionInfo(externalUrl = ""),
        session = ServerSessionInfo(),
        user = ServerUserInfo(id = userId, name = "Alice", picturePath = userPicturePath),
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

    private fun imageResult(image: Image? = null): ImageResult {
        val result = mockk<ImageResult>()
        every { result.image } returns image
        return result
    }

    /** Stubs [serverManager] so the user on [serverId] resolves to a person with [picture]. */
    private fun givenServerWithPersonPicture(serverId: Int, picture: String, previousPicture: String? = null) {
        coEvery { serverManager.getServer(serverId) } returns server(id = serverId, userId = "uid-$serverId", userPicturePath = previousPicture)
        coEvery { serverManager.updateServer(any()) } just Runs
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
        coEvery { serverManager.getServer(1) } returns server(id = 1, userId = null, userPicturePath = null)

        assertNull(useCase.getUserAvatar(1))
        coVerify(exactly = 0) { serverManager.integrationRepository(any()) }
    }

    @Test
    fun `Given no person entity matches the user when getUserAvatar then returns null`() = runTest {
        coEvery { serverManager.getServer(1) } returns server(id = 1, userId = "uid-1", userPicturePath = null)
        coEvery { serverManager.integrationRepository(1) } returns mockk<IntegrationRepository> {
            coEvery { getEntities() } returns listOf(personEntity(userId = "someone-else", picture = "/picture.png"))
        }

        assertNull(useCase.getUserAvatar(1))
    }

    @Test
    fun `Given a resolvable picture when getUserAvatar then path is stored and the avatar is downloaded`() = runTest {
        val picturePath = "http://homeassistant.local:8123/api/image/serve/abc"
        givenServerWithPersonPicture(serverId = 1, picture = picturePath)

        val serverSlot = slot<Server>()
        coEvery { serverManager.updateServer(capture(serverSlot)) } just Runs
        coEvery { imageLoader.execute(any()) } returns imageResult()

        useCase.getUserAvatar(1)

        assertEquals(picturePath, serverSlot.captured.user.picturePath)
        coVerify { imageLoader.execute(any()) }
    }

    @Test
    fun `Given no resolvable picture but previously stored when getUserAvatar then the avatar is downloaded from the stored path`() = runTest {
        val storedPicture = "http://homeassistant.local:8123/api/image/serve/previous"
        givenServerWithPersonPicture(serverId = 1, picture = "", previousPicture = storedPicture)

        // Entities don't load, so nothing to load picture from
        coEvery { serverManager.integrationRepository(1) } returns mockk {
            coEvery { getEntities() } returns emptyList()
        }
        val imageRequestSlot = slot<ImageRequest>()
        coEvery { imageLoader.execute(capture(imageRequestSlot)) } returns imageResult()

        useCase.getUserAvatar(1)

        coVerify(exactly = 0) { serverManager.updateServer(any()) }
        assertNotNull(imageRequestSlot.captured)
        assertEquals(storedPicture, imageRequestSlot.captured.data.toString())
    }

    @Test
    fun `Given a download when getUserAvatar then the request is keyed by the base-URL-agnostic cache key`() = runTest {
        val picture = "http://homeassistant.local:8123/api/image/serve/abc"
        givenServerWithPersonPicture(serverId = 1, picture = picture)
        val request = slot<ImageRequest>()
        coEvery { imageLoader.execute(capture(request)) } returns imageResult()

        useCase.getUserAvatar(1)

        val cacheKey = avatarCacheKey(serverId = 1, picturePath = picture)
        assertEquals(cacheKey, request.captured.diskCacheKey)
        assertEquals(cacheKey, request.captured.memoryCacheKey)
    }

    @Test
    fun `Given a download when getUserAvatar then the request disables hardware bitmaps and carries the bearer token`() = runTest {
        givenServerWithPersonPicture(serverId = 1, picture = "http://homeassistant.local:8123/api/image/serve/abc")
        val request = slot<ImageRequest>()
        coEvery { imageLoader.execute(capture(request)) } returns imageResult()

        useCase.getUserAvatar(1)

        assertFalse(request.captured.allowHardware)
        assertEquals("Bearer token", request.captured.httpHeaders["Authorization"])
    }

    @Test
    fun `Given a relative picture path when getUserAvatar then it is resolved against the server base URL`() = runTest {
        givenServerWithPersonPicture(serverId = 1, picture = "/api/image/serve/abc")
        coEvery { serverManager.connectionStateProvider(1) } returns mockk {
            every { urlFlow() } returns flowOf(UrlState.HasUrl(URL("http://homeassistant.local:8123/")))
            coEvery { canSafelySendCredentials(any()) } returns true
        }
        val request = slot<ImageRequest>()
        coEvery { imageLoader.execute(capture(request)) } returns imageResult()

        useCase.getUserAvatar(1)

        assertEquals("http://homeassistant.local:8123/api/image/serve/abc", request.captured.data)
    }

    @Test
    fun `Given credentials cannot be safely sent when getUserAvatar then it does not download and falls back to the cache`() = runTest {
        val picture = "http://homeassistant.local:8123/api/image/serve/abc"
        givenServerWithPersonPicture(serverId = 1, picture = picture, previousPicture = picture)
        coEvery { serverManager.integrationRepository(1) } returns mockk {
            coEvery { getEntities() } returns emptyList()
        }
        coEvery { serverManager.connectionStateProvider(1) } returns mockk {
            coEvery { canSafelySendCredentials(any()) } returns false
        }
        val memoryCache = mockk<MemoryCache>()
        every { memoryCache.get(any()) } returns null
        every { imageLoader.memoryCache } returns memoryCache
        every { imageLoader.diskCache } returns null

        assertNull(useCase.getUserAvatar(1))
        coVerify(exactly = 0) { serverManager.updateServer(any()) }
        coVerify(exactly = 0) { imageLoader.execute(any()) }
        verify { memoryCache.get(MemoryCache.Key(avatarCacheKey(serverId = 1, picturePath = picture))) }
    }

    @Test
    fun `Given the download yields no image when getUserAvatar then returns null`() = runTest {
        givenServerWithPersonPicture(serverId = 1, picture = "http://homeassistant.local:8123/api/image/serve/abc")
        coEvery { imageLoader.execute(any()) } returns imageResult(image = null)

        assertNull(useCase.getUserAvatar(1))
    }

    @Test
    fun `Given the server is unreachable when getUserAvatar then the cache is queried by the cache key`() = runTest {
        val picture = "/api/image/serve/abc"
        givenServerWithPersonPicture(serverId = 1, picture = picture, previousPicture = picture)
        // No entities or base URL available, so the avatar can only come from the cache.
        coEvery { serverManager.integrationRepository(1) } returns mockk {
            coEvery { getEntities() } returns emptyList()
        }
        coEvery { serverManager.connectionStateProvider(1) } returns mockk {
            every { urlFlow() } returns flowOf(UrlState.HasUrl(null))
        }
        val memoryCache = mockk<MemoryCache>()
        every { memoryCache.get(any()) } returns null
        every { imageLoader.memoryCache } returns memoryCache
        every { imageLoader.diskCache } returns null

        assertNull(useCase.getUserAvatar(1))
        coVerify(exactly = 0) { serverManager.updateServer(any()) }
        verify { memoryCache.get(MemoryCache.Key(avatarCacheKey(serverId = 1, picturePath = picture))) }
        coVerify(exactly = 0) { imageLoader.execute(any()) }
    }

    @Test
    fun `Given the server is unreachable and the memory cache misses when getUserAvatar then the disk cache is queried`() = runTest {
        val picture = "/api/image/serve/abc"
        givenServerWithPersonPicture(serverId = 1, picture = picture, previousPicture = picture)
        // No entities or base URL available, so the avatar can only come from the cache.
        coEvery { serverManager.integrationRepository(1) } returns mockk {
            coEvery { getEntities() } returns emptyList()
        }
        coEvery { serverManager.connectionStateProvider(1) } returns mockk {
            every { urlFlow() } returns flowOf(UrlState.HasUrl(null))
        }
        val memoryCache = mockk<MemoryCache>()
        every { memoryCache.get(any()) } returns null
        every { imageLoader.memoryCache } returns memoryCache
        val diskCache = mockk<DiskCache>()
        every { diskCache.openSnapshot(any()) } returns null
        every { imageLoader.diskCache } returns diskCache

        assertNull(useCase.getUserAvatar(1))
        coVerify(exactly = 0) { serverManager.updateServer(any()) }
        verify { diskCache.openSnapshot(avatarCacheKey(serverId = 1, picturePath = picture)) }
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
