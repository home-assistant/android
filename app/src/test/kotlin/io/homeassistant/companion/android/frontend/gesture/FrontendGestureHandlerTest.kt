package io.homeassistant.companion.android.frontend.gesture

import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.GestureDirection
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.frontend.EvaluateScriptUsage
import io.homeassistant.companion.android.frontend.WebViewAction
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ShowSidebarMessage
import io.homeassistant.companion.android.frontend.navigation.FrontendEvent
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.util.mockServer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(EvaluateScriptUsage::class)
@ExtendWith(ConsoleLogExtension::class)
class FrontendGestureHandlerTest {

    private val prefsRepository: PrefsRepository = mockk()
    private val externalBusRepository: FrontendExternalBusRepository = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk()
    private lateinit var handler: FrontendGestureHandler

    @BeforeEach
    fun setup() {
        handler = FrontendGestureHandler(
            prefsRepository = prefsRepository,
            externalBusRepository = externalBusRepository,
            serverManager = serverManager,
        )
    }

    @Test
    fun `Given NONE action when handleGesture then returns Ignored`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_LEFT_TWO) } returns GestureAction.NONE

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.LEFT,
            pointerCount = 2,
        )

        assertEquals(GestureResult.Ignored, result)
        coVerify(exactly = 0) { externalBusRepository.send(any()) }
    }

    @Test
    fun `Given SHOW_SIDEBAR action when handleGesture then sends ShowSidebarMessage`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_RIGHT_TWO) } returns GestureAction.SHOW_SIDEBAR

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.RIGHT,
            pointerCount = 2,
        )

        assertEquals(GestureResult.Forwarded, result)
        coVerify { externalBusRepository.send(ShowSidebarMessage) }
    }

    @Test
    fun `Given NAVIGATE_DASHBOARD and server 2025_6 when handleGesture then returns PerformWebViewActionThen with ClearHistory`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_UP_TWO) } returns GestureAction.NAVIGATE_DASHBOARD
        val server = mockServer(
            url = "https://ha.test",
            name = "Test",
            haVersion = HomeAssistantVersion(2025, 6, 0),
            serverId = 1,
        )
        coEvery { serverManager.getServer(1) } returns server

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.UP,
            pointerCount = 2,
        )

        assertInstanceOf(GestureResult.PerformWebViewActionThen::class.java, result)
        val chainedResult = result as GestureResult.PerformWebViewActionThen<*>
        assertInstanceOf(WebViewAction.ClearHistory::class.java, chainedResult.action)

        // Execute the continuation and verify NavigateToMessage is sent
        val thenResult = chainedResult.then()
        assertEquals(GestureResult.Forwarded, thenResult)
        coVerify { externalBusRepository.send(match { it is OutgoingExternalBusMessage }) }
    }

    @Test
    fun `Given NAVIGATE_DASHBOARD and old server when handleGesture then returns Ignored`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_UP_TWO) } returns GestureAction.NAVIGATE_DASHBOARD
        val server = mockServer(
            url = "https://ha.test",
            name = "Test",
            haVersion = HomeAssistantVersion(2025, 5, 0),
            serverId = 1,
        )
        coEvery { serverManager.getServer(1) } returns server

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.UP,
            pointerCount = 2,
        )

        assertEquals(GestureResult.Ignored, result)
        coVerify(exactly = 0) { externalBusRepository.send(any()) }
    }

    @Test
    fun `Given QUICKBAR_DEFAULT and server 2026_2 when handleGesture then dispatches Ctrl+K`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_DOWN_TWO) } returns GestureAction.QUICKBAR_DEFAULT
        val server = mockServer(
            url = "https://ha.test",
            name = "Test",
            haVersion = HomeAssistantVersion(2026, 2, 0),
            serverId = 1,
        )
        coEvery { serverManager.getServer(1) } returns server

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.DOWN,
            pointerCount = 2,
        )

        assertEquals(GestureResult.Forwarded, result)
        coVerify { externalBusRepository.evaluateScript(match { it.contains("ctrlKey: true") && it.contains("key: 'k'") }) }
    }

    @Test
    fun `Given QUICKBAR_DEFAULT and old server when handleGesture then dispatches E key`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_DOWN_TWO) } returns GestureAction.QUICKBAR_DEFAULT
        val server = mockServer(
            url = "https://ha.test",
            name = "Test",
            haVersion = HomeAssistantVersion(2026, 1, 0),
            serverId = 1,
        )
        coEvery { serverManager.getServer(1) } returns server

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.DOWN,
            pointerCount = 2,
        )

        assertEquals(GestureResult.Forwarded, result)
        coVerify { externalBusRepository.evaluateScript(match { it.contains("key: 'e'") && !it.contains("ctrlKey: true") }) }
    }

    @Test
    fun `Given QUICKBAR_ENTITIES when handleGesture then dispatches E key`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_LEFT_TWO) } returns GestureAction.QUICKBAR_ENTITIES

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.LEFT,
            pointerCount = 2,
        )

        assertEquals(GestureResult.Forwarded, result)
        coVerify { externalBusRepository.evaluateScript(match { it.contains("key: 'e'") }) }
    }

    @Test
    fun `Given QUICKBAR_COMMANDS when handleGesture then dispatches C key`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_LEFT_TWO) } returns GestureAction.QUICKBAR_COMMANDS

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.LEFT,
            pointerCount = 2,
        )

        assertEquals(GestureResult.Forwarded, result)
        coVerify { externalBusRepository.evaluateScript(match { it.contains("key: 'c'") }) }
    }

    @Test
    fun `Given OPEN_ASSIST when handleGesture then returns Navigate to Assist`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_UP_THREE) } returns GestureAction.OPEN_ASSIST

        val result = handler.handleGesture(
            serverId = 42,
            direction = GestureDirection.UP,
            pointerCount = 3,
        )

        assertInstanceOf(GestureResult.Navigate::class.java, result)
        val event = (result as GestureResult.Navigate).event
        assertInstanceOf(FrontendEvent.NavigateToAssist::class.java, event)
        val assist = event as FrontendEvent.NavigateToAssist
        assertEquals(42, assist.serverId)
        assertEquals(true, assist.startListening)
    }

    @Test
    fun `Given OPEN_APP_SETTINGS when handleGesture then returns Navigate to Settings`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_DOWN_THREE) } returns GestureAction.OPEN_APP_SETTINGS

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.DOWN,
            pointerCount = 3,
        )

        assertInstanceOf(GestureResult.Navigate::class.java, result)
        val event = (result as GestureResult.Navigate).event
        assertEquals(FrontendEvent.NavigateToSettings, event)
    }

    @Test
    fun `Given OPEN_APP_DEVELOPER when handleGesture then returns Navigate to Developer Settings`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_RIGHT_THREE) } returns GestureAction.OPEN_APP_DEVELOPER

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.RIGHT,
            pointerCount = 3,
        )

        assertInstanceOf(GestureResult.Navigate::class.java, result)
        val event = (result as GestureResult.Navigate).event
        assertEquals(FrontendEvent.NavigateToDeveloperSettings, event)
    }

    @Test
    fun `Given NAVIGATE_FORWARD when handleGesture then returns WebViewAction Forward`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_RIGHT_TWO) } returns GestureAction.NAVIGATE_FORWARD

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.RIGHT,
            pointerCount = 2,
        )

        assertInstanceOf(GestureResult.PerformWebViewAction::class.java, result)
        assertInstanceOf(WebViewAction.Forward::class.java, (result as GestureResult.PerformWebViewAction).action)
    }

    @Test
    fun `Given NAVIGATE_RELOAD when handleGesture then returns WebViewAction Reload`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_DOWN_TWO) } returns GestureAction.NAVIGATE_RELOAD

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.DOWN,
            pointerCount = 2,
        )

        assertInstanceOf(GestureResult.PerformWebViewAction::class.java, result)
        assertInstanceOf(WebViewAction.Reload::class.java, (result as GestureResult.PerformWebViewAction).action)
    }

    @Test
    fun `Given SERVER_LIST when handleGesture then returns Navigate ShowServerSwitcher`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_LEFT_TWO) } returns GestureAction.SERVER_LIST
        val serverA = mockServer(url = "https://a.test", name = "A", serverId = 1)
        coEvery { serverManager.servers() } returns listOf(serverA)

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.LEFT,
            pointerCount = 2,
        )

        assertEquals(GestureResult.Navigate(FrontendEvent.ShowServerSwitcher), result)
    }

    @Test
    fun `Given SERVER_NEXT when handleGesture then returns SwitchServer with next server in list order`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_LEFT_TWO) } returns GestureAction.SERVER_NEXT
        val serverA = mockServer(url = "https://a.test", name = "A", serverId = 1)
        val serverB = mockServer(url = "https://b.test", name = "B", serverId = 2)
        val serverC = mockServer(url = "https://c.test", name = "C", serverId = 3)
        coEvery { serverManager.servers() } returns listOf(serverA, serverB, serverC)

        val result = handler.handleGesture(
            serverId = 2,
            direction = GestureDirection.LEFT,
            pointerCount = 2,
        )

        assertEquals(GestureResult.SwitchServer(3), result)
    }

    @Test
    fun `Given SERVER_NEXT on last server when handleGesture then wraps around to first server`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_LEFT_TWO) } returns GestureAction.SERVER_NEXT
        val serverA = mockServer(url = "https://a.test", name = "A", serverId = 1)
        val serverB = mockServer(url = "https://b.test", name = "B", serverId = 2)
        coEvery { serverManager.servers() } returns listOf(serverA, serverB)

        val result = handler.handleGesture(
            serverId = 2,
            direction = GestureDirection.LEFT,
            pointerCount = 2,
        )

        assertEquals(GestureResult.SwitchServer(1), result)
    }

    @Test
    fun `Given SERVER_PREVIOUS when handleGesture then returns SwitchServer with previous server in list order`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_LEFT_TWO) } returns GestureAction.SERVER_PREVIOUS
        val serverA = mockServer(url = "https://a.test", name = "A", serverId = 1)
        val serverB = mockServer(url = "https://b.test", name = "B", serverId = 2)
        val serverC = mockServer(url = "https://c.test", name = "C", serverId = 3)
        coEvery { serverManager.servers() } returns listOf(serverA, serverB, serverC)

        val result = handler.handleGesture(
            serverId = 2,
            direction = GestureDirection.LEFT,
            pointerCount = 2,
        )

        assertEquals(GestureResult.SwitchServer(1), result)
    }

    @Test
    fun `Given SERVER_PREVIOUS on first server when handleGesture then wraps around to last server`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_LEFT_TWO) } returns GestureAction.SERVER_PREVIOUS
        val serverA = mockServer(url = "https://a.test", name = "A", serverId = 1)
        val serverB = mockServer(url = "https://b.test", name = "B", serverId = 2)
        val serverC = mockServer(url = "https://c.test", name = "C", serverId = 3)
        coEvery { serverManager.servers() } returns listOf(serverA, serverB, serverC)

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.LEFT,
            pointerCount = 2,
        )

        assertEquals(GestureResult.SwitchServer(3), result)
    }

    @Test
    fun `Given SERVER_NEXT with only one server when handleGesture then returns Ignored`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_LEFT_TWO) } returns GestureAction.SERVER_NEXT
        val onlyServer = mockServer(url = "https://a.test", name = "A", serverId = 1)
        coEvery { serverManager.servers() } returns listOf(onlyServer)

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.LEFT,
            pointerCount = 2,
        )

        assertEquals(GestureResult.Ignored, result)
    }

    @Test
    fun `Given SERVER_PREVIOUS with only one server when handleGesture then returns Ignored`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_LEFT_TWO) } returns GestureAction.SERVER_PREVIOUS
        val onlyServer = mockServer(url = "https://a.test", name = "A", serverId = 1)
        coEvery { serverManager.servers() } returns listOf(onlyServer)

        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.LEFT,
            pointerCount = 2,
        )

        assertEquals(GestureResult.Ignored, result)
    }

    @Test
    fun `Given SERVER_NEXT with current server not in list when handleGesture then returns Ignored`() = runTest {
        coEvery { prefsRepository.getGestureAction(HAGesture.SWIPE_LEFT_TWO) } returns GestureAction.SERVER_NEXT
        val serverA = mockServer(url = "https://a.test", name = "A", serverId = 1)
        val serverB = mockServer(url = "https://b.test", name = "B", serverId = 2)
        coEvery { serverManager.servers() } returns listOf(serverA, serverB)

        val result = handler.handleGesture(
            serverId = 99,
            direction = GestureDirection.LEFT,
            pointerCount = 2,
        )

        assertEquals(GestureResult.Ignored, result)
    }

    @Test
    fun `Given unsupported pointer count when handleGesture then returns Ignored`() = runTest {
        val result = handler.handleGesture(
            serverId = 1,
            direction = GestureDirection.LEFT,
            pointerCount = 1,
        )

        assertEquals(GestureResult.Ignored, result)
        coVerify(exactly = 0) { externalBusRepository.send(any()) }
    }
}
