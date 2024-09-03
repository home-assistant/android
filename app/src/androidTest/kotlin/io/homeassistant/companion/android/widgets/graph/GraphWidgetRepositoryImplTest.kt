package io.homeassistant.companion.android.widgets.graph

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.homeassistant.companion.android.common.data.widgets.GraphWidgetRepositoryImpl
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetDao
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetHistoryEntity
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
            textSize = 14f,
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
        graphWidgetRepository.add(Companion.WIDGET_OBJECT)

        val retrievedWidget = graphWidgetRepository.get(1)
        assertEquals(Companion.WIDGET_OBJECT, retrievedWidget)
    }

    @Test
    fun testUpdateWidgetLastUpdate() = runBlocking {
        graphWidgetRepository.add(Companion.WIDGET_OBJECT)

        val newUpdate = "New Update"
        graphWidgetRepository.updateWidgetLastUpdate(1, newUpdate)

        val updatedWidget = graphWidgetRepository.get(1)
        assertEquals(newUpdate, updatedWidget?.lastUpdate)
    }

    @Test
    fun testInsertGraphWidgetHistory() = runBlocking {
        graphWidgetRepository.add(Companion.WIDGET_OBJECT)

        val historyEntity = GraphWidgetHistoryEntity(
            entityId = "history1",
            graphWidgetId = 1,
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
        graphWidgetRepository.add(Companion.WIDGET_OBJECT)

        val currentTime = System.currentTimeMillis()
        val oldTime = currentTime - (60 * 60 * 1000) // 1 hour ago

        val historyEntity1 = GraphWidgetHistoryEntity("history1", 1, "State1", oldTime)
        val historyEntity2 = GraphWidgetHistoryEntity("history2", 1, "State2", currentTime)

        graphWidgetRepository.insertGraphWidgetHistory(historyEntity1)
        graphWidgetRepository.insertGraphWidgetHistory(historyEntity2)

        graphWidgetRepository.deleteEntriesOlderThan(1, currentTime)

        val widgetHistories = graphWidgetDao.getWithHistories(1)
        assertEquals(1, widgetHistories?.histories?.size)
        assertEquals(historyEntity2, widgetHistories?.histories?.get(0))
    }

    @Test
    fun testSaveHistoricWithoutAppWidgetShouldThrowExceptionForeignKey() = runBlocking {
        val historyEntity1 = GraphWidgetHistoryEntity("history1", 1, "State1", System.currentTimeMillis())

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                graphWidgetDao.add(historyEntity1)
            }
        }
    }
}
