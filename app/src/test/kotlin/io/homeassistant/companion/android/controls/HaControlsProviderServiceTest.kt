package io.homeassistant.companion.android.controls

import android.service.controls.Control
import dagger.hilt.android.testing.HiltTestApplication
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Bookmark
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.util.mockServer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

private const val SERVER_ID = 1
private const val SWITCH_A = "switch.a"
private const val SWITCH_B = "switch.b"

/**
 * Tests the controls published by [HaControlsProviderService] for the controls the system
 * subscribes to. The service is attached to a context without [android.app.Service.onCreate],
 * which would require Hilt injection, and its dependencies are set on its injected fields instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HaControlsProviderServiceTest {

    private val serverManager: ServerManager = mockk()
    private val prefsRepository: PrefsRepository = mockk()
    private val entitiesForDisplayManager: EntitiesForDisplayManager = mockk()
    private val displayStates = MutableSharedFlow<EntityDisplayState<EntityDisplayWithContext>>()

    private val subscriber = RecordingSubscriber()

    @Test
    fun `Given snapshots when subscribed then only the controls that changed since the last one are sent`() = runTest {
        val service = createService()

        service.createPublisherFor(mutableListOf("$SERVER_ID.$SWITCH_A", "$SERVER_ID.$SWITCH_B")).subscribe(subscriber)
        subscriber.subscription.request(Long.MAX_VALUE)
        displayStates.subscriptionCount.first { it > 0 }

        displayStates.emit(loaded(switchItem(SWITCH_A), switchItem(SWITCH_B)))
        advanceUntilIdle()
        assertEquals(listOf("$SERVER_ID.$SWITCH_A", "$SERVER_ID.$SWITCH_B"), subscriber.controlIds())

        // The same snapshot again sends nothing
        displayStates.emit(loaded(switchItem(SWITCH_A), switchItem(SWITCH_B)))
        advanceUntilIdle()
        assertEquals(2, subscriber.controls.size)

        // Only the switch that changed is sent again
        displayStates.emit(loaded(switchItem(SWITCH_A), switchItem(SWITCH_B, isActive = true)))
        advanceUntilIdle()
        assertEquals(listOf("$SERVER_ID.$SWITCH_A", "$SERVER_ID.$SWITCH_B", "$SERVER_ID.$SWITCH_B"), subscriber.controlIds())
    }

    @Test
    fun `Given entities that can no longer be followed when subscribed then their controls are failed`() = runTest {
        val service = createService()
        every { entitiesForDisplayManager.observeInContext(any(), any<List<String>>()) } returns
            flowOf(loaded(switchItem(SWITCH_A), switchItem(SWITCH_B)))

        service.createPublisherFor(mutableListOf("$SERVER_ID.$SWITCH_A", "$SERVER_ID.$SWITCH_B")).subscribe(subscriber)
        subscriber.subscription.request(Long.MAX_VALUE)
        advanceUntilIdle()

        assertEquals(
            listOf(
                "$SERVER_ID.$SWITCH_A" to Control.STATUS_OK,
                "$SERVER_ID.$SWITCH_B" to Control.STATUS_OK,
                "$SERVER_ID.$SWITCH_A" to Control.STATUS_ERROR,
                "$SERVER_ID.$SWITCH_B" to Control.STATUS_ERROR,
            ),
            subscriber.controls.map { it.controlId to it.status },
        )
    }

    @Test
    fun `Given an error state when subscribed then the controls are failed once`() = runTest {
        val service = createService()
        every { entitiesForDisplayManager.observeInContext(any(), any<List<String>>()) } returns
            flowOf(EntityDisplayState.Error)

        service.createPublisherFor(mutableListOf("$SERVER_ID.$SWITCH_A", "$SERVER_ID.$SWITCH_B")).subscribe(subscriber)
        subscriber.subscription.request(Long.MAX_VALUE)
        advanceUntilIdle()

        assertEquals(
            listOf("$SERVER_ID.$SWITCH_A" to Control.STATUS_ERROR, "$SERVER_ID.$SWITCH_B" to Control.STATUS_ERROR),
            subscriber.controls.map { it.controlId to it.status },
        )
    }

    @Test
    fun `Given an entity missing from the first snapshot when subscribed then its control is not found`() = runTest {
        val service = createService()

        service.createPublisherFor(mutableListOf("$SERVER_ID.$SWITCH_A", "$SERVER_ID.$SWITCH_B")).subscribe(subscriber)
        subscriber.subscription.request(Long.MAX_VALUE)
        displayStates.subscriptionCount.first { it > 0 }

        displayStates.emit(loaded(switchItem(SWITCH_A)))
        advanceUntilIdle()

        assertEquals(
            listOf("$SERVER_ID.$SWITCH_B" to Control.STATUS_NOT_FOUND, "$SERVER_ID.$SWITCH_A" to Control.STATUS_OK),
            subscriber.controls.map { it.controlId to it.status },
        )
    }

    @Test
    fun `Given a snapshot when the system cancels the subscription then the controls are not failed`() = runTest {
        val service = createService()

        service.createPublisherFor(mutableListOf("$SERVER_ID.$SWITCH_A")).subscribe(subscriber)
        subscriber.subscription.request(Long.MAX_VALUE)
        displayStates.subscriptionCount.first { it > 0 }
        displayStates.emit(loaded(switchItem(SWITCH_A)))
        advanceUntilIdle()

        subscriber.subscription.cancel()
        advanceUntilIdle()

        assertEquals(listOf("$SERVER_ID.$SWITCH_A" to Control.STATUS_OK), subscriber.controls.map { it.controlId to it.status })
    }

    @Test
    fun `Given control ids when subscribed then only their entities are observed`() = runTest {
        val service = createService()

        service.createPublisherFor(mutableListOf("$SERVER_ID.$SWITCH_A", "$SERVER_ID.$SWITCH_B")).subscribe(subscriber)
        subscriber.subscription.request(Long.MAX_VALUE)
        advanceUntilIdle()

        verify(exactly = 1) { entitiesForDisplayManager.observeInContext(SERVER_ID, listOf(SWITCH_A, SWITCH_B)) }
    }

    private fun TestScope.createService(): HaControlsProviderService {
        val server = mockServer(url = null, name = "Home", serverId = SERVER_ID)
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.servers() } returns listOf(server)
        coEvery { serverManager.getServer(SERVER_ID) } returns server
        coEvery { serverManager.connectionStateProvider(SERVER_ID) } returns mockk<ServerConnectionStateProvider> {
            every { urlFlow(any()) } returns flowOf(UrlState.HasUrl(null))
        }
        coEvery { prefsRepository.getControlsEnableStructure() } returns false
        coEvery { prefsRepository.getControlsAuthRequired() } returns ControlsAuthRequiredSetting.NONE
        every { entitiesForDisplayManager.observeInContext(any(), any<List<String>>()) } returns displayStates

        val instance = HaControlsProviderService(StandardTestDispatcher(testScheduler))
        return ServiceController.of(instance, null).get().apply {
            serverManager = this@HaControlsProviderServiceTest.serverManager
            prefsRepository = this@HaControlsProviderServiceTest.prefsRepository
            entitiesForDisplayManager = this@HaControlsProviderServiceTest.entitiesForDisplayManager
        }
    }

    private fun switchItem(entityId: String, isActive: Boolean = false) = EntityDisplayWithContext(
        item = EntityDisplayWithoutContext(
            entityId = entityId,
            name = entityId,
            icon = Mdi.Bookmark,
            isActive = isActive,
        ),
    )

    private fun loaded(vararg items: EntityDisplayWithContext) = EntityDisplayState.Loaded(items.toList())

    private class RecordingSubscriber : Flow.Subscriber<Control> {
        val controls = mutableListOf<Control>()
        lateinit var subscription: Flow.Subscription

        fun controlIds() = controls.map { it.controlId }

        override fun onSubscribe(subscription: Flow.Subscription) {
            this.subscription = subscription
        }

        override fun onNext(item: Control) {
            controls += item
        }

        override fun onError(throwable: Throwable) = throw throwable

        override fun onComplete() = Unit
    }
}
