package io.homeassistant.companion.android.database.widget

interface WidgetDao<T : WidgetEntity<T>> {
    suspend fun add(entity: T)
    suspend fun delete(id: Int)
    suspend fun deleteAll(ids: IntArray)
}
