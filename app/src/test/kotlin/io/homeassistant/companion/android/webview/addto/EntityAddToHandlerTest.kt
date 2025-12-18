package io.homeassistant.companion.android.webview.addto

import android.content.Context
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.IMAGE_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.TODO_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.widgets.camera.CameraWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.entity.EntityWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.mediaplayer.MediaPlayerControlsWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.todo.TodoWidgetConfigureActivity
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@ExperimentalCoroutinesApi
@ExtendWith(ConsoleLogExtension::class)
class EntityAddToHandlerTest {

    private lateinit var serverManager: ServerManager
    private lateinit var prefsRepository: PrefsRepository
    private lateinit var integrationRepository: IntegrationRepository
    private lateinit var context: Context
    private lateinit var handler: EntityAddToHandler

    private val serverId = 42

    @BeforeEach
    fun setUp() {
        serverManager = mockk()
        prefsRepository = mockk()
        integrationRepository = mockk()
        context = mockk(relaxed = true)
        val server = mockk<Server>()

        every { server.id } returns serverId
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { serverManager.getServer() } returns server
        handler = EntityAddToHandler(serverManager, prefsRepository)

        // Always override the handler so individual test can decide to override it or not without impacting the other tests
        FailFast.setHandler { exception, _ ->
            fail("Unhandled exception caught", exception)
        }
    }

    @Test
    fun `Given AndroidAutoFavorite action when executing then it adds to favorite`() = runTest {
        val entityId = "vehicle.test"
        val action = EntityAddToAction.AndroidAutoFavorite
        val onShowSnackbar: suspend (String, String?) -> Boolean = mockk(relaxed = true)
        coJustRun { prefsRepository.addAutoFavorite(any()) }
        every { context.getString(commonR.string.add_to_android_auto_success) } returns "hello"

        handler.execute(context, action, entityId, onShowSnackbar)

        coVerify {
            prefsRepository.addAutoFavorite(AutoFavorite(serverId, entityId))
        }
        coVerify { onShowSnackbar("hello", null) }
    }

    @Test
    fun `Given null server when adding AndroidAutoFavorite then call FailFast and show snackbar in release`() = runTest {
        val entityId = "vehicle.test"
        val action = EntityAddToAction.AndroidAutoFavorite
        val onShowSnackbar: suspend (String, String?) -> Boolean = mockk(relaxed = true)
        var throwableCaptured: Throwable? = null

        every { context.getString(commonR.string.add_to_android_auto_success) } returns "hello"

        FailFast.setHandler { throwable, _ ->
            throwableCaptured = throwable
        }

        coEvery { serverManager.getServer() } returns null

        handler.execute(context, action, entityId, onShowSnackbar)

        assertNotNull(throwableCaptured)
        coVerify(exactly = 0) { prefsRepository.addAutoFavorite(any()) }
        // In debug this won't be display since FailFast would throw
        coVerify { onShowSnackbar("hello", null) }
    }

    @Test
    fun `Given null server when getting actionsForEntity then return empty list`() = runTest {
        val entityId = "light.test"
        coEvery { serverManager.getServer() } returns null

        val actions = handler.actionsForEntity(isFullFlavor = true, isAutomotive = false, isQuest = false, entityId = entityId)

        assertEquals(emptyList<EntityAddToAction>(), actions)
    }

    @Test
    fun `Given entity not found when getting actionsForEntity then return empty list`() = runTest {
        val entityId = "light.nonexistent"
        coEvery { integrationRepository.getEntity(entityId) } returns null

        val actions = handler.actionsForEntity(isFullFlavor = true, isAutomotive = false, isQuest = false, entityId)

        assertEquals(emptyList<EntityAddToAction>(), actions)
    }

    @Test
    fun `Given standard entityId when getting actionsForEntity then returns EntityWidget`() = runTest {
        val entityId = "standard.test"

        mockGetEntity(entityId)

        val actions = handler.actionsForEntity(isFullFlavor = true, isAutomotive = false, isQuest = false, entityId)
        assertEquals(1, actions.size)
        assertEquals(EntityAddToAction.EntityWidget, actions.first())
    }

    @Test
    fun `Given alarm_control_panel entityId on full flavor when getting actionsForEntity then returns EntityWidget and AndroidAutoFavorite`() = runTest {
        val entityId = "alarm_control_panel.test"

        mockGetEntity(entityId)

        val actions = handler.actionsForEntity(isFullFlavor = true, isAutomotive = false, isQuest = false, entityId)

        assertEquals(listOf(EntityAddToAction.EntityWidget, EntityAddToAction.AndroidAutoFavorite), actions)
    }

    @Test
    fun `Given alarm_control_panel entityId on minimal flavor when getting actionsForEntity then returns EntityWidget`() = runTest {
        val entityId = "alarm_control_panel.test"

        mockGetEntity(entityId)

        val actions = handler.actionsForEntity(isFullFlavor = false, isAutomotive = false, isQuest = false, entityId)

        assertEquals(listOf(EntityAddToAction.EntityWidget), actions)
    }

    @Test
    fun `Given media player entityId when getting actionsForEntity then returns EntityWidget and MediaPlayerWidget`() = runTest {
        val entityId = "$MEDIA_PLAYER_DOMAIN.test"

        mockGetEntity(entityId)

        val actions = handler.actionsForEntity(isFullFlavor = true, isAutomotive = false, isQuest = false, entityId)

        assertEquals(listOf(EntityAddToAction.EntityWidget, EntityAddToAction.MediaPlayerWidget), actions)
    }

    @Test
    fun `Given todo entityId when getting actionsForEntity then returns EntityWidget and TodoWidget`() = runTest {
        val entityId = "$TODO_DOMAIN.test"

        mockGetEntity(entityId)

        val actions = handler.actionsForEntity(isFullFlavor = true, isAutomotive = false, isQuest = false, entityId)

        assertEquals(listOf(EntityAddToAction.EntityWidget, EntityAddToAction.TodoWidget), actions)
    }

    @Test
    fun `Given camera entityId when getting actionsForEntity then returns EntityWidget and CameraWidget`() = runTest {
        val entityId = "$CAMERA_DOMAIN.test"

        mockGetEntity(entityId)

        val actions = handler.actionsForEntity(isFullFlavor = true, isAutomotive = false, isQuest = false, entityId)

        assertEquals(listOf(EntityAddToAction.EntityWidget, EntityAddToAction.CameraWidget), actions)
    }

    @Test
    fun `Given image entityId when getting actionsForEntity then returns EntityWidget and CameraWidget`() = runTest {
        val entityId = "$IMAGE_DOMAIN.test"

        mockGetEntity(entityId)

        val actions = handler.actionsForEntity(isFullFlavor = true, isAutomotive = false, isQuest = false, entityId)
        assertEquals(listOf(EntityAddToAction.EntityWidget, EntityAddToAction.CameraWidget), actions)
    }

    @Test
    fun `Given EntityWidget action when executing then it starts EntityWidgetConfigureActivity`() = runTest {
        val entityId = "light.test"
        val action = EntityAddToAction.EntityWidget
        val onShowSnackbar: suspend (String, String?) -> Boolean = mockk(relaxed = true)

        mockkObject(EntityWidgetConfigureActivity.Companion)
        every { EntityWidgetConfigureActivity.newInstance(context, entityId) } returns mockk()

        handler.execute(context, action, entityId, onShowSnackbar)

        verify { context.startActivity(any()) }
        verify { EntityWidgetConfigureActivity.newInstance(context, entityId) }
        coVerify(exactly = 0) { onShowSnackbar(any(), any()) }
    }

    @Test
    fun `Given MediaPlayerWidget action when executing then it starts MediaPlayerControlsWidgetConfigureActivity`() = runTest {
        val entityId = "$MEDIA_PLAYER_DOMAIN.test"
        val action = EntityAddToAction.MediaPlayerWidget
        val onShowSnackbar: suspend (String, String?) -> Boolean = mockk(relaxed = true)

        mockkObject(MediaPlayerControlsWidgetConfigureActivity.Companion)
        every { MediaPlayerControlsWidgetConfigureActivity.newInstance(context, entityId) } returns mockk()

        handler.execute(context, action, entityId, onShowSnackbar)
        verify { context.startActivity(any()) }
        verify { MediaPlayerControlsWidgetConfigureActivity.newInstance(context, entityId) }
        coVerify(exactly = 0) { onShowSnackbar(any(), any()) }
    }

    @Test
    fun `Given CameraWidget action when executing then it starts CameraWidgetConfigureActivity`() = runTest {
        val entityId = "$CAMERA_DOMAIN.test"
        val action = EntityAddToAction.CameraWidget
        val onShowSnackbar: suspend (String, String?) -> Boolean = mockk(relaxed = true)

        mockkObject(CameraWidgetConfigureActivity.Companion)
        every { CameraWidgetConfigureActivity.newInstance(context, entityId) } returns mockk()

        handler.execute(context, action, entityId, onShowSnackbar)

        verify { context.startActivity(any()) }
        verify { CameraWidgetConfigureActivity.newInstance(context, entityId) }
        coVerify(exactly = 0) { onShowSnackbar(any(), any()) }
    }

    @Test
    fun `Given TodoWidget action when executing then it starts TodoWidgetConfigureActivity`() = runTest {
        val entityId = "$TODO_DOMAIN.test"
        val action = EntityAddToAction.TodoWidget
        val onShowSnackbar: suspend (String, String?) -> Boolean = mockk(relaxed = true)

        mockkObject(TodoWidgetConfigureActivity.Companion)
        every { TodoWidgetConfigureActivity.newInstance(context, entityId) } returns mockk()

        handler.execute(context, action, entityId, onShowSnackbar)

        verify { context.startActivity(any()) }
        verify { TodoWidgetConfigureActivity.newInstance(context, entityId) }
        coVerify(exactly = 0) { onShowSnackbar(any(), any()) }
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "true,light.test",
            "false,light.test",
            "true,alarm_control_panel.test",
            "false,alarm_control_panel.test",
        ],
    )
    fun `Given standard entity on automotive when getting actionsForEntity then returns auto favorite`(isFullFlavor: Boolean, entityId: String) = runTest {
        mockGetEntity(entityId)

        val actions = handler.actionsForEntity(isFullFlavor = isFullFlavor, isAutomotive = true, isQuest = false, entityId)

        assertEquals(listOf(EntityAddToAction.AndroidAutoFavorite), actions)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "$MEDIA_PLAYER_DOMAIN.test",
            "$TODO_DOMAIN.test",
            "$CAMERA_DOMAIN.test",
            "$IMAGE_DOMAIN.test",
        ],
    )
    fun `Given entity on automotive not vehicle domain with when getting actionsForEntity then returns empty list`(entityId: String) = runTest {
        mockGetEntity(entityId)

        val actions = handler.actionsForEntity(isFullFlavor = true, isAutomotive = true, isQuest = false, entityId)

        assertEquals(emptyList<EntityAddToAction>(), actions)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "light.test",
            "alarm_control_panel.test",
            "$MEDIA_PLAYER_DOMAIN.test",
            "$TODO_DOMAIN.test",
            "$CAMERA_DOMAIN.test",
            "$IMAGE_DOMAIN.test",
        ],
    )
    fun `Given entity on Quest with when getting actionsForEntity then returns empty list`(entityId: String) = runTest {
        mockGetEntity(entityId)

        val actions = handler.actionsForEntity(isFullFlavor = false, isAutomotive = false, isQuest = true, entityId)

        assertEquals(emptyList<EntityAddToAction>(), actions)
    }

    private fun mockGetEntity(entityId: String) {
        coEvery { integrationRepository.getEntity(entityId) } coAnswers {
            delay(1)
            createEntity(firstArg<String>())
        }
    }

    private fun createEntity(entityId: String): Entity {
        return Entity(entityId = entityId, "", mapOf(), LocalDateTime.now(), LocalDateTime.now())
    }
}
