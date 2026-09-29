package io.homeassistant.companion.android.imageloader

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Size
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoilHAImageLoaderTest {

    private val coilImageLoader: ImageLoader = mockk(relaxed = true)
    private val createdWith = mutableListOf<OkHttpClient>()

    private val loader = CoilHAImageLoader(
        context = mockk(relaxed = true),
        imageLoaderFactory = { okHttpClient ->
            createdWith += okHttpClient
            coilImageLoader
        },
    )

    private fun givenCoilReturnsNoImage() {
        val result = mockk<ImageResult>()
        every { result.image } returns null
        coEvery { coilImageLoader.execute(any()) } returns result
    }

    /** Loads [request] and returns the Coil request it was turned into. */
    private suspend fun coilRequestFor(request: HAImageRequest): ImageRequest {
        givenCoilReturnsNoImage()
        loader.init(OkHttpClient())
        loader.loadBitmap(request)
        val coilRequest = slot<ImageRequest>()
        coVerify { coilImageLoader.execute(capture(coilRequest)) }
        return coilRequest.captured
    }

    @Test
    fun `Given init not called when loadBitmap then it suspends until init is called`() = runTest {
        givenCoilReturnsNoImage()

        val bitmap = async { loader.loadBitmap(HAImageRequest(url = "http://ha.local/image.png")) }
        runCurrent()

        assertFalse(bitmap.isCompleted)
        coVerify(exactly = 0) { coilImageLoader.execute(any()) }

        loader.init(OkHttpClient())

        assertNull(bitmap.await())
        coVerify(exactly = 1) { coilImageLoader.execute(any()) }
    }

    @Test
    fun `Given init not called when getCachedBitmap then it suspends until init is called`() = runTest {
        every { coilImageLoader.memoryCache } returns null
        every { coilImageLoader.diskCache } returns null

        val bitmap = async { loader.getCachedBitmap("key") }
        runCurrent()

        assertFalse(bitmap.isCompleted)

        loader.init(OkHttpClient())

        assertNull(bitmap.await())
        assertTrue(bitmap.isCompleted)
    }

    @Test
    fun `Given init called twice when loading then the first OkHttpClient is used`() = runTest {
        givenCoilReturnsNoImage()
        val first = OkHttpClient()

        loader.init(first)
        loader.init(OkHttpClient())
        loader.loadBitmap(HAImageRequest(url = "http://ha.local/image.png"))

        assertEquals(listOf(first), createdWith)
    }

    @Test
    fun `Given a memory cache miss when getCachedBitmap then the disk cache is queried with the key`() = runTest {
        val memoryCache = mockk<MemoryCache>()
        every { memoryCache[any()] } returns null
        every { coilImageLoader.memoryCache } returns memoryCache
        val diskCache = mockk<DiskCache>()
        every { diskCache.openSnapshot(any()) } returns null
        every { coilImageLoader.diskCache } returns diskCache
        loader.init(OkHttpClient())

        assertNull(loader.getCachedBitmap("key"))

        verify { memoryCache[MemoryCache.Key("key")] }
        verify { diskCache.openSnapshot("key") }
    }

    @Test
    fun `Given a default request when loading then it uses software bitmaps, caches and original size`() = runTest {
        val request = coilRequestFor(HAImageRequest(url = "http://ha.local/image.png"))

        assertEquals("http://ha.local/image.png", request.data)
        assertFalse(request.allowHardware)
        assertEquals(CachePolicy.ENABLED, request.memoryCachePolicy)
        assertEquals(CachePolicy.ENABLED, request.diskCachePolicy)
        assertNull(request.memoryCacheKey)
        assertNull(request.diskCacheKey)
        assertEquals(Size.ORIGINAL, request.sizeResolver.size())
    }

    @Test
    fun `Given a cache key when loading then it keys both memory and disk caches`() = runTest {
        val request = coilRequestFor(
            HAImageRequest(url = "http://ha.local/image.png", cachePolicy = HAImageCachePolicy.Keyed(key = "key")),
        )

        assertEquals("key", request.memoryCacheKey)
        assertEquals("key", request.diskCacheKey)
    }

    @Test
    fun `Given a disabled cache when loading then memory and disk caches are disabled`() = runTest {
        val request = coilRequestFor(
            HAImageRequest(url = "http://ha.local/image.png", cachePolicy = HAImageCachePolicy.Disabled),
        )

        assertEquals(CachePolicy.DISABLED, request.memoryCachePolicy)
        assertEquals(CachePolicy.DISABLED, request.diskCachePolicy)
        assertEquals(CachePolicy.READ_ONLY, request.networkCachePolicy)
    }

    @Test
    fun `Given an exact size when loading then the request uses it with exact precision`() = runTest {
        val request = coilRequestFor(
            HAImageRequest(url = "http://ha.local/image.png", size = HAImageSize.Exact(1024)),
        )

        assertEquals(Size(1024, 1024), request.sizeResolver.size())
        assertEquals(Precision.EXACT, request.precision)
    }

    @Test
    fun `Given an inexact size when loading then the request uses it with inexact precision`() = runTest {
        val request = coilRequestFor(
            HAImageRequest(url = "http://ha.local/image.png", size = HAImageSize.Inexact(width = 200, height = 100)),
        )

        assertEquals(Size(200, 100), request.sizeResolver.size())
        assertEquals(Precision.INEXACT, request.precision)
    }

    @Test
    fun `Given headers when loading then they are sent with the request`() = runTest {
        val request = coilRequestFor(
            HAImageRequest(url = "http://ha.local/image.png", headers = mapOf("Authorization" to "Bearer token")),
        )

        assertEquals("Bearer token", request.httpHeaders["Authorization"])
    }
}
