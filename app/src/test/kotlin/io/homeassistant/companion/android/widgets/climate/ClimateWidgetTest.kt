package io.homeassistant.companion.android.widgets.climate

import android.content.Context
import io.homeassistant.companion.android.database.widget.ClimateWidgetDao
import io.homeassistant.companion.android.database.widget.ClimateWidgetEntity
import io.homeassistant.companion.android.widgets.EntitiesPerServer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClimateWidgetTest {
    val dao: ClimateWidgetDao = mockk()

    val receiver = ClimateWidget().apply {
        dao = this@ClimateWidgetTest.dao
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
            ClimateWidgetEntity(
                id = 1,
                serverId = 42,
                entityId = "404",
            ),
            ClimateWidgetEntity(
                id = 2,
                serverId = 43,
                entityId = "403",
            ),
        )
        assertEquals(
            mapOf<Int, EntitiesPerServer>(
                1 to EntitiesPerServer(serverId = 42, listOf("404")),
                2 to EntitiesPerServer(serverId = 43, listOf("403")),
            ),
            receiver.getWidgetEntitiesByServer(context),
        )
    }
}
