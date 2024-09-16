package io.homeassistant.companion.android.repositories

import io.homeassistant.companion.android.database.widget.WidgetEntity
import kotlinx.coroutines.flow.Flow

interface BaseDaoWidgetRepository<T : WidgetEntity> {

    fun get(id: Int): T?

    fun exist(appWidgetId: Int): Boolean {
        return get(appWidgetId) != null
    }

    suspend fun add(entity: T)

    suspend fun delete(id: Int)

    suspend fun deleteAll(ids: IntArray)

    suspend fun getAll(): List<T>

    suspend fun getAllFlow(): Flow<List<T>>

    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String)
}
