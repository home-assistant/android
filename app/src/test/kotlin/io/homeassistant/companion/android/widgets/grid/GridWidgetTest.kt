package io.homeassistant.companion.android.widgets.grid

import android.content.Context
import io.homeassistant.companion.android.database.widget.GridWidgetDao
import io.homeassistant.companion.android.database.widget.GridWidgetEntity
import io.homeassistant.companion.android.widgets.EntitiesPerServer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GridWidgetTest {
    private val dao: GridWidgetDao = mockk()

    private val receiver = GridWidget().apply {
        dao = this@GridWidgetTest.dao
    }

    @Test
    fun `Given no entry in DAO when invoking getWidgetEntitiesByServer then returns empty`() = runTest {
        val context = mockk<Context>()

        coEvery { dao.getAll() } returns emptyList()

        assertEquals(emptyMap<Int, EntitiesPerServer>(), receiver.getWidgetEntitiesByServer(context))
    }

    @Test
    fun `Given entries in DAO when invoking getWidgetEntitiesByServer then returns properly mapped items`() = runTest {
        val context = mockk<Context>()

        coEvery { dao.getAll() } returns listOf(
            GridWidgetEntity(
                id = 1,
                serverId = 42,
                label = null,
                items = listOf(GridWidgetEntity.Item(1, "entity1", "Label", "Icon")),
            ),
            GridWidgetEntity(
                id = 2,
                serverId = 43,
                label = null,
                items = listOf(GridWidgetEntity.Item(2, "entity2", "Label", "Icon")),
            ),
        )
        assertEquals(
            mapOf(
                1 to EntitiesPerServer(serverId = 42, listOf("entity1")),
                2 to EntitiesPerServer(serverId = 43, listOf("entity2")),
            ),
            receiver.getWidgetEntitiesByServer(context),
        )
    }
}
