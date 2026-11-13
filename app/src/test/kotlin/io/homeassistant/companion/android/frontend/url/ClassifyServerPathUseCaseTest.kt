package io.homeassistant.companion.android.frontend.url

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.PanelResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

private const val SERVER_ID = 1
private const val BASE_URL = "https://home.example.com"

class ClassifyServerPathUseCaseTest {

    private val serverManager: ServerManager = mockk()
    private val webSocketRepository: WebSocketRepository = mockk()
    private val classifyServerPath = ClassifyServerPathUseCase(serverManager)

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["", "/", "/?kiosk"])
    fun `Given root path when classify then returns Frontend without getting the panels`(path: String) = runTest {
        assertEquals(ServerPathType.Frontend, classify(path))

        coVerify(exactly = 0) { serverManager.webSocketRepository(any()) }
    }

    @ParameterizedTest(name = "{0} admin={1}")
    @CsvSource(
        "/lovelace, true",
        "/lovelace/cameras?kiosk, false",
        "/d5369777_music_assistant, false",
        "/config/api, true",
        "/lovelace/local, false",
    )
    fun `Given path of a panel when classify then returns Frontend`(path: String, isAdmin: Boolean) = runTest {
        givenServer(isAdmin = isAdmin, panels = listOf("lovelace", "config", "d5369777_music_assistant"))

        assertEquals(ServerPathType.Frontend, classify(path))
    }

    @ParameterizedTest(name = "{0} admin={1}")
    @CsvSource(
        "/local/video.mp4, true",
        "/local/video.mp4, false",
        "/api/frigate/notifications/1.2-abc/clip.mp4, false",
        "/media/local/video.mp4?authSig=abc, false",
        "/hacsfiles/card/card.js, false",
        "/static/icons/favicon.ico, false",
    )
    fun `Given known non frontend path when classify then returns NotFrontend`(path: String, isAdmin: Boolean) = runTest {
        givenServer(isAdmin = isAdmin, panels = listOf("lovelace"))

        assertEquals(ServerPathType.NotFrontend, classify(path))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["/immich/1/2", "/localhost", "/typo"])
    fun `Given path outside of the panels and admin user when classify then returns NotFrontend`(path: String) = runTest {
        givenServer(isAdmin = true, panels = listOf("lovelace"))

        assertEquals(ServerPathType.NotFrontend, classify(path))
    }

    @ParameterizedTest(name = "admin={0}")
    @CsvSource(value = ["false", "null"], nullValues = ["null"])
    fun `Given path outside of the panels and user not known as admin when classify then returns Unknown`(
        isAdmin: Boolean?,
    ) = runTest {
        givenServer(isAdmin = isAdmin, panels = listOf("lovelace"))

        assertEquals(ServerPathType.Unknown, classify("/config/automation"))
    }

    @Test
    fun `Given panels not returned when classify then only known non frontend path is NotFrontend`() = runTest {
        givenServer(isAdmin = true, panels = null)

        assertEquals(ServerPathType.NotFrontend, classify("/local/video.mp4"))
        assertEquals(ServerPathType.Unknown, classify("/lovelace"))
    }

    @Test
    fun `Given panels failing to decode when classify then classifies without them`() = runTest {
        givenServer(isAdmin = true, panels = null)
        coEvery { webSocketRepository.getPanels() } throws SerializationException("Unexpected JSON")

        assertEquals(ServerPathType.Unknown, classify("/lovelace"))
    }

    @Test
    fun `Given server without repository when classify then classifies without the panels`() = runTest {
        givenServer(isAdmin = true, panels = null)
        coEvery { serverManager.webSocketRepository(SERVER_ID) } throws IllegalStateException("No server")

        assertEquals(ServerPathType.Unknown, classify("/lovelace"))
    }

    private suspend fun classify(path: String): ServerPathType = classifyServerPath(serverId = SERVER_ID, url = "$BASE_URL$path".toHttpUrl())

    private fun givenServer(isAdmin: Boolean?, panels: List<String>?) {
        coEvery { serverManager.getServer(SERVER_ID) } returns Server(
            id = SERVER_ID,
            _name = "Test Server",
            connection = ServerConnectionInfo(externalUrl = BASE_URL),
            session = ServerSessionInfo(),
            user = ServerUserInfo(isAdmin = isAdmin),
        )
        coEvery { serverManager.webSocketRepository(SERVER_ID) } returns webSocketRepository
        coEvery { webSocketRepository.getPanels() } returns panels?.map { PanelResponse(urlPath = it) }
    }
}
