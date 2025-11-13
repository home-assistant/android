package io.homeassistant.companion.android.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith

private data class FakeGlanceId(val id: Int) : GlanceId

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class BaseGlanceEntityWidgetReceiverTest {

    val mockedDao: TodoWidgetDao = mockk()
    val mockedServerManager: ServerManager = mockk()
    val mockedWidget: GlanceAppWidget = mockk()
    val glanceManager: GlanceAppWidgetManager = mockk()

    private fun TestScope.getReceiver(
        widgetEntitiesByServer: Map<Int, EntitiesPerServer> = emptyMap<Int, EntitiesPerServer>(),
        coroutineScopeProvider: () -> CoroutineScope = { this },
        onEntityUpdateCallback: suspend (Context, Int, Entity) -> Unit = { _, _, _ -> },
    ): BaseGlanceEntityWidgetReceiver<TodoWidgetEntity, TodoWidgetDao> {
        return object : BaseGlanceEntityWidgetReceiver<TodoWidgetEntity, TodoWidgetDao>(widgetScopeProvider = coroutineScopeProvider, glanceManagerProvider = { glanceManager }) {
            override suspend fun getWidgetEntitiesByServer(context: Context): Map<Int, EntitiesPerServer> = widgetEntitiesByServer
            override val glanceAppWidget: GlanceAppWidget = mockedWidget
            override suspend fun onEntityUpdate(context: Context, appWidgetId: Int, entity: Entity) {
                onEntityUpdateCallback(context, appWidgetId, entity)
            }
        }.apply {
            dao = mockedDao
            serverManager = mockedServerManager
        }
    }

    private fun serverRegistered() {
        coEvery { mockedServerManager.isRegistered() } returns true
    }

    @Test
    fun `Given valid intent with ACTION_SCREEN_ON when onReceive is called and no server is register then do nothing`() = runTest {
        val context: Context = mockk()
        val intent = mockIntent(Intent.ACTION_SCREEN_ON)
        val entitiesPerServer = mockk<Map<Int, EntitiesPerServer>>()
        val receiver = getReceiver(entitiesPerServer)

        coEvery { mockedServerManager.isRegistered() } returns false

        receiver.onReceive(context, intent)

        verify(exactly = 0) { entitiesPerServer wasNot Called }
    }

    private fun mockIntent(action: String, appWidgetId: Int? = null): Intent {
        val intent = mockk<Intent>()
        every { intent.action } returns action
        every { intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) } returns (appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID)
        return intent
    }

    @Test
    fun `Given valid intent with ACTION_SCREEN_ON when onReceive is called with registered server then it cleanup orphans`() = runTest {
        val context: Context = mockk()
        val intent = mockIntent(Intent.ACTION_SCREEN_ON)
        val receiver = getReceiver(
            mapOf(
                1 to EntitiesPerServer(42, listOf("entity1")),
                2 to EntitiesPerServer(43, listOf("entity2")),
                3 to EntitiesPerServer(44, listOf("entity3")),
            ),
        )
        val glanceIds = listOf<GlanceId>(FakeGlanceId(1))
        val integrationRepository = mockk<IntegrationRepository>()

        serverRegistered()
        coEvery { glanceManager.getGlanceIds(mockedWidget.javaClass) } returns glanceIds
        every { glanceManager.getAppWidgetId(any()) } answers { firstArg<FakeGlanceId>().id }
        coEvery { mockedServerManager.getServer(any<Int>()) } returns mockk()
        coEvery { mockedServerManager.integrationRepository(any()) } returns integrationRepository
        coEvery { integrationRepository.getEntityUpdates(listOf("entity1")) } returns channelFlow {
            close()
        }
        coJustRun { mockedDao.deleteAll(any()) }

        receiver.onReceive(context, intent)

        advanceUntilIdle()

        coVerify { mockedDao.deleteAll(intArrayOf(2, 3)) }
    }

    @Test
    fun `Given valid intent with ACTION_SCREEN_ON when onReceive is called with registered servers and multiple entities then it watch for changes and updates widgets`() = runTest {
        val context: Context = mockk()
        val intent = mockIntent(Intent.ACTION_SCREEN_ON)
        var entityReceived: Entity? = null
        val receiver = getReceiver(
            mapOf(
                1 to EntitiesPerServer(42, listOf("entity1")),
                2 to EntitiesPerServer(43, listOf("entity2")),
            ),
            onEntityUpdateCallback = { _, _, entity ->
                entityReceived = entity
            },
        )
        val glanceIds = listOf<GlanceId>(FakeGlanceId(1), FakeGlanceId(2))
        val integrationRepository = mockk<IntegrationRepository>()
        var widget1EntityProducer: ProducerScope<Entity>? = null
        var widget2EntityProducer: ProducerScope<Entity>? = null

        serverRegistered()
        coEvery { glanceManager.getGlanceIds(mockedWidget.javaClass) } returns glanceIds
        every { glanceManager.getAppWidgetId(any()) } answers { firstArg<FakeGlanceId>().id }
        every { glanceManager.getGlanceIdBy(any<Int>()) } answers { FakeGlanceId(firstArg()) }
        coJustRun { mockedWidget.update(context, any()) }
        coEvery { mockedServerManager.getServer(any<Int>()) } returns mockk()
        coEvery { mockedServerManager.integrationRepository(any()) } returns integrationRepository
        coEvery { integrationRepository.getEntityUpdates(listOf("entity1")) } returns channelFlow {
            widget1EntityProducer = this
            awaitClose()
        }
        coEvery { integrationRepository.getEntityUpdates(listOf("entity2")) } returns channelFlow {
            widget2EntityProducer = this
            awaitClose()
        }

        receiver.onReceive(context, intent)

        advanceUntilIdle()
        assertNull(entityReceived)

        val dummyUpdateWidget1: Entity = mockk()
        widget1EntityProducer!!.send(dummyUpdateWidget1)
        advanceUntilIdle()
        assertEquals(dummyUpdateWidget1, entityReceived)

        val dummyUpdateWidget2: Entity = mockk()
        widget2EntityProducer!!.send(dummyUpdateWidget2)
        advanceUntilIdle()
        assertEquals(dummyUpdateWidget2, entityReceived)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockedWidget.update(context, FakeGlanceId(1))
            mockedWidget.update(context, FakeGlanceId(2))
        }

        // Closing producers to end the test
        widget1EntityProducer.close()
        widget2EntityProducer.close()
    }

    @Test
    fun `Given valid intent with ACTION_APPWIDGET_UPDATE when onReceive is called then updateAll`() = runTest {
        val context: Context = mockk()
        val intent = mockIntent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        val receiver = getReceiver()

        // Since `updateAll` is an extension we need to mockk static the whole file that contains it
        mockkStatic("androidx.glance.appwidget.GlanceAppWidgetKt")
        coJustRun { mockedWidget.updateAll(context) }

        receiver.onReceive(context, intent)

        advanceUntilIdle()

        coVerify(exactly = 1) { mockedWidget.updateAll(context) }
    }

    @Test
    fun `Given valid intent with ACTION_SCREEN_OFF when onReceive is called with entities being watched then stop watching for entities changes`() = runTest {
        val context: Context = mockk()
        // We need a second scope that is going to be canceled that we can also control the advanceUntilIdle
        val receiverScope = TestScope()
        val receiver = getReceiver(
            mapOf(
                1 to EntitiesPerServer(42, listOf("entity1")),
            ),
            coroutineScopeProvider = { receiverScope },
        )
        val glanceIds = listOf<GlanceId>(FakeGlanceId(1))
        val integrationRepository = mockk<IntegrationRepository>()

        var producer: ProducerScope<Entity>? = null

        serverRegistered()
        coEvery { glanceManager.getGlanceIds(mockedWidget.javaClass) } returns glanceIds
        every { glanceManager.getAppWidgetId(any()) } answers { firstArg<FakeGlanceId>().id }
        coEvery { mockedServerManager.getServer(any<Int>()) } returns mockk()
        coEvery { mockedServerManager.integrationRepository(any()) } returns integrationRepository
        coEvery { integrationRepository.getEntityUpdates(listOf("entity1")) } returns channelFlow {
            producer = this
            awaitClose()
        }

        receiver.onReceive(context, mockIntent(Intent.ACTION_SCREEN_ON))

        receiverScope.advanceUntilIdle()

        assertTrue(producer!!.isActive)

        receiver.onReceive(context, mockIntent(Intent.ACTION_SCREEN_OFF))

        receiverScope.advanceUntilIdle()

        assertFalse(producer.isActive)
    }

    @Test
    fun `Given valid intent with ACTION_APPWIDGET_CREATED when onReceive is called then persist WidgetEntity in DAO`() = runTest {
        val receiver = getReceiver()
        val context: Context = mockk()
        val appWidgetId = 42
        val intent = mockIntent(ACTION_APPWIDGET_CREATED, appWidgetId)
        val rawEntity = TodoWidgetEntity(-1, 43, "entity")
        val updatedEntity = TodoWidgetEntity(42, 43, "entity")

        every { intent.getSerializableExtra(EXTRA_WIDGET_ENTITY) } returns rawEntity
        coJustRun { mockedDao.add(updatedEntity) }

        receiver.onReceive(context, intent)

        advanceUntilIdle()

        coVerify(exactly = 1) { mockedDao.add(updatedEntity) }
    }

    @Test
    fun `Given intent with ACTION_APPWIDGET_CREATED without EXTRA_WIDGET_ENTITY when onReceive is called then do nothing`() = runTest {
        FailFast.setHandler { _, _ ->
            // No-op
        }
        val receiver = getReceiver()
        val context: Context = mockk()
        val appWidgetId = 42
        val intent = mockIntent(ACTION_APPWIDGET_CREATED, appWidgetId)

        every { intent.getSerializableExtra(EXTRA_WIDGET_ENTITY) } returns null

        receiver.onReceive(context, intent)

        advanceUntilIdle()

        coVerify(exactly = 0) { mockedDao wasNot Called }
    }

    @Test
    fun `Given intent with ACTION_APPWIDGET_CREATED without EXTRA_APPWIDGET_ID when onReceive is called then do nothing`() = runTest {
        FailFast.setHandler { _, _ ->
            // No-op
        }
        val receiver = getReceiver()
        val context: Context = mockk()
        val intent = mockIntent(ACTION_APPWIDGET_CREATED)

        every { intent.getSerializableExtra(EXTRA_WIDGET_ENTITY) } returns null

        receiver.onReceive(context, intent)

        advanceUntilIdle()

        coVerify(exactly = 0) { mockedDao wasNot Called }
    }
}
