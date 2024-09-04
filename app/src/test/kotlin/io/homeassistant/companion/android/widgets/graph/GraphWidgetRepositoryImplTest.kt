
import io.homeassistant.companion.android.common.data.widgets.GraphWidgetRepositoryImpl
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetDao
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetWithHistories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class GraphWidgetRepositoryImplTest {

    private lateinit var graphWidgetDao: GraphWidgetDao
    private lateinit var graphWidgetRepository: GraphWidgetRepositoryImpl

    @Before
    fun setUp() {
        graphWidgetDao = mock(GraphWidgetDao::class.java)
        graphWidgetRepository = GraphWidgetRepositoryImpl(graphWidgetDao)
    }

    @Test
    fun `test getGraphWidget returns expected result`() = runBlocking {
        val expectedEntity = GraphWidgetEntity(
            1, 1, "entityId", null, null, 30L, 24, "", "", WidgetTapAction.REFRESH, "lastUpdate", WidgetBackgroundType.DAYNIGHT, null
        )
        whenever(graphWidgetDao.get(1)).thenReturn(expectedEntity)

        val result = graphWidgetRepository.get(1)

        assertEquals(expectedEntity, result)
    }

    @Test
    fun `test getGraphWidgetWithHistories returns expected result`() = runBlocking {
        val expectedEntity = GraphWidgetEntity(
            1, 1, "entityId", null, null, 30L, 24, "", "", WidgetTapAction.REFRESH, "lastUpdate", WidgetBackgroundType.DAYNIGHT, null
        )
        val expectedHistories = GraphWidgetWithHistories(expectedEntity, listOf())
        whenever(graphWidgetDao.getWithHistories(1)).thenReturn(expectedHistories)

        val result = graphWidgetRepository.getGraphWidgetWithHistories(1)

        assertEquals(expectedHistories, result)
    }

    @Test
    fun `test add GraphWidgetEntity`() = runBlocking {
        val entity = GraphWidgetEntity(
            1, 1, "entityId", null, null, 30L, 24, "", "", WidgetTapAction.REFRESH, "lastUpdate", WidgetBackgroundType.DAYNIGHT, null
        )

        graphWidgetRepository.add(entity)

        verify(graphWidgetDao).add(entity)
    }

    @Test
    fun `test delete GraphWidgetEntity by id`() = runBlocking {
        val widgetId = 1

        graphWidgetRepository.delete(widgetId)

        verify(graphWidgetDao).delete(widgetId)
    }

    @Test
    fun `test deleteAll GraphWidgetEntity by ids`() = runBlocking {
        val widgetIds = intArrayOf(1, 2, 3)

        graphWidgetRepository.deleteAll(widgetIds)

        verify(graphWidgetDao).deleteAll(widgetIds)
    }

    @Test
    fun `test getAll returns expected result`() = runBlocking {
        val expectedList = listOf(
            GraphWidgetEntity(
                1, 1, "entityId", null, null, 30L, 24, "", "", WidgetTapAction.REFRESH, "lastUpdate", WidgetBackgroundType.DAYNIGHT, null
            )
        )
        whenever(graphWidgetDao.getAll()).thenReturn(expectedList)

        val result = graphWidgetRepository.getAll()

        assertEquals(expectedList, result)
    }

    @Test
    fun `test getAllFlow returns expected result`() = runBlocking {
        val expectedList = listOf(
            GraphWidgetEntity(
                1, 1, "entityId", null, null, 30L, 24, "", "", WidgetTapAction.REFRESH, "lastUpdate", WidgetBackgroundType.DAYNIGHT, null
            )
        )
        whenever(graphWidgetDao.getAllFlow()).thenReturn(flowOf(expectedList))

        val result = graphWidgetRepository.getAllFlow().first()

        assertEquals(expectedList, result)
    }

    @Test
    fun `test updateWidgetLastUpdate`() = runBlocking {
        val widgetId = 1
        val lastUpdate = "newUpdate"

        graphWidgetRepository.updateWidgetLastUpdate(widgetId, lastUpdate)

        verify(graphWidgetDao).updateWidgetLastUpdate(widgetId, lastUpdate)
    }

    @Test
    fun `test deleteEntriesOlderThan`() = runBlocking {
        val widgetId = 1
        val cutoffTime = System.currentTimeMillis()

        graphWidgetRepository.deleteEntriesOlderThan(widgetId, cutoffTime)

        verify(graphWidgetDao).deleteEntriesOlderThan(widgetId, cutoffTime)

        // Mocking the return of null to simulate that the entity was deleted
        whenever(graphWidgetDao.get(widgetId)).thenReturn(null)
        val result = graphWidgetRepository.get(widgetId)

        assertEquals(null, result)
    }

}
