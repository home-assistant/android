package io.homeassistant.companion.android.webview.addto

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.IMAGE_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.TODO_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

@ExperimentalCoroutinesApi
class AddToViewModelTest {

    private lateinit var serverManager: ServerManager
    private lateinit var prefsRepository: PrefsRepository
    private lateinit var integrationRepository: IntegrationRepository

    private val serverId = 42

    @BeforeEach
    fun setUp() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
        serverManager = mockk()
        prefsRepository = mockk()
        integrationRepository = mockk()
        val server = mockk<Server>()

        every { server.id } returns serverId
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { serverManager.getServer() } returns server
    }

    @Test
    fun `Given entityId when invoking addToAndroidAutoFavorite then it adds to favorite`() = runTest {
        val entityId = "vehicle.test"
        coEvery { integrationRepository.getEntity(entityId) } returns createEntity(entityId)
        coJustRun { prefsRepository.addAutoFavorite(any()) }

        val viewModel = AddToViewModel(entityId, serverManager, prefsRepository)

        viewModel.addToAndroidAutoFavorite()

        coVerify {
            prefsRepository.addAutoFavorite(AutoFavorite(serverId, entityId))
        }
    }

    @Test
    fun `Given standard entityId when getting potentialActions then it emits first empty then EntityWidget`() = runTest {
        val entityId = "standard.test"

        mockGetEntity(entityId)

        val viewModel = AddToViewModel(entityId, serverManager, prefsRepository)

        viewModel.potentialActions.test {
            assertTrue(awaitItem().isEmpty())
            advanceUntilIdle()
            val actions = awaitItem()
            assertEquals(1, actions.size)
            assertEquals(AddToAction.EntityWidget, actions.first())
        }
    }

    @Test
    fun `Given entityId with vehicle domain when getting potentialActions then it emits first empty then EntityWidget and AndroidAutoFavorite`() = runTest {
        val entityId = "alarm_control_panel.test"

        mockGetEntity(entityId)

        val viewModel = AddToViewModel(entityId, serverManager, prefsRepository)

        viewModel.potentialActions.test {
            assertTrue(awaitItem().isEmpty())
            advanceUntilIdle()
            val actions = awaitItem()
            assertEquals(listOf(AddToAction.EntityWidget, AddToAction.AndroidAutoFavorite), actions)
        }
    }

    @Test
    fun `Given media player entityId when getting potentialActions then it emits first empty then EntityWidget and MediaPlayerWidget`() = runTest {
        val entityId = "$MEDIA_PLAYER_DOMAIN.test"

        mockGetEntity(entityId)

        val viewModel = AddToViewModel(entityId, serverManager, prefsRepository)

        viewModel.potentialActions.test {
            assertTrue(awaitItem().isEmpty())
            advanceUntilIdle()
            val actions = awaitItem()
            assertEquals(listOf(AddToAction.EntityWidget, AddToAction.MediaPlayerWidget), actions)
        }
    }

    @Test
    fun `Given list entityId when getting potentialActions then it emits first empty then EntityWidget and TodoWidget`() = runTest {
        val entityId = "$TODO_DOMAIN.test"

        mockGetEntity(entityId)

        val viewModel = AddToViewModel(entityId, serverManager, prefsRepository)

        viewModel.potentialActions.test {
            assertTrue(awaitItem().isEmpty())
            advanceUntilIdle()
            val actions = awaitItem()
            assertEquals(listOf(AddToAction.EntityWidget, AddToAction.TodoWidget), actions)
        }
    }

    @Test
    fun `Given camera entityId when getting potentialActions then it emits first empty then EntityWidget and CameraWidget`() = runTest {
        val entityId = "$CAMERA_DOMAIN.test"

        mockGetEntity(entityId)

        val viewModel = AddToViewModel(entityId, serverManager, prefsRepository)

        viewModel.potentialActions.test {
            assertTrue(awaitItem().isEmpty())
            advanceUntilIdle()
            val actions = awaitItem()
            assertEquals(listOf(AddToAction.EntityWidget, AddToAction.CameraWidget), actions)
        }
    }

    @Test
    fun `Given image entityId when getting potentialActions then it emits first empty then EntityWidget and CameraWidget`() = runTest {
        val entityId = "$IMAGE_DOMAIN.test"

        mockGetEntity(entityId)

        val viewModel = AddToViewModel(entityId, serverManager, prefsRepository)

        viewModel.potentialActions.test {
            assertTrue(awaitItem().isEmpty())
            advanceUntilIdle()
            val actions = awaitItem()
            assertEquals(listOf(AddToAction.EntityWidget, AddToAction.CameraWidget), actions)
        }
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
