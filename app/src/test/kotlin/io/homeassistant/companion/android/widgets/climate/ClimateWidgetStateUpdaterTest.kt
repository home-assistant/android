package io.homeassistant.companion.android.widgets.climate

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon
import io.homeassistant.companion.android.common.data.integration.ClimateControls
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.widget.ClimateWidgetDao
import io.homeassistant.companion.android.database.widget.ClimateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClimateWidgetStateUpdaterTest {

    private val dao = mockk<ClimateWidgetDao>()
    private val webSocketRepository = mockk<WebSocketRepository>()
    private val serverManager = mockk<ServerManager>().apply {
        coEvery { webSocketRepository(any()) } returns webSocketRepository
    }
    private val entitiesForDisplayManager = mockk<EntitiesForDisplayManager>()
    private val updater = ClimateWidgetStateUpdater(dao, serverManager, entitiesForDisplayManager)

    /*
Initial state emission
     */
    @Test
    fun `Given widgetId not in DAO when subscribing to stateFlow then emits EmptyState`() = runTest {
        val widgetId = 42

        coEvery { dao.getFlow(widgetId) } returns flow {
            emit(null)
            delay(1) // Fake delay to simulate that the flow doesn't complete
        }
        coEvery { dao.get(widgetId) } returns null

        updater.stateFlow(42).test {
            awaitEmptyClimateState()
            expectNoEvents()
        }
    }

    @Test
    fun `Given widgetId in DAO when subscribing to stateFlow then emits DAO Entry current state out of sync`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val climateWidgetEntity = ClimateWidgetEntity(widgetId, 1, entityId)

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(climateWidgetEntity)
            awaitClose()
        }
        coEvery { dao.get(widgetId) } returns climateWidgetEntity
        mockDisplayEntities(entityId) { awaitClose() }

        updater.stateFlow(42).test {
            val state = awaitItem()
            assertEquals(ClimateStateWithData.from(climateWidgetEntity), state)
            expectNoEvents()
        }
    }

    @Test
    fun `Given widgetId in DAO with a removed server when subscribing to stateFlow then emits DAO Entry current state out of sync`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val climateWidgetEntity = ClimateWidgetEntity(widgetId, 1, entityId)

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(climateWidgetEntity)
            awaitClose()
        }
        coEvery { dao.get(widgetId) } returns climateWidgetEntity
        // A removed server has nothing to observe, so the widget keeps the data of the database
        mockDisplayEntities(entityId) { send(EntityDisplayState.Error) }

        updater.stateFlow(42).test {
            assertEquals(ClimateStateWithData.from(climateWidgetEntity), awaitItem())
            assertEquals(ClimateStateWithData.from(climateWidgetEntity), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `Given widgetID when subscribing to stateFlow with error then it catches and complete the flow`() = runTest {
        val widgetId = 42

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            throw IllegalStateException()
        }

        updater.stateFlow(widgetId).test {
            awaitComplete()
        }
    }

    /*
Watch for update
     */
    @Test
    fun `Given widgetId in DAO when subscribing to stateFlow then updates latestUpdateData and emits new state`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val climateWidgetEntity = ClimateWidgetEntity(widgetId, 1, entityId)
        val displayEntity = fakeEntityDisplay(
            entityId = entityId,
            name = "My HVAC",
            rawState = "heat",
            climateControls = ClimateControls(
                currentTemperature = 22f,
                targetTemperature = 24f,
                targetTemperatureStep = 1f,
                minTemperature = 15f,
                maxTemperature = 30f,
                hvacAction = "heat",
                hvacSupportedModes = listOf("off", "heat"),
            ),
        )

        mockInitialStateForEmpty()

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(climateWidgetEntity)
            awaitClose()
        }
        coJustRun { dao.updateWidgetLastUpdate(any(), any()) }

        mockDisplayEntities(entityId) {
            send(loaded(displayEntity))
            awaitClose()
        }

        updater.stateFlow(widgetId).test {
            awaitEmptyClimateState()
            assertEquals(
                ClimateStateWithData.from(
                    climateWidgetEntity,
                    displayEntity,
                ),
                awaitItem(),
            )
            expectNoEvents()
        }

        verifyDaoUpdate(exactly = 1)

        coVerify {
            dao.updateWidgetLastUpdate(
                widgetId = widgetId,
                lastUpdateData = ClimateWidgetEntity.LastUpdateData(
                    entityName = "My HVAC",
                    currentTemp = 22f,
                    climateTemp = 24f,
                    stepTemp = 1f,
                    minTemp = 15f,
                    maxTemp = 30f,
                    stateClimate = "heat",
                    hvacModesSupported = listOf("off", "heat"),
                ),
            )
        }
    }

    @Test
    fun `Given widgetID in DAO when subscribing to stateFlow then it emits only when configuration or entity changes`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val climateWidgetEntity = ClimateWidgetEntity(widgetId, 1, entityId)
        val displayEntity = fakeEntityDisplay(
            entityId = entityId,
            name = "My HVAC",
            rawState = "heat",
            climateControls = ClimateControls(
                currentTemperature = 22f,
                targetTemperature = 24f,
                targetTemperatureStep = 1f,
                minTemperature = 15f,
                maxTemperature = 30f,
                hvacAction = "heat",
                hvacSupportedModes = listOf("off", "heat"),
            ),
        )

        var daoFlowEmitter: ProducerScope<ClimateWidgetEntity?>? = null
        var entityUpdatesEmitter: ProducerScope<EntityDisplayState<EntityDisplayWithoutContext>>? = null

        mockInitialStateForEmpty()

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            daoFlowEmitter = this
            send(climateWidgetEntity)
            awaitClose()
        }
        coJustRun { dao.updateWidgetLastUpdate(any(), any()) }

        mockDisplayEntities(entityId) {
            entityUpdatesEmitter = this
            send(loaded(displayEntity))
            awaitClose()
        }

        updater.stateFlow(widgetId).test {
            awaitEmptyClimateState()

            awaitItem()
            verifyDaoUpdate(exactly = 1)
            verifyEntityUpdates(exactly = 1)

            // send entity without configuration changes doesn't trigger an update
            daoFlowEmitter!!.send(climateWidgetEntity)
            daoFlowEmitter.send(climateWidgetEntity.copy(latestUpdateData = ClimateWidgetEntity.LastUpdateData("hello", 24f)))
            expectNoEvents()
            verifyDaoUpdate(exactly = 1)
            verifyEntityUpdates(exactly = 1)

            // A new item with a configuration change emits
            daoFlowEmitter.send(climateWidgetEntity.copy(backgroundType = WidgetBackgroundType.TRANSPARENT))
            awaitItem()
            verifyDaoUpdate(exactly = 2)
            verifyEntityUpdates(exactly = 2)

            // Every entity update triggers a new state
            entityUpdatesEmitter!!.send(loaded(displayEntity))
            awaitItem()
            verifyDaoUpdate(exactly = 3)

            entityUpdatesEmitter.send(loaded(displayEntity.copy(name = "Renamed list")))
            awaitItem()
            verifyDaoUpdate(exactly = 4)

            expectNoEvents()
        }
    }

    @Test
    fun `Given widgetID in DAO when subscribing to stateFlow without entity to observe then it emits dao entity with out of sync`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val climateWidgetEntity = ClimateWidgetEntity(widgetId, 1, entityId)

        mockInitialStateForEmpty()

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(climateWidgetEntity)
            awaitClose()
        }
        coJustRun { dao.updateWidgetLastUpdate(any(), any()) }

        mockDisplayEntities(entityId) { send(EntityDisplayState.Error) }

        updater.stateFlow(widgetId).test {
            awaitEmptyClimateState()
            assertEquals(
                ClimateStateWithData.from(
                    climateWidgetEntity,
                ),
                awaitItem(),
            )
            expectNoEvents()
        }
    }

    @Test
    fun `Given widget without data in DAO when loading then emits loading state`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val climateWidgetEntity = ClimateWidgetEntity(widgetId, 1, entityId)
        val displayEntity = fakeEntityDisplay(
            entityId = entityId,
            name = "My HVAC",
            rawState = "heat",
            climateControls = ClimateControls(
                currentTemperature = 22f,
                targetTemperature = 24f,
                targetTemperatureStep = 1f,
                minTemperature = 15f,
                maxTemperature = 30f,
                hvacAction = "heat",
                hvacSupportedModes = listOf("off", "heat"),
            ),
        )

        mockInitialStateForEmpty()

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(climateWidgetEntity)
            awaitClose()
        }
        coJustRun { dao.updateWidgetLastUpdate(any(), any()) }

        mockDisplayEntities(entityId) {
            send(EntityDisplayState.Loading)
            send(loaded(displayEntity))
            awaitClose()
        }

        updater.stateFlow(widgetId).test {
            awaitEmptyClimateState()
            // Nothing was ever loaded for this widget, an empty list would be misleading
            assertEquals(LoadingClimateState, awaitItem())
            assertEquals(ClimateStateWithData.from(climateWidgetEntity, displayEntity), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `Given widget with data in DAO when loading then emits the DAO data instead of loading`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val climateWidgetEntity = ClimateWidgetEntity(widgetId, 1, entityId).copy(
            latestUpdateData = ClimateWidgetEntity.LastUpdateData(
                entityName = "My HVAC",
                currentTemp = 22f,
                climateTemp = 24f,
                stepTemp = 1f,
                minTemp = 15f,
                maxTemp = 30f,
                stateClimate = "heat",
                hvacModesSupported = listOf("off", "heat"),
            ),
        )

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(climateWidgetEntity)
            awaitClose()
        }
        coEvery { dao.get(widgetId) } returns climateWidgetEntity

        mockDisplayEntities(entityId) {
            send(EntityDisplayState.Loading)
            awaitClose()
        }

        updater.stateFlow(widgetId).test {
            // The cached data is shown while loading rather than a spinner over readable content
            assertEquals(ClimateStateWithData.from(climateWidgetEntity), awaitItem())
            assertEquals(ClimateStateWithData.from(climateWidgetEntity), awaitItem())
            expectNoEvents()
        }
    }

    private fun fakeEntityDisplay(
        entityId: String,
        name: String,
        icon: IIcon? = null,
        rawState: String = "",
        climateControls: ClimateControls? = null,
    ): EntityDisplayWithoutContext {
        return EntityDisplayWithoutContext(
            entityId,
            name,
            icon ?: Icon.cmd_bookmark,
            rawState = rawState,
            climateControls = climateControls,
        )
    }

    private fun verifyEntityUpdates(exactly: Int) {
        verify(exactly = exactly) {
            entitiesForDisplayManager.observe(any(), any())
        }
    }

    private fun verifyDaoUpdate(exactly: Int) {
        coVerify(exactly = exactly) {
            dao.updateWidgetLastUpdate(any(), any())
        }
    }

    private suspend fun TurbineTestContext<ClimateState>.awaitEmptyClimateState() {
        val state = awaitItem()
        assertEquals(EmptyClimateState, state)
    }

    private fun mockInitialStateForEmpty() {
        coEvery { dao.get(any()) } returns null
    }

    private fun mockDisplayEntities(entityId: String, emissions: suspend ProducerScope<EntityDisplayState<EntityDisplayWithoutContext>>.() -> Unit) {
        every { entitiesForDisplayManager.observe(any(), listOf(entityId)) } returns channelFlow {
            emissions()
        }
    }

    private fun loaded(display: EntityDisplayWithoutContext) = EntityDisplayState.Loaded(listOf(display))
}
