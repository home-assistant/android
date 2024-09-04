package io.homeassistant.companion.android.widgets.graph

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import io.homeassistant.companion.android.common.data.widgets.GraphWidgetRepositoryImpl
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetDao
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetHistoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4ClassRunner::class)
class GraphWidgetRepositoryImplTest {

    private lateinit var graphWidgetDao: GraphWidgetDao
    private lateinit var db: AppDatabase
    private lateinit var graphWidgetRepository: GraphWidgetRepositoryImpl

    companion object {
        private val WIDGET_OBJECT = GraphWidgetEntity(
            id = 1,
            serverId = 1,
            entityId = "entityId",
            attributeIds = null,
            label = "Test Widget",
            stateSeparator = ", ",
            attributeSeparator = ": ",
            tapAction = WidgetTapAction.REFRESH,
            lastUpdate = "Last Update",
            backgroundType = WidgetBackgroundType.DAYNIGHT,
            textColor = "#FFFFFF"
        )
    }

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        graphWidgetDao = db.graphWidgetDao()
        graphWidgetRepository = GraphWidgetRepositoryImpl(graphWidgetDao)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndGetGraphWidget() = runBlocking {
        graphWidgetRepository.add(WIDGET_OBJECT)

        val retrievedWidget = graphWidgetRepository.get(1)
        assertEquals(WIDGET_OBJECT, retrievedWidget)
    }

    @Test
    fun testUpdateWidgetLastUpdate() = runBlocking {
        graphWidgetRepository.add(WIDGET_OBJECT)

        val newUpdate = "New Update"
        graphWidgetRepository.updateWidgetLastUpdate(1, newUpdate)

        val updatedWidget = graphWidgetRepository.get(1)
        assertEquals(newUpdate, updatedWidget?.lastUpdate)
    }

    @Test
    fun testInsertGraphWidgetHistory() = runBlocking {
        graphWidgetRepository.add(WIDGET_OBJECT)

        val historyEntity = GraphWidgetHistoryEntity(
            id = 1,
            entityId = "history1",
            graphWidgetId = WIDGET_OBJECT.id,
            state = "State1",
            sentState = System.currentTimeMillis()
        )

        graphWidgetRepository.insertGraphWidgetHistory(historyEntity)

        val widgetHistories = graphWidgetDao.getWithHistories(1)
        assertEquals(1, widgetHistories?.histories?.size)
        assertEquals(historyEntity, widgetHistories?.histories?.get(0))
    }

    @Test
    fun testDeleteEntriesOlderThan() = runBlocking {
        graphWidgetRepository.add(WIDGET_OBJECT)

        val currentTime = System.currentTimeMillis()
        val oldTime = currentTime - (60 * 60 * 1000) // 1 hour ago

        val historyEntity1 = GraphWidgetHistoryEntity(1,"history1", 1, "State1", oldTime)
        val historyEntity2 = GraphWidgetHistoryEntity(2,"history2", 1, "State2", currentTime)

        graphWidgetRepository.insertGraphWidgetHistory(historyEntity1)
        graphWidgetRepository.insertGraphWidgetHistory(historyEntity2)

        graphWidgetRepository.deleteEntriesOlderThan(1, currentTime)

        val widgetHistories = graphWidgetDao.getWithHistories(1)
        assertEquals(1, widgetHistories?.histories?.size)
        assertEquals(historyEntity2, widgetHistories?.histories?.get(0))
    }

}
